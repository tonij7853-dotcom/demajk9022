package chat.stoat.formatting

import androidx.compose.ui.text.TextRange
import org.junit.Assert.assertEquals
import org.junit.Test

class MarkdownFormattingHelperTest {

    @Test
    fun testToggleBoldOnSelection() {
        val text = "Hello world"
        val selection = TextRange(6, 11) // "world"
        val result = MarkdownFormattingHelper.toggleBold(text, selection)

        assertEquals("Hello **world**", result.text)
        assertEquals(TextRange(8, 13), result.selection)
    }

    @Test
    fun testToggleBoldOffWhenSelectedEntirely() {
        val text = "Hello **world**"
        val selection = TextRange(6, 15) // "**world**"
        val result = MarkdownFormattingHelper.toggleBold(text, selection)

        assertEquals("Hello world", result.text)
        assertEquals(TextRange(6, 11), result.selection)
    }

    @Test
    fun testToggleBoldOffWhenSurrounded() {
        val text = "Hello **world**"
        val selection = TextRange(8, 13) // "world" inside "**world**"
        val result = MarkdownFormattingHelper.toggleBold(text, selection)

        assertEquals("Hello world", result.text)
        assertEquals(TextRange(6, 11), result.selection)
    }

    @Test
    fun testToggleBoldNoSelectionInsertsMarkers() {
        val text = "Hello "
        val selection = TextRange(6, 6)
        val result = MarkdownFormattingHelper.toggleBold(text, selection)

        assertEquals("Hello ****", result.text)
        assertEquals(TextRange(8, 8), result.selection)
    }

    @Test
    fun testToggleBoldNoSelectionRemovesMarkersWhenBetween() {
        val text = "Hello ****"
        val selection = TextRange(8, 8)
        val result = MarkdownFormattingHelper.toggleBold(text, selection)

        assertEquals("Hello ", result.text)
        assertEquals(TextRange(6, 6), result.selection)
    }

    @Test
    fun testToggleItalicOnSelection() {
        val text = "Test italic"
        val selection = TextRange(5, 11) // "italic"
        val result = MarkdownFormattingHelper.toggleItalic(text, selection)

        assertEquals("Test *italic*", result.text)
        assertEquals(TextRange(6, 12), result.selection)
    }

    @Test
    fun testToggleItalicOff() {
        val text = "Test *italic*"
        val selection = TextRange(5, 13) // "*italic*"
        val result = MarkdownFormattingHelper.toggleItalic(text, selection)

        assertEquals("Test italic", result.text)
        assertEquals(TextRange(5, 11), result.selection)
    }

    @Test
    fun testToggleCodeOnSelection() {
        val text = "call func() now"
        val selection = TextRange(5, 11) // "func()"
        val result = MarkdownFormattingHelper.toggleCode(text, selection)

        assertEquals("call `func()` now", result.text)
        assertEquals(TextRange(6, 12), result.selection)
    }

    @Test
    fun testToggleHeadingAddsPrefix() {
        val text = "My Title"
        val selection = TextRange(3, 3)
        val result = MarkdownFormattingHelper.toggleHeading(text, selection)

        assertEquals("# My Title", result.text)
        assertEquals(TextRange(5, 5), result.selection)
    }

    @Test
    fun testToggleHeadingRemovesPrefix() {
        val text = "# My Title"
        val selection = TextRange(5, 5)
        val result = MarkdownFormattingHelper.toggleHeading(text, selection)

        assertEquals("My Title", result.text)
        assertEquals(TextRange(3, 3), result.selection)
    }

    @Test
    fun testToggleQuoteAddsPrefix() {
        val text = "A thoughtful quote"
        val selection = TextRange(0, 0)
        val result = MarkdownFormattingHelper.toggleQuote(text, selection)

        assertEquals("> A thoughtful quote", result.text)
    }

    @Test
    fun testToggleQuoteRemovesPrefix() {
        val text = "> A thoughtful quote"
        val selection = TextRange(2, 2)
        val result = MarkdownFormattingHelper.toggleQuote(text, selection)

        assertEquals("A thoughtful quote", result.text)
    }

    @Test
    fun testInsertLinkWithSelection() {
        val text = "Click here for details"
        val selection = TextRange(6, 10) // "here"
        val result = MarkdownFormattingHelper.insertLink(text, selection)

        assertEquals("Click [here](url) for details", result.text)
        // Selects "url"
        assertEquals(TextRange(13, 16), result.selection)
    }

    @Test
    fun testInsertLinkNoSelection() {
        val text = ""
        val selection = TextRange(0, 0)
        val result = MarkdownFormattingHelper.insertLink(text, selection)

        assertEquals("[text](url)", result.text)
        // Selects "text"
        assertEquals(TextRange(1, 5), result.selection)
    }

    @Test
    fun testEmptyStringHandling() {
        val text = ""
        val selection = TextRange(0, 0)
        val result = MarkdownFormattingHelper.toggleBold(text, selection)

        assertEquals("****", result.text)
        assertEquals(TextRange(2, 2), result.selection)
    }
}
