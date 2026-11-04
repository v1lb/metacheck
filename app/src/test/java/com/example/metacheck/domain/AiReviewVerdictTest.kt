package com.example.metacheck.domain

import com.example.metacheck.domain.model.Severity
import com.example.metacheck.domain.model.Signal
import com.example.metacheck.domain.model.SignalKind
import com.example.metacheck.sampleReport
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AiReviewVerdictTest {
    private fun withSignals(vararg extra: Signal) =
        sampleReport(ai = false).let { it.copy(signals = it.signals + extra) }

    @Test fun gpsAloneDoesNotMakeAiVerdictInconclusive() {
        assertFalse(withSignals(Signal(Severity.CAUTION, kind = SignalKind.HAS_GPS)).needsAiReview)
    }

    @Test fun strippedMetadataNeedsReview() {
        assertTrue(withSignals(Signal(Severity.CAUTION, kind = SignalKind.NO_METADATA)).needsAiReview)
        assertTrue(withSignals(Signal(Severity.CAUTION, kind = SignalKind.SCREENSHOT)).needsAiReview)
    }

    @Test fun aiEvidenceWinsOverReview() {
        val report = sampleReport(ai = true).let {
            it.copy(signals = it.signals + Signal(Severity.CAUTION, kind = SignalKind.NO_METADATA))
        }
        assertFalse(report.needsAiReview)
    }

    @Test fun truncatedFileNeedsReview() {
        val report = sampleReport(ai = false).let { it.copy(file = it.file.copy(truncated = true)) }
        assertTrue(report.needsAiReview)
    }
}
