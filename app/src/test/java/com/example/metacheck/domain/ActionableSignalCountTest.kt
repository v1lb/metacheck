package com.example.metacheck.domain

import com.example.metacheck.domain.model.Severity
import com.example.metacheck.domain.model.Signal
import com.example.metacheck.sampleReport
import org.junit.Assert.assertEquals
import org.junit.Test

class ActionableSignalCountTest {
    @Test fun informationalNoteIsNotAnAiDetection() {
        assertEquals(0, sampleReport(ai = false).actionableSignalCount)
    }

    @Test fun genuineAiIndicatorIsCounted() {
        assertEquals(1, sampleReport(ai = true).actionableSignalCount)
    }

    @Test fun cautionAndAiEvidenceAreCountedButNotesAreNot() {
        val report = sampleReport(ai = true).let {
            it.copy(signals = it.signals + listOf(
                Signal(Severity.CAUTION, "Incomplete metadata", "Details"),
                Signal(Severity.INFO, "Nothing conclusive", "Note"),
            ))
        }
        assertEquals(2, report.actionableSignalCount)
    }
}
