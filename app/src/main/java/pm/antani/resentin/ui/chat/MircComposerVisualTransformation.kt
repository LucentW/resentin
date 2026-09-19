package pm.antani.resentin.ui.chat

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import pm.antani.resentin.mirc.isMircHexDigit
import pm.antani.resentin.ui.common.mircAnnotatedString

/**
 * Keeps IRC control codes in the editable value while rendering the draft as formatted text.
 * Without this transformation Compose's TextField renders the draft plainly, even though the
 * codes are correctly preserved for sending.
 */
internal object MircComposerVisualTransformation : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val source = text.text
        val transformed = mircAnnotatedString(source)
        val originalToTransformed = IntArray(source.length + 1)
        val transformedToOriginal = IntArray(transformed.text.length + 1)

        var originalIndex = 0
        var transformedIndex = 0
        while (originalIndex < source.length) {
            val controlLength = mircControlCodeLength(source, originalIndex)
            if (controlLength > 0) {
                repeat(controlLength) {
                    originalToTransformed[originalIndex++] = transformedIndex
                }
                originalToTransformed[originalIndex] = transformedIndex
                transformedToOriginal[transformedIndex] = originalIndex
            } else {
                originalToTransformed[originalIndex] = transformedIndex
                transformedToOriginal[transformedIndex] = originalIndex
                originalIndex++
                transformedIndex++
                originalToTransformed[originalIndex] = transformedIndex
                transformedToOriginal[transformedIndex] = originalIndex
            }
        }

        val offsetMapping = object : OffsetMapping {
            override fun originalToTransformed(offset: Int): Int =
                originalToTransformed[offset.coerceIn(0, source.length)]

            override fun transformedToOriginal(offset: Int): Int =
                transformedToOriginal[offset.coerceIn(0, transformed.text.length)]
        }
        return TransformedText(transformed, offsetMapping)
    }
}

// Must mirror MircParser exactly: every byte the parser consumes but does not
// render has to map onto the same transformed offset, or the composer cursor
// drifts on text carrying those codes.
private fun mircControlCodeLength(text: String, index: Int): Int = when (text[index].code) {
    2, 15, 29, 30, 31 -> 1
    17, 22 -> 1
    4 -> {
        var cursor = index + 1
        repeat(6) { if (cursor < text.length && text[cursor].isMircHexDigit()) cursor++ }
        if (cursor < text.length && text[cursor] == ',') {
            var j = cursor + 1
            var digits = 0
            while (j < text.length && text[j].isMircHexDigit() && digits < 6) {
                j++
                digits++
            }
            if (digits == 6) cursor = j
        }
        cursor - index
    }
    3 -> {
        var cursor = index + 1
        var foregroundDigits = 0
        while (cursor < text.length && text[cursor].isDigit() && foregroundDigits < 2) {
            cursor++
            foregroundDigits++
        }
        if (cursor < text.length && text[cursor] == ',') {
            var backgroundCursor = cursor + 1
            var backgroundDigits = 0
            while (backgroundCursor < text.length && text[backgroundCursor].isDigit() && backgroundDigits < 2) {
                backgroundCursor++
                backgroundDigits++
            }
            if (backgroundDigits > 0) cursor = backgroundCursor
        }
        cursor - index
    }
    else -> 0
}
