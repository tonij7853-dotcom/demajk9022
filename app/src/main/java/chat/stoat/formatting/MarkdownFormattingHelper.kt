package chat.stoat.formatting

import androidx.compose.ui.text.TextRange

data class FormattingResult(
    val text: String,
    val selection: TextRange
)

object MarkdownFormattingHelper {

    fun toggleBold(text: String, selection: TextRange): FormattingResult =
        toggleMarker(text, selection, prefix = "**", suffix = "**")

    fun toggleItalic(text: String, selection: TextRange): FormattingResult =
        toggleMarker(text, selection, prefix = "*", suffix = "*")

    fun toggleCode(text: String, selection: TextRange): FormattingResult =
        toggleMarker(text, selection, prefix = "`", suffix = "`")

    fun toggleStrikethrough(text: String, selection: TextRange): FormattingResult =
        toggleMarker(text, selection, prefix = "~~", suffix = "~~")

    fun toggleMarker(
        text: String,
        selection: TextRange,
        prefix: String,
        suffix: String = prefix
    ): FormattingResult {
        val start = selection.min.coerceIn(0, text.length)
        val end = selection.max.coerceIn(0, text.length)

        if (start < end) {
            val selectedText = text.substring(start, end)

            // Subcase A: Selection itself starts with prefix and ends with suffix
            if (selectedText.length >= prefix.length + suffix.length &&
                selectedText.startsWith(prefix) && selectedText.endsWith(suffix)
            ) {
                val unwrapped = selectedText.substring(prefix.length, selectedText.length - suffix.length)
                val newText = text.substring(0, start) + unwrapped + text.substring(end)
                return FormattingResult(newText, TextRange(start, start + unwrapped.length))
            }

            // Subcase B: Text immediately surrounding the selection has prefix and suffix
            if (start >= prefix.length && end + suffix.length <= text.length &&
                text.substring(start - prefix.length, start) == prefix &&
                text.substring(end, end + suffix.length) == suffix
            ) {
                val newText = text.substring(0, start - prefix.length) + selectedText + text.substring(end + suffix.length)
                val newStart = start - prefix.length
                return FormattingResult(newText, TextRange(newStart, newStart + selectedText.length))
            }

            // Subcase C: Wrap selection
            val wrapped = prefix + selectedText + suffix
            val newText = text.substring(0, start) + wrapped + text.substring(end)
            return FormattingResult(newText, TextRange(start + prefix.length, start + prefix.length + selectedText.length))
        } else {
            // Cursor only, no selection
            val cursor = start

            // Subcase A: Cursor between prefix and suffix
            if (cursor >= prefix.length && cursor + suffix.length <= text.length &&
                text.substring(cursor - prefix.length, cursor) == prefix &&
                text.substring(cursor, cursor + suffix.length) == suffix
            ) {
                val newText = text.substring(0, cursor - prefix.length) + text.substring(cursor + suffix.length)
                return FormattingResult(newText, TextRange(cursor - prefix.length))
            }

            // Subcase B: Insert empty markers with cursor inside
            val newText = text.substring(0, cursor) + prefix + suffix + text.substring(cursor)
            return FormattingResult(newText, TextRange(cursor + prefix.length))
        }
    }

    fun toggleHeading(text: String, selection: TextRange): FormattingResult {
        if (text.isEmpty()) {
            return FormattingResult("# ", TextRange(2))
        }

        val cursor = selection.start.coerceIn(0, text.length)
        val lineStart = text.lastIndexOf('\n', (cursor - 1).coerceAtLeast(0)).let {
            if (it == -1) 0 else it + 1
        }
        val lineEnd = text.indexOf('\n', cursor).let {
            if (it == -1) text.length else it
        }

        val line = text.substring(lineStart, lineEnd)

        return if (line.startsWith("# ")) {
            val newLine = line.removePrefix("# ")
            val newText = text.substring(0, lineStart) + newLine + text.substring(lineEnd)
            val newCursor = (cursor - 2).coerceIn(lineStart, lineStart + newLine.length)
            FormattingResult(newText, TextRange(newCursor))
        } else {
            val newLine = "# $line"
            val newText = text.substring(0, lineStart) + newLine + text.substring(lineEnd)
            val newCursor = (cursor + 2).coerceIn(lineStart, lineStart + newLine.length)
            FormattingResult(newText, TextRange(newCursor))
        }
    }

    fun toggleQuote(text: String, selection: TextRange): FormattingResult {
        if (text.isEmpty()) {
            return FormattingResult("> ", TextRange(2))
        }

        val cursor = selection.start.coerceIn(0, text.length)
        val lineStart = text.lastIndexOf('\n', (cursor - 1).coerceAtLeast(0)).let {
            if (it == -1) 0 else it + 1
        }
        val lineEnd = text.indexOf('\n', cursor).let {
            if (it == -1) text.length else it
        }

        val line = text.substring(lineStart, lineEnd)

        return if (line.startsWith("> ")) {
            val newLine = line.removePrefix("> ")
            val newText = text.substring(0, lineStart) + newLine + text.substring(lineEnd)
            val newCursor = (cursor - 2).coerceIn(lineStart, lineStart + newLine.length)
            FormattingResult(newText, TextRange(newCursor))
        } else {
            val newLine = "> $line"
            val newText = text.substring(0, lineStart) + newLine + text.substring(lineEnd)
            val newCursor = (cursor + 2).coerceIn(lineStart, lineStart + newLine.length)
            FormattingResult(newText, TextRange(newCursor))
        }
    }

    fun insertLink(text: String, selection: TextRange): FormattingResult {
        val start = selection.min.coerceIn(0, text.length)
        val end = selection.max.coerceIn(0, text.length)

        return if (start < end) {
            val selectedText = text.substring(start, end)
            val inserted = "[$selectedText](url)"
            val newText = text.substring(0, start) + inserted + text.substring(end)
            // Select "url" so user can immediately type the link
            val urlStart = start + selectedText.length + 3
            FormattingResult(newText, TextRange(urlStart, urlStart + 3))
        } else {
            val inserted = "[text](url)"
            val newText = text.substring(0, start) + inserted + text.substring(start)
            // Select "text"
            FormattingResult(newText, TextRange(start + 1, start + 5))
        }
    }
}
