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
    }
