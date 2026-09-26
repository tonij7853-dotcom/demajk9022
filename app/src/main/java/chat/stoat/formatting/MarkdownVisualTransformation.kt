package chat.stoat.formatting

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.sp
import chat.stoat.ui.theme.FragmentMono
import chat.stoat.ui.theme.Newsreader

/**
 * VisualTransformation that renders live Markdown styling inside the text field while typing.
 * Syntax markers (*, **, #, `, etc.) are kept in the underlying text but dimmed visually.
 * The plain text remains unaffected so it sends as raw Markdown.
 */
class MarkdownVisualTransformation(
    private val markerColor: Color = Color(0x66888888),
    private val codeBackground: Color = Color(0x22888888),
    private val quoteColor: Color = Color(0xAA888888)
) : VisualTransformation {

    override fun filter(text: AnnotatedString): TransformedText {
        val raw = text.text
        if (raw.isEmpty()) {
            return TransformedText(text, OffsetMapping.Identity)
        }

        val builder = AnnotatedString.Builder(raw)

        // Track code ranges so we ignore other markers inside code spans
        val codeRanges = mutableListOf<IntRange>()

        // 1. Inline code spans: `code`
        val codeRegex = Regex("`([^`\\n]+)`")
        codeRegex.findAll(raw).forEach { match ->
            val range = match.range
            codeRanges.add(range)
            // Dim markers `
            builder.addStyle(SpanStyle(color = markerColor), range.first, range.first + 1)
            builder.addStyle(SpanStyle(color = markerColor), range.last, range.last + 1)
            // Style code content
            builder.addStyle(
                SpanStyle(
                    fontFamily = FragmentMono,
                    background = codeBackground
                ),
                range.first + 1,
                range.last
            )
        }

        fun isInsideCode(start: Int, end: Int): Boolean {
            return codeRanges.any { start >= it.first && end <= it.last + 1 }
        }

        // 2. Bold spans: **bold**
        val boldRegex = Regex("\\*\\*([^\\*\\n]+?)\\*\\*")
        boldRegex.findAll(raw).forEach { match ->
            val start = match.range.first
            val end = match.range.last + 1
            if (!isInsideCode(start, end)) {
                // Dim markers **
                builder.addStyle(SpanStyle(color = markerColor), start, start + 2)
                builder.addStyle(SpanStyle(color = markerColor), end - 2, end)
                // Style bold content
                builder.addStyle(SpanStyle(fontWeight = FontWeight.Bold), start + 2, end - 2)
            }
        }

        // 3. Italic spans: *italic* (ensuring not bold, and not spaced like "5 * 3")
        val italicRegex = Regex("(?<!\\*)\\*([^\\*\\s\\n](?:[^\\*\\n]*?[^\\*\\s\\n])?)\\*(?!\\*)")
        italicRegex.findAll(raw).forEach { match ->
            val start = match.range.first
            val end = match.range.last + 1
            if (!isInsideCode(start, end)) {
                // Dim marker *
                builder.addStyle(SpanStyle(color = markerColor), start, start + 1)
                builder.addStyle(SpanStyle(color = markerColor), end - 1, end)
                // Style italic content
                builder.addStyle(SpanStyle(fontStyle = FontStyle.Italic), start + 1, end - 1)
            }
        }

        // 4. Strikethrough spans: ~~strike~~
        val strikeRegex = Regex("~~([^~\\n]+?)~~")
        strikeRegex.findAll(raw).forEach { match ->
            val start = match.range.first
            val end = match.range.last + 1
            if (!isInsideCode(start, end)) {
                builder.addStyle(SpanStyle(color = markerColor), start, start + 2)
                builder.addStyle(SpanStyle(color = markerColor), end - 2, end)
                builder.addStyle(SpanStyle(textDecoration = TextDecoration.LineThrough), start + 2, end - 2)
            }
        }

        // 5. Spoilers: ||spoiler||
        val spoilerRegex = Regex("\\|\\|([^|\\n]+?)\\|\\|")
        spoilerRegex.findAll(raw).forEach { match ->
            val start = match.range.first
            val end = match.range.last + 1
            if (!isInsideCode(start, end)) {
                builder.addStyle(SpanStyle(color = markerColor), start, start + 2)
                builder.addStyle(SpanStyle(color = markerColor), end - 2, end)
                builder.addStyle(SpanStyle(background = codeBackground), start + 2, end - 2)
            }
        }

        // 6. Headings and Quotes on lines
        var lineStart = 0
        raw.lines().forEach { line ->
            val lineEnd = lineStart + line.length

            if (!isInsideCode(lineStart, lineEnd)) {
                // Heading: # Heading or ## Heading or ### Heading
                val headingMatch = Regex("^(#{1,3})\\s+(.*)$").find(line)
                if (headingMatch != null) {
                    val hashes = headingMatch.groupValues[1]
                    val markerLen = hashes.length + 1 // including the trailing space
                    builder.addStyle(SpanStyle(color = markerColor), lineStart, lineStart + markerLen)
                    builder.addStyle(
                        SpanStyle(
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = Newsreader
                        ),
                        lineStart + markerLen,
                        lineEnd
                    )
                }

                // Blockquote: > quote
                if (line.startsWith("> ")) {
                    builder.addStyle(SpanStyle(color = markerColor), lineStart, lineStart + 2)
                    builder.addStyle(
                        SpanStyle(fontStyle = FontStyle.Italic, color = quoteColor),
                        lineStart + 2,
                        lineEnd
                    )
                }
            }

            lineStart = lineEnd + 1 // account for newline character
        }

        return TransformedText(builder.toAnnotatedString(), OffsetMapping.Identity)
    }
}
