package com.example.metacheck.domain.metadata

import com.example.metacheck.domain.model.MetaReport
import java.util.Locale

/** Signals are based on actual GPS tags, never on guesses from location-like text. */
object GpsPrivacy {
    fun hasExifLocation(report: MetaReport): Boolean = report.gps != null ||
        report.exifSections.any { it.title == "Геолокация EXIF" && it.items.isNotEmpty() }

    /** Also recognize explicit XMP and PNG GPS keys. The map still needs a valid EXIF coordinate pair. */
    fun hasLocationMarkers(report: MetaReport): Boolean = hasExifLocation(report) ||
        (report.xmpItems + report.pngTextItems).any {
            val label = it.label.lowercase(Locale.ROOT)
            label.contains("gpslatitude") || label.contains("gpslongitude") ||
                label.contains("gpsaltitude") || label.startsWith("gps:") ||
                label.startsWith("gps_")
        }

    fun validCoordinates(latitude: Double, longitude: Double): Boolean =
        latitude.isFinite() && longitude.isFinite() && latitude in -90.0..90.0 && longitude in -180.0..180.0

    fun label(latitude: Double, longitude: Double): String {
        require(validCoordinates(latitude, longitude))
        return String.format(Locale.ROOT, "%.6f°, %.6f°", latitude, longitude)
    }

    /** Exact geographic position, rendered in an equirectangular offline viewport. */
    fun longitudeDelta(longitude: Double, center: Double): Double {
        require(validCoordinates(0.0, longitude) && validCoordinates(0.0, center))
        val raw = longitude - center
        return when { raw > 180.0 -> raw - 360.0; raw < -180.0 -> raw + 360.0; else -> raw }
    }

    fun mapX(longitude: Double, center: Double, degreesSpan: Double): Float =
        (.5 + longitudeDelta(longitude, center) / degreesSpan).toFloat()

    fun mapY(latitude: Double, center: Double, degreesSpan: Double): Float =
        (.5 - (latitude - center) / degreesSpan).toFloat()
}
