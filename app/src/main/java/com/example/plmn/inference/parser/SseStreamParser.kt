package com.example.plmn.inference.parser

import org.json.JSONObject

/**
 * SseStreamParser: Incremental stateful Server-Sent Events (SSE) stream parser.
 *
 * Handles arbitrary network chunking boundaries, CRLF/LF line endings, comments (`: ping`),
 * multiple events per buffer chunk, partial lines across chunks, and `[DONE]` termination.
 */
class SseStreamParser(
    private val onDataReceived: (data: String) -> Unit
) {
    private val lineBuffer = StringBuilder()
    private var currentEventType: String? = null
    private val eventDataBuffer = StringBuilder()

    fun processChunk(chunk: String) {
        for (i in chunk.indices) {
            val char = chunk[i]
            if (char == '\r') {
                // Ignore CR, handle on LF or next
                continue
            }
            if (char == '\n') {
                flushLine(lineBuffer.toString())
                lineBuffer.setLength(0)
            } else {
                lineBuffer.append(char)
            }
        }
    }

    fun processEof() {
        if (lineBuffer.isNotEmpty()) {
            flushLine(lineBuffer.toString())
            lineBuffer.setLength(0)
        }
        flushEvent()
    }

    private fun flushLine(line: String) {
        val trimmed = line.trim()
        if (trimmed.isEmpty()) {
            // Empty line triggers event dispatch in SSE spec
            flushEvent()
            return
        }

        if (trimmed.startsWith(":")) {
            // SSE comment / keepalive - ignore
            return
        }

        if (trimmed.startsWith("event:")) {
            currentEventType = trimmed.substring(6).trim()
        } else if (trimmed.startsWith("data:")) {
            val dataVal = trimmed.substring(5).trim()
            if (eventDataBuffer.isNotEmpty()) {
                eventDataBuffer.append("\n")
            }
            eventDataBuffer.append(dataVal)
        }
    }

    private fun flushEvent() {
        if (eventDataBuffer.isNotEmpty()) {
            val dataStr = eventDataBuffer.toString()
            eventDataBuffer.setLength(0)
            currentEventType = null
            onDataReceived(dataStr)
        }
    }
}
