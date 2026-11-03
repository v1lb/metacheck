package com.example.metacheck.domain

import com.example.metacheck.domain.metadata.MetadataPatch
import com.example.metacheck.domain.metadata.MetadataPatchValidator
import com.example.metacheck.domain.metadata.PatchField
import com.example.metacheck.domain.metadata.PatchIssue
import org.junit.Assert.*
import org.junit.Test

class MetadataPatchValidatorTest {
    @Test fun acceptsValidGpsAndExposure() {
        val data = MetadataPatch(latitude = 41.9028, longitude = 12.4964, iso = 400,
            aperture = 1.8, exposureSeconds = .025, dateTaken = "2026:10:19 12:20:30")
        assertTrue(MetadataPatchValidator.validate(data).toString(), MetadataPatchValidator.validate(data).isEmpty())
    }
    @Test fun rejectsOnlyOneGpsCoordinate() {
        val errors = MetadataPatchValidator.validate(MetadataPatch(latitude = 41.9))
        assertTrue(errors.toString(), errors.any { it.code == PatchIssue.Code.GPS_INCOMPLETE })
    }
    @Test fun rejectsGpsOutOfRangeAndNonFinite() {
        assertTrue(MetadataPatchValidator.validate(MetadataPatch(latitude = 91.0, longitude = 0.0)).isNotEmpty())
        assertTrue(MetadataPatchValidator.validate(MetadataPatch(latitude = Double.NaN, longitude = 0.0)).isNotEmpty())
    }
    @Test fun rejectsGpsModificationWhenRemovalRequested() {
        assertTrue(MetadataPatchValidator.validate(MetadataPatch(removeGps = true, latitude = 1.0, longitude = 2.0)).isNotEmpty())
    }
    @Test fun supportsRemoveGpsAlone() {
        assertTrue(MetadataPatchValidator.validate(MetadataPatch(removeGps = true)).isEmpty())
    }
    @Test fun rejectsMalformedDate() {
        assertEquals(listOf(PatchIssue(PatchIssue.Code.DATE_FORMAT, PatchField.DATE_TAKEN)),
            MetadataPatchValidator.validate(MetadataPatch(dateTaken = "2026:02:31 12:12:12")))
        assertTrue(MetadataPatchValidator.validate(MetadataPatch(dateTaken = "19.10.2026")).isNotEmpty())
    }
    @Test fun rejectsEmptyPatchAndControlCharacters() {
        assertEquals(listOf(PatchIssue(PatchIssue.Code.NO_CHANGES, null)), MetadataPatchValidator.validate(MetadataPatch()))
        assertEquals(listOf(PatchIssue(PatchIssue.Code.TEXT_INVALID, PatchField.AUTHOR)),
            MetadataPatchValidator.validate(MetadataPatch(author = "a\u0000b")))
    }
    @Test fun checksExposureLimits() {
        assertTrue(MetadataPatchValidator.validate(MetadataPatch(exposureSeconds = 0.0)).isNotEmpty())
        assertTrue(MetadataPatchValidator.validate(MetadataPatch(iso = -1)).isNotEmpty())
        assertTrue(MetadataPatchValidator.validate(MetadataPatch(aperture = 1.8)).isEmpty())
    }
}
