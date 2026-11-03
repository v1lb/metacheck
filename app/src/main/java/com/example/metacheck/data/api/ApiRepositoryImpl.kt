package com.example.metacheck.data.api

import com.example.metacheck.data.export.ReportJson
import com.example.metacheck.data.parser.readBounded
import com.example.metacheck.domain.api.ApiConfigValidator
import com.example.metacheck.domain.model.ApiError
import com.example.metacheck.domain.model.ApiException
import com.example.metacheck.domain.model.ApiResult
import com.example.metacheck.domain.model.MetaReport
import com.example.metacheck.domain.repository.ApiRepository
import com.example.metacheck.domain.repository.SettingsRepository
import com.example.metacheck.domain.text.MetaTexts
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

    override suspend fun send(report: MetaReport, texts: MetaTexts): Result<ApiResult> = withContext(io) {
        try {
            Result.success(sendBlocking(report, texts))
        } catch (e: CancellationException) {
            throw e
        } catch (e: ApiException) {
            Result.failure(e)
        } catch (e: SSLPeerUnverifiedException) {
            Result.failure(ApiException(ApiError.CERTIFICATE))
        } catch (e: SSLHandshakeException) {
            Result.failure(ApiException(ApiError.TLS_HANDSHAKE))
        } catch (e: UnknownHostException) {
            Result.failure(ApiException(ApiError.HOST_NOT_FOUND))
        } catch (e: SocketTimeoutException) {
            Result.failure(ApiException(ApiError.TIMEOUT))
        } catch (e: IllegalArgumentException) {
            // Сообщения OkHttp о неверных заголовках содержат значение, поэтому наружу их не отдаём.
            Result.failure(ApiException(ApiError.BAD_SETTINGS))
        } catch (e: Exception) {
            Result.failure(ApiException(ApiError.REQUEST_FAILED))
        }
    }

    private fun sendBlocking(report: MetaReport, texts: MetaTexts): ApiResult {
        val s = settingsRepository.settings.value
        if (!s.apiEnabled) throw ApiException(ApiError.DISABLED)
        ApiConfigValidator.validateUrl(s.apiEndpoint)?.let { throw ApiException(ApiError.INVALID_CONFIG, it) }
        ApiConfigValidator.validateHeaderName(s.apiHeaderName)?.let { throw ApiException(ApiError.INVALID_CONFIG, it) }
        ApiConfigValidator.validatePin(s.certPin)?.let { throw ApiException(ApiError.INVALID_CONFIG, it) }

        val key = settingsRepository.readApiKey()
        if (s.hasApiKey && key == null) throw ApiException(ApiError.KEY_UNREADABLE)

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

        val body = ReportJson.toJson(report, includeGps = s.apiSendGps, texts = texts).toString()
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
