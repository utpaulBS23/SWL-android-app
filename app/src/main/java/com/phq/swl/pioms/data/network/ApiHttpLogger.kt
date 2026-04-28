package com.phq.swl.pioms.data.network

import android.util.Log
import org.json.JSONObject

object ApiHttpLogger {
    private const val TAG = "PIOMS_API"

    fun logRequest(
        method: String,
        url: String,
        headers: Map<String, String?> = emptyMap(),
        bodyText: String? = null,
    ) {
        val safeHeaders =
            headers.mapValues { (_, v) ->
                v?.let { redactHeaderValue(it) }
            }

        val safeBody = bodyText?.let { redactBodyText(it) }

        val msg =
            buildString {
                append("REQUEST ").append(method).append(" ").append(url)
                if (safeHeaders.isNotEmpty()) {
                    append("\nheaders=").append(JSONObject(safeHeaders as Map<*, *>).toString())
                }
                if (!safeBody.isNullOrBlank()) {
                    append("\nbody=").append(trimForLog(safeBody))
                }
            }
        Log.d(TAG, msg)
    }

    fun logResponse(
        method: String,
        url: String,
        statusCode: Int,
        bodyText: String? = null,
    ) {
        val safeBody = bodyText?.let { redactBodyText(it) }
        val msg =
            buildString {
                append("RESPONSE ").append(method).append(" ").append(url).append(" status=").append(statusCode)
                if (!safeBody.isNullOrBlank()) {
                    append("\nbody=").append(trimForLog(safeBody))
                }
            }
        Log.d(TAG, msg)
    }

    fun logBinaryResponse(
        method: String,
        url: String,
        statusCode: Int,
        byteCount: Int,
    ) {
        Log.d(TAG, "RESPONSE $method $url status=$statusCode bytes=$byteCount")
    }

    private fun redactHeaderValue(value: String): String {
        val trimmed = value.trim()
        return if (trimmed.startsWith("Bearer ", ignoreCase = true)) {
            "Bearer ***"
        } else {
            trimmed
        }
    }

    private fun redactBodyText(text: String): String {
        var t = text
        t = t.replace(Regex("\"Password\"\\s*:\\s*\".*?\"", RegexOption.IGNORE_CASE), "\"Password\":\"***\"")
        t = t.replace(Regex("\"OTP\"\\s*:\\s*\".*?\"", RegexOption.IGNORE_CASE), "\"OTP\":\"***\"")
        t = t.replace(Regex("\"otp\"\\s*:\\s*\".*?\"", RegexOption.IGNORE_CASE), "\"otp\":\"***\"")
        t = t.replace(Regex("\"token\"\\s*:\\s*\".*?\"", RegexOption.IGNORE_CASE), "\"token\":\"***\"")
        t = t.replace(Regex("\"Authorization\"\\s*:\\s*\".*?\"", RegexOption.IGNORE_CASE), "\"Authorization\":\"***\"")
        return t
    }

    private fun trimForLog(text: String, maxChars: Int = 2000): String =
        if (text.length <= maxChars) text else text.take(maxChars) + "…"
}
