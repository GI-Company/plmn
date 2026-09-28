package com.example.plmn.inference.parser

import org.json.JSONObject

/**
 * NdjsonStreamParser: Incremental stateful NDJSON (Newline Delimited JSON) stream parser.
 *
 * Handles line fragments split across arbitrary network read buffers and malformed JSON recovery.
 */
class NdjsonStreamParser(
    private val onJsonReceived: (json: JSONObject) -> Unit
) {
    private val buffer = StringBuilder()

    fun processChunk(chunk: String) {
        for (i in chunk.indices) {
            val char = chunk[i]
            if (char == '\r') continue
            if (char == '\n') {
                flushLine(buffer.toString())
                buffer.setLength(0)
            } else {
                buffer.append(char)
            }
        }
    }

    fun processEof() {
        if (buffer.isNotEmpty()) {
            flushLine(buffer.toString())
            buffer.setLength(0)
        }
    }

    private fun flushLine(line: String) {
        val trimmed = line.trim()
        if (trimmed.isEmpty()) return
        try {
            val json = JSONObject(trimmed)
            onJsonReceived(json)
        } catch (_: Exception) {
            // Malformed JSON chunk fragment ignored safely
        }
    }
}
