package com.valhalla.bolt.model

/** Decode recovery UI messages for display, without altering the retained installer output. */
fun readableOperationLog(raw: String): String = buildString(raw.length) {
    var start = 0
    while (start < raw.length) {
        val newline = raw.indexOf('\n', start)
        val end = if (newline < 0) raw.length else newline
        val line = raw.substring(start, end).removeSuffix("\r")
        val command = line.trimStart(' ', '\t')
        val displayed = when {
            // AnyKernel emits an empty command after each message to flush recovery's UI.
            command == "ui_print" -> null
            command.startsWith("ui_print ") || command.startsWith("ui_print\t") -> command.substring(9)
            else -> line
        }
        if (displayed != null) {
            append(displayed)
            if (newline >= 0) append('\n')
        }
        start = end + 1
    }
}
