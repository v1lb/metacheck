package com.example.metacheck.domain

import com.example.metacheck.domain.metadata.MetadataPatch
import com.example.metacheck.domain.metadata.MetadataPatchValidator
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
        assertTrue(errors.any { it.contains("вместе") })
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
        assertTrue(MetadataPatchValidator.validate(MetadataPatch(dateTaken = "2026:02:31 12:12:12")).isNotEmpty())
        assertTrue(MetadataPatchValidator.validate(MetadataPatch(dateTaken = "19.10.2026")).isNotEmpty())
    }
    @Test fun rejectsEmptyPatchAndControlCharacters() {
        assertTrue(MetadataPatchValidator.validate(MetadataPatch()).isNotEmpty())
        assertTrue(MetadataPatchValidator.validate(MetadataPatch(author = "a\u0000b")).isNotEmpty())
    }
    @Test fun checksExposureLimits() {
        assertTrue(MetadataPatchValidator.validate(MetadataPatch(exposureSeconds = 0.0)).isNotEmpty())
        assertTrue(MetadataPatchValidator.validate(MetadataPatch(iso = -1)).isNotEmpty())
        assertTrue(MetadataPatchValidator.validate(MetadataPatch(aperture = 1.8)).isEmpty())
    }
}
