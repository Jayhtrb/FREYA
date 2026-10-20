package ai.freya.app.util

import java.time.Instant
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneOffset

/**
 * Best-effort ISO-8601 timestamp parsing. The backend mixes offset-bearing,
 * offset-less, and truncated timestamps; fall back to now rather than crash.
 */
fun parseIsoToMillis(value: String?): Long {
    if (value.isNullOrBlank()) return System.currentTimeMillis()
    return try {
        Instant.parse(value).toEpochMilli()
    } catch (_: Exception) {
        try {
            OffsetDateTime.parse(value).toInstant().toEpochMilli()
        } catch (_: Exception) {
            try {
                LocalDateTime.parse(value).atZone(ZoneOffset.UTC).toInstant().toEpochMilli()
            } catch (_: Exception) {
                System.currentTimeMillis()
            }
        }
    }
}
