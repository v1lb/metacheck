package com.example.metacheck.ui

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Подсказки, которые показывает главный экран по событиям из ViewModel студии:
 * окно Pro, когда исчерпан дневной лимит, и просьба оценить приложение после удачных очисток.
 * ViewModel не держит Activity, поэтому события идут через этот объект.
 */
object AppPrompts {
    private val _paywall = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val paywall: SharedFlow<Unit> = _paywall.asSharedFlow()

    private val _review = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val review: SharedFlow<Unit> = _review.asSharedFlow()

    fun dailyLimitReached() { _paywall.tryEmit(Unit) }

    fun cleanSucceeded() { _review.tryEmit(Unit) }
}
