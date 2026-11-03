package com.example.metacheck.domain

import kotlinx.coroutines.CancellationException

/** runCatching, который не глотает отмену корутины. */
inline fun <T> runCatchingCancellable(block: () -> T): Result<T> =
    try {
        Result.success(block())
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Result.failure(e)
    } catch (e: StackOverflowError) {
        // Файл, специально собранный против рекурсивного разбора (EXIF, XMP), не должен ронять приложение.
        Result.failure(AppFailure(FailureCode.CANNOT_READ_SOURCE, cause = e))
    } catch (e: OutOfMemoryError) {
        Result.failure(AppFailure(FailureCode.OUT_OF_MEMORY, cause = e))
    }
