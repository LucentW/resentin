package pm.antani.resentin.ui.common

import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import pm.antani.resentin.irc.UrlDetector
import pm.antani.resentin.mirc.MircParser
import pm.antani.resentin.mirc.MircSpan

/** Standard mIRC 16-color palette (codes 0-15). */
private val mircColors = listOf(
    Color(0xFFFFFFFF), // 0 white
    Color(0xFF000000), // 1 black
    Color(0xFF00007F), // 2 blue
    Color(0xFF009300), // 3 green
    Color(0xFFFF0000), // 4 red
    Color(0xFF7F0000), // 5 brown
    Color(0xFF9C009C), // 6 purple
    Color(0xFFFC7F00), // 7 orange
    Color(0xFFFFFF00), // 8 yellow
    Color(0xFF00FC00), // 9 light green
    Color(0xFF009393), // 10 cyan
    Color(0xFF00FFFF), // 11 light cyan
    Color(0xFF0000FC), // 12 light blue
    Color(0xFFFF00FF), // 13 pink
    Color(0xFF7F7F7F), // 14 grey
    Color(0xFFD2D2D2), // 15 light grey
)

private fun mircColorOrNull(code: Int?): Color? = code?.let { mircColors.getOrNull(it) }

// A fixed link blue rather than a MaterialTheme color: this file builds the
// AnnotatedString outside of composition (mircAnnotatedString/withClickableLinks are
// plain functions, reused as-is by the topic dialog), and a link needs to read as a
// link the same way regardless of whatever mIRC color the surrounding text carries.
private val linkStyles = TextLinkStyles(
    style = SpanStyle(color = Color(0xFF4A9EFF), textDecoration = TextDecoration.Underline),
)

fun mircAnnotatedString(text: String): AnnotatedString = buildAnnotatedString {
    MircParser.parse(text).forEach { span ->
        withStyle(
            SpanStyle(
                color = mircColorOrNull(span.foreground) ?: Color.Unspecified,
                background = mircColorOrNull(span.background) ?: Color.Unspecified,
                fontWeight = if (span.bold) FontWeight.Bold else null,
                fontStyle = if (span.italic) FontStyle.Italic else null,
                textDecoration = when {
                    span.underline && span.strikethrough -> TextDecoration.combine(
                        listOf(TextDecoration.Underline, TextDecoration.LineThrough),
                    )
                    span.underline -> TextDecoration.Underline
                    span.strikethrough -> TextDecoration.LineThrough
                    else -> null
                },
            ),
        ) {
            append(span.text)
        }
    }
}

/** Layers clickable [LinkAnnotation.Url] ranges on top of an already-built
 * [AnnotatedString] (which may already carry mIRC color/bold/etc. spans) — Text renders
 * a link's default styling and opens it via the platform URI handler automatically, no
 * manual tap handling needed. */
fun withClickableLinks(annotated: AnnotatedString): AnnotatedString {
    val ranges = UrlDetector.find(annotated.text)
    if (ranges.isEmpty()) return annotated
    return AnnotatedString.Builder(annotated).apply {
        ranges.forEach { range ->
            addLink(
                LinkAnnotation.Url(annotated.text.substring(range.first, range.last + 1), linkStyles),
                range.first,
                range.last + 1,
            )
        }
    }.toAnnotatedString()
}

@Composable
fun MircText(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = LocalTextStyle.current,
    color: Color = Color.Unspecified,
    maxLines: Int = Int.MAX_VALUE,
    overflow: TextOverflow = TextOverflow.Clip,
) {
    val annotated = remember(text) { withClickableLinks(mircAnnotatedString(text)) }
    Text(text = annotated, modifier = modifier, style = style, color = color, maxLines = maxLines, overflow = overflow)
}
