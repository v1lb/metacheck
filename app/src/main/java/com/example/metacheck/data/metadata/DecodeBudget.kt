package com.example.metacheck.data.metadata

/**
 * Выбор inSampleSize для декодирования с учётом свободной памяти процесса (аудит, п. 12).
 *
 * Раньше учитывался только предел 16 Мп: 64 МБ на ARGB-растр плюс ещё 64 МБ на повёрнутую копию.
 * На телефонах с кучей 128–256 МБ это OutOfMemoryError. Теперь уменьшаем, пока не выполнены оба условия:
 *  - пикселей не больше [maxPixels];
 *  - все одновременно живущие копии растра занимают не больше [budgetShare] свободной памяти.
 */
internal object DecodeBudget {
    const val BYTES_PER_PIXEL = 4L // ARGB_8888
    const val DEFAULT_BUDGET_SHARE = 0.5

    fun sampleSize(
        width: Int,
        height: Int,
        maxPixels: Long,
        copies: Int,
        availableBytes: Long,
        budgetShare: Double = DEFAULT_BUDGET_SHARE,
    ): Int {
        require(width > 0 && height > 0 && copies >= 1)
        val budget = (availableBytes.coerceAtLeast(0) * budgetShare).toLong()
        var sample = 1
        while (sample < MAX_SAMPLE) {
            val w = (width / sample).toLong().coerceAtLeast(1)
            val h = (height / sample).toLong().coerceAtLeast(1)
            val pixels = w * h
            if (pixels <= maxPixels && pixels * BYTES_PER_PIXEL * copies <= budget) return sample
            sample *= 2
        }
        return MAX_SAMPLE
    }

    /** Сколько ещё можно выделить в куче Java до достижения лимита процесса. */
    fun availableHeapBytes(runtime: Runtime = Runtime.getRuntime()): Long =
        runtime.maxMemory() - (runtime.totalMemory() - runtime.freeMemory())

    private const val MAX_SAMPLE = 1 shl 10
}
