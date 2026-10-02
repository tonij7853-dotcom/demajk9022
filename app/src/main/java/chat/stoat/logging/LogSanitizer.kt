package chat.stoat.logging

import android.net.Uri

/**
 * Privacy sanitizer to ensure passwords, auth tokens, session tokens, and message bodies
 * are never written to disk or exported in diagnostics logs.
 */
object LogSanitizer {
    private val SENSITIVE_KEY_PATTERN = Regex(
        "(?i).*(password|token|session|auth|secret|cookie|credential|ticket|bearer|apiKey|private_key).*"
    )

    private val JWT_PATTERN = Regex("eyJ[A-Za-z0-9_-]{15,}\\.[A-Za-z0-9_-]{15,}(\\.[A-Za-z0-9_-]+)?")
    private val SESSION_TOKEN_PATTERN = Regex("(x-session-token[:=]|bearer\\s+)[A-Za-z0-9_-]{20,}", RegexOption.IGNORE_CASE)
    private val URL_AUTH_PARAM_PATTERN = Regex("(?i)([?&](token|key|session|auth|password)=)[^&]+")

    /**
     * Sanitizes an arbitrary map of log details, redacting any sensitive keys or values.
     */
    fun sanitizeMap(map: Map<String, Any?>): Map<String, Any?> {
        val result = LinkedHashMap<String, Any?>(map.size)
        for ((key, value) in map) {
            result[key] = sanitizeEntry(key, value)
        }
        return result
    }

    private fun sanitizeEntry(key: String, value: Any?): Any? {
        if (value == null) return null

        // Redact message content bodies to preserve user chat privacy
        if (key.equals("content", ignoreCase = true) ||
            key.equals("message_content", ignoreCase = true) ||
            key.equals("messageContent", ignoreCase = true) ||
            key.equals("raw_body", ignoreCase = true)
        ) {
            val len = (value as? String)?.length ?: value.toString().length
            return "[REDACTED_CHAT_CONTENT ($len chars)]"
        }

        // Redact sensitive keys
        if (SENSITIVE_KEY_PATTERN.matches(key)) {
            return "[REDACTED]"
        }

        return when (value) {
            is Map<*, *> -> {
                val nested = LinkedHashMap<String, Any?>()
                for ((k, v) in value) {
                    if (k is String) {
                        nested[k] = sanitizeEntry(k, v)
                    } else if (k != null) {
                        nested[k.toString()] = sanitizeValue(v)
                    }
                }
                nested
            }
            is List<*> -> value.map { sanitizeValue(it) }
            is Set<*> -> value.map { sanitizeValue(it) }.toSet()
            is String -> sanitizeString(value)
            is Number, is Boolean -> value
            else -> sanitizeString(value.toString())
        }
    }

    private fun sanitizeValue(value: Any?): Any? {
        if (value == null) return null
        return when (value) {
            is String -> sanitizeString(value)
            is Map<*, *> -> sanitizeMap(value.filterKeys { it is String } as Map<String, Any?>)
            is List<*> -> value.map { sanitizeValue(it) }
            is Number, is Boolean -> value
            else -> sanitizeString(value.toString())
        }
    }

    /**
     * Masks any embedded tokens, JWTs, or auth parameters found in a freeform string.
     */
    fun sanitizeString(text: String): String {
        var sanitized = text
        if (sanitized.contains("eyJ", ignoreCase = false)) {
            sanitized = JWT_PATTERN.replace(sanitized, "[REDACTED_JWT]")
        }
        if (SESSION_TOKEN_PATTERN.containsMatchIn(sanitized)) {
            sanitized = SESSION_TOKEN_PATTERN.replace(sanitized, "$1[REDACTED_TOKEN]")
        }
        if (URL_AUTH_PARAM_PATTERN.containsMatchIn(sanitized)) {
            sanitized = URL_AUTH_PARAM_PATTERN.replace(sanitized, "$1[REDACTED]")
        }
        return sanitized
    }

    /**
     * Sanitizes a URL by removing any sensitive query parameters like ?token=... or ?key=...
     */
    fun sanitizeUrl(url: String): String {
        return try {
            val parsed = Uri.parse(url)
            val scheme = parsed.scheme ?: return sanitizeString(url)
            val host = parsed.host ?: ""
            val path = parsed.path ?: ""
            val queryNames = parsed.queryParameterNames
            if (queryNames.isEmpty()) {
                "$scheme://$host$path"
            } else {
                val cleanParams = queryNames.map { name ->
                    if (SENSITIVE_KEY_PATTERN.matches(name)) {
                        "$name=[REDACTED]"
                    } else {
                        "$name=${parsed.getQueryParameter(name)}"
                    }
                }.joinToString("&")
                "$scheme://$host$path?$cleanParams"
            }
        } catch (_: Exception) {
            URL_AUTH_PARAM_PATTERN.replace(url, "$1[REDACTED]")
        }
    }
}
