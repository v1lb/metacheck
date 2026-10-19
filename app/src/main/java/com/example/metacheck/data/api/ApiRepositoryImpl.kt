package com.example.metacheck.data.api

import com.example.metacheck.data.export.ReportJson
import com.example.metacheck.data.parser.readBounded
import com.example.metacheck.domain.api.ApiConfigValidator
import com.example.metacheck.domain.model.ApiException
import com.example.metacheck.domain.model.ApiResult
import com.example.metacheck.domain.model.MetaReport
import com.example.metacheck.domain.repository.ApiRepository
import com.example.metacheck.domain.repository.SettingsRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.CertificatePinner
import okhttp3.ConnectionSpec
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.TlsVersion
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLHandshakeException
import javax.net.ssl.SSLPeerUnverifiedException

/**
 * Отправляет на пользовательский REST-адрес JSON-отчёт. Само изображение не отправляется никогда.
 * Только HTTPS, редиректы выключены (чтобы ключ не ушёл на другой хост), TLS не ниже 1.2
 * (по желанию только 1.3), необязательный пиннинг сертификата.
 */
class ApiRepositoryImpl(
    private val settingsRepository: SettingsRepository,
    private val io: CoroutineDispatcher = Dispatchers.IO,
) : ApiRepository {

    override suspend fun send(report: MetaReport): Result<ApiResult> = withContext(io) {
        try {
            Result.success(sendBlocking(report))
        } catch (e: CancellationException) {
            throw e
        } catch (e: ApiException) {
            Result.failure(e)
        } catch (e: SSLPeerUnverifiedException) {
            Result.failure(ApiException("Сертификат сервера не прошёл проверку (недоверенный или не совпал пин)."))
        } catch (e: SSLHandshakeException) {
            Result.failure(ApiException("Не удалось установить защищённое соединение. Проверь настройку «Только TLS 1.3»."))
        } catch (e: UnknownHostException) {
            Result.failure(ApiException("Сервер не найден. Проверь адрес и интернет."))
        } catch (e: SocketTimeoutException) {
            Result.failure(ApiException("Сервер не ответил вовремя."))
        } catch (e: IllegalArgumentException) {
            // Сообщения OkHttp о неверных заголовках содержат значение, поэтому наружу их не отдаём.
            Result.failure(ApiException("Неверные настройки API (адрес или заголовок)."))
        } catch (e: Exception) {
            Result.failure(ApiException("Не удалось выполнить запрос."))
        }
    }

    private fun sendBlocking(report: MetaReport): ApiResult {
        val s = settingsRepository.settings.value
        if (!s.apiEnabled) throw ApiException("Отправка отключена в настройках.")
        ApiConfigValidator.validateUrl(s.apiEndpoint)?.let { throw ApiException(it) }
        ApiConfigValidator.validateHeaderName(s.apiHeaderName)?.let { throw ApiException(it) }
        ApiConfigValidator.validatePin(s.certPin)?.let { throw ApiException(it) }

        val key = settingsRepository.readApiKey()
        if (s.hasApiKey && key == null) throw ApiException("Не удалось прочитать сохранённый ключ. Введи его заново в настройках.")

        val url = s.apiEndpoint.trim().toHttpUrl()
        val spec = if (s.tls13Only) {
            ConnectionSpec.Builder(ConnectionSpec.RESTRICTED_TLS).tlsVersions(TlsVersion.TLS_1_3).build()
        } else {
            ConnectionSpec.RESTRICTED_TLS
        }
        val clientBuilder = OkHttpClient.Builder()
            .connectionSpecs(listOf(spec))
            .followRedirects(false)
            .followSslRedirects(false)
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .writeTimeout(20, TimeUnit.SECONDS)
            .callTimeout(30, TimeUnit.SECONDS)
        if (s.certPin.isNotBlank()) {
            clientBuilder.certificatePinner(
                CertificatePinner.Builder().add(url.host, "sha256/" + s.certPin.trim()).build(),
            )
        }

        val body = ReportJson.toJson(report, includeGps = s.apiSendGps).toString()
            .toRequestBody("application/json; charset=utf-8".toMediaType())
        val request = Request.Builder().url(url).post(body).header("User-Agent", "MetaCheck")
        if (key != null) request.header(s.apiHeaderName.trim(), key)

        clientBuilder.build().newCall(request.build()).execute().use { response ->
            val bytes = response.body?.byteStream()?.let { readBounded(it, MAX_RESPONSE_BYTES).first } ?: ByteArray(0)
            return ApiResult(response.code, String(bytes, Charsets.UTF_8))
        }
    }

    private companion object {
        const val MAX_RESPONSE_BYTES = 64 * 1024
    }
}
