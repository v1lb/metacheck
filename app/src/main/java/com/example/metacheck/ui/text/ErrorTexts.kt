package com.example.metacheck.ui.text

import androidx.annotation.StringRes
import com.example.metacheck.R
import com.example.metacheck.data.crypto.CryptoUnavailableException
import com.example.metacheck.domain.AppFailure
import com.example.metacheck.domain.FailureCode
import com.example.metacheck.domain.HiddenPart
import com.example.metacheck.domain.api.ApiConfigIssue
import com.example.metacheck.domain.metadata.DailyCleanQuota
import com.example.metacheck.domain.metadata.DailyLimitReachedException
import com.example.metacheck.domain.metadata.InvalidPatchException
import com.example.metacheck.domain.metadata.PatchField
import com.example.metacheck.domain.metadata.PatchIssue
import com.example.metacheck.domain.model.ApiError
import com.example.metacheck.domain.model.ApiException
import com.example.metacheck.domain.model.LicenseError
import com.example.metacheck.domain.model.LicenseException
import com.example.metacheck.domain.model.ProRequiredException
import com.example.metacheck.domain.model.TooLargeException
import com.example.metacheck.domain.model.UnsupportedFormatException

/**
 * Единственное место, где ошибки превращаются в текст для пользователя.
 * Технические сообщения исключений (на английском, для разработчика) пользователю не показываются.
 */
object ErrorTexts {

    /** Причина ошибки без префикса «Операция не выполнена». */
    fun reason(error: Throwable): UiText {
        // Потеря доступа к файлу важнее всего остального: человек должен выбрать файл снова.
        if (error.causes().any { it is SecurityException }) return UiText.Res(R.string.error_access_lost)
        return when (error) {
            is AppFailure -> failure(error)
            is InvalidPatchException -> patchIssues(error.issues)
            is ApiException -> api(error)
            is LicenseException -> UiText.Res(license(error.code))
            is CryptoUnavailableException -> UiText.Res(R.string.error_keystore_unavailable)
            is DailyLimitReachedException -> UiText.Res(R.string.error_daily_limit, DailyCleanQuota.FREE_DAILY_LIMIT)
            is ProRequiredException -> UiText.Res(R.string.error_pro_required)
            is UnsupportedFormatException -> UiText.Res(R.string.error_unsupported_format)
            is TooLargeException -> UiText.Res(R.string.error_not_enough_memory_analyze)
            is OutOfMemoryError -> UiText.Res(R.string.failure_out_of_memory)
            else -> UiText.Res(R.string.error_generic)
        }
    }

    /** «Операция не выполнена: <причина>» — для операций студии. */
    fun operationFailed(error: Throwable): UiText {
        val reason = reason(error)
        // Потерянный доступ — не «сбой», а просьба выбрать файл: без префикса.
        return if (reason == UiText.Res(R.string.error_access_lost)) reason
        else UiText.Res(R.string.error_operation_failed, reason)
    }

    fun patchIssues(issues: List<PatchIssue>): UiText =
        UiText.Joined(issues.map(::patchIssue), separator = "; ")

    fun patchIssue(issue: PatchIssue): UiText = when (issue.code) {
        PatchIssue.Code.GPS_REMOVE_AND_SET -> UiText.Res(R.string.patch_gps_remove_and_set)
        PatchIssue.Code.GPS_INCOMPLETE -> UiText.Res(R.string.patch_gps_incomplete)
        PatchIssue.Code.LATITUDE_RANGE -> UiText.Res(R.string.patch_latitude_range)
        PatchIssue.Code.LONGITUDE_RANGE -> UiText.Res(R.string.patch_longitude_range)
        PatchIssue.Code.ISO_RANGE -> UiText.Res(R.string.patch_iso_range)
        PatchIssue.Code.APERTURE_RANGE -> UiText.Res(R.string.patch_aperture_range)
        PatchIssue.Code.EXPOSURE_RANGE -> UiText.Res(R.string.patch_exposure_range)
        PatchIssue.Code.TEXT_INVALID -> UiText.Res(R.string.patch_text_invalid, UiText.Res(fieldName(issue.field)))
        PatchIssue.Code.DATE_FORMAT -> UiText.Res(R.string.patch_date_format, UiText.Res(fieldName(issue.field)))
        PatchIssue.Code.NO_CHANGES -> UiText.Res(R.string.patch_no_changes)
    }

    fun configIssue(issue: ApiConfigIssue): UiText = UiText.Res(
        when (issue) {
            ApiConfigIssue.URL_EMPTY -> R.string.api_config_url_empty
            ApiConfigIssue.URL_INVALID -> R.string.api_config_url_invalid
            ApiConfigIssue.URL_NOT_HTTPS -> R.string.api_config_url_not_https
            ApiConfigIssue.URL_NO_HOST -> R.string.api_config_url_no_host
            ApiConfigIssue.URL_HAS_CREDENTIALS -> R.string.api_config_url_has_credentials
            ApiConfigIssue.HEADER_INVALID -> R.string.api_config_header_invalid
            ApiConfigIssue.PIN_INVALID -> R.string.api_config_pin_invalid
        },
    )

    private fun api(e: ApiException): UiText = when (e.code) {
        ApiError.INVALID_CONFIG -> e.configIssue?.let(::configIssue) ?: UiText.Res(R.string.api_error_bad_settings)
        ApiError.DISABLED -> UiText.Res(R.string.api_error_disabled)
        ApiError.CERTIFICATE -> UiText.Res(R.string.api_error_certificate)
        ApiError.TLS_HANDSHAKE -> UiText.Res(R.string.api_error_tls)
        ApiError.HOST_NOT_FOUND -> UiText.Res(R.string.api_error_host_not_found)
        ApiError.TIMEOUT -> UiText.Res(R.string.api_error_timeout)
        ApiError.BAD_SETTINGS -> UiText.Res(R.string.api_error_bad_settings)
        ApiError.KEY_UNREADABLE -> UiText.Res(R.string.api_error_key_unreadable)
        ApiError.REQUEST_FAILED -> UiText.Res(R.string.api_error_request_failed)
    }

    @StringRes
    private fun license(code: LicenseError): Int = when (code) {
        LicenseError.NOT_ENABLED -> R.string.license_error_not_enabled
        LicenseError.MALFORMED -> R.string.license_error_malformed
        LicenseError.BAD_SIGNATURE -> R.string.license_error_bad_signature
        LicenseError.EXPIRED -> R.string.license_error_expired
    }

    @StringRes
    private fun fieldName(field: PatchField?): Int = when (field) {
        PatchField.MAKE -> R.string.patch_field_make
        PatchField.MODEL -> R.string.patch_field_model
        PatchField.AUTHOR -> R.string.patch_field_author
        PatchField.COPYRIGHT -> R.string.patch_field_copyright
        PatchField.DESCRIPTION -> R.string.patch_field_description
        PatchField.DATE_TAKEN -> R.string.patch_field_date_taken
        PatchField.DATE_DIGITIZED -> R.string.patch_field_date_digitized
        null -> R.string.patch_field_unknown
    }

    private fun failure(f: AppFailure): UiText {
        val arg = f.args.firstOrNull().orEmpty()
        return when (f.code) {
            FailureCode.INVALID_URI -> UiText.Res(R.string.failure_invalid_uri)
            FailureCode.SAME_AS_SOURCE -> UiText.Res(R.string.failure_same_as_source)
            FailureCode.SAME_AS_BACKUP -> UiText.Res(R.string.failure_same_as_backup)
            FailureCode.CANNOT_READ_SOURCE -> UiText.Res(R.string.failure_cannot_read_source)
            FailureCode.CANNOT_OPEN_OUTPUT -> UiText.Res(R.string.failure_cannot_open_output)
            FailureCode.NO_SAFE_OVERWRITE -> UiText.Res(R.string.failure_no_safe_overwrite)
            FailureCode.CANNOT_CREATE_IN_FOLDER -> UiText.Res(R.string.failure_cannot_create_in_folder)
            FailureCode.SOURCE_TOO_LARGE -> UiText.Res(R.string.failure_source_too_large)
            FailureCode.FORMAT_UNCONFIRMED ->
                if (arg.isBlank()) UiText.Res(R.string.failure_format_unconfirmed)
                else UiText.Res(R.string.failure_format_unconfirmed_type, arg)
            FailureCode.UNSUPPORTED_IMAGE -> UiText.Res(R.string.failure_unsupported_image)
            FailureCode.EDIT_JPEG_ONLY -> UiText.Res(R.string.failure_edit_jpeg_only)
            FailureCode.OVERWRITE_EDIT_JPEG_ONLY -> UiText.Res(R.string.failure_overwrite_edit_jpeg_only)
            FailureCode.OVERWRITE_STRIP_JPEG_PNG_ONLY -> UiText.Res(R.string.failure_overwrite_strip_jpeg_png_only)
            FailureCode.WIPE_GPS_JPEG_PNG_ONLY -> UiText.Res(R.string.failure_wipe_gps_jpeg_png_only)
            FailureCode.DECODE_FAILED -> UiText.Res(R.string.failure_decode_failed)
            FailureCode.ENCODE_FAILED -> UiText.Res(R.string.failure_encode_failed)
            FailureCode.OUT_OF_MEMORY -> UiText.Res(R.string.failure_out_of_memory)
            FailureCode.EMPTY_RESULT -> UiText.Res(R.string.failure_empty_result)
            FailureCode.CLEAN_CHECK_FAILED -> UiText.Res(R.string.failure_clean_check_failed)
            FailureCode.HIDDEN_DATA_FOUND -> UiText.Res(
                R.string.failure_hidden_data_found,
                UiText.Joined(f.args.mapNotNull(::hiddenPart), separator = ", "),
            )
            FailureCode.GPS_NOT_REMOVED -> UiText.Res(R.string.failure_gps_not_removed)
            FailureCode.GPS_NOT_WRITTEN -> UiText.Res(R.string.failure_gps_not_written)
            FailureCode.GPS_MISMATCH -> UiText.Res(R.string.failure_gps_mismatch)
            FailureCode.EXIF_FIELD_UNVERIFIED -> UiText.Res(R.string.failure_exif_field_unverified, arg)
            FailureCode.SAVED_FILE_UNVERIFIED -> UiText.Res(R.string.failure_saved_file_unverified)
            FailureCode.EDIT_UNVERIFIED -> UiText.Res(R.string.failure_edit_unverified)
            FailureCode.BACKUP_UNVERIFIED -> UiText.Res(R.string.failure_backup_unverified)
            FailureCode.REPLACED_FILE_UNVERIFIED -> UiText.Res(R.string.failure_replaced_file_unverified)
            FailureCode.RESTORE_UNVERIFIED -> UiText.Res(R.string.failure_restore_unverified)
            FailureCode.REPLACE_CANCELLED_RESTORED ->
                UiText.Res(R.string.failure_replace_cancelled_restored, causeOf(f))
            FailureCode.REPLACE_FAILED_RESTORE_FAILED ->
                UiText.Res(R.string.failure_replace_failed_restore_failed, causeOf(f))
            FailureCode.BATCH_SIZE -> UiText.Res(R.string.failure_batch_size)
            FailureCode.HISTORY_UNAVAILABLE -> UiText.Res(R.string.failure_history_unavailable)
            FailureCode.KEYSTORE_UNAVAILABLE -> UiText.Res(R.string.error_keystore_unavailable)
        }
    }

    private fun causeOf(f: AppFailure): UiText = f.cause?.let(::reason) ?: UiText.Res(R.string.error_generic)

    private fun hiddenPart(name: String): UiText? = when (HiddenPart.entries.firstOrNull { it.name == name }) {
        HiddenPart.XMP -> UiText.Plain("XMP")
        HiddenPart.IPTC -> UiText.Plain("IPTC")
        HiddenPart.COMMENT -> UiText.Res(R.string.hidden_comment)
        HiddenPart.MPF -> UiText.Res(R.string.hidden_mpf)
        HiddenPart.TRAILER -> UiText.Res(R.string.hidden_trailer)
        HiddenPart.EXIF_THUMBNAIL -> UiText.Res(R.string.hidden_exif_thumbnail)
        null -> null
    }

    private fun Throwable.causes(): Sequence<Throwable> = generateSequence(this) { it.cause }.take(8)
}
