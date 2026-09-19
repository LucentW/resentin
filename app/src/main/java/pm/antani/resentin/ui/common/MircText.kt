package pm.antani.resentin.ui.common

import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import pm.antani.resentin.irc.ChannelReferenceDetector
import pm.antani.resentin.irc.DccFileLinkDetector
import pm.antani.resentin.irc.UrlDetector
import pm.antani.resentin.mirc.MircParser
import pm.antani.resentin.mirc.MircSpan

/** Standard mIRC 16-color palette (codes 0-15) — tuned for dark surfaces. */
private val mircColorsDark = listOf(
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

/** Same codes remapped for light surfaces — the pale entries (white, yellow,
 * light green/cyan/grey, orange, pink) would vanish on near-white, so they
 * render as their dark counterparts instead, the way desktop IRC clients do. */
private val mircColorsLight = listOf(
    Color(0xFF1C1B20), // 0 white -> dark surface tone
    Color(0xFF000000), // 1 black
    Color(0xFF00007F), // 2 blue
    Color(0xFF009300), // 3 green
    Color(0xFFFF0000), // 4 red
    Color(0xFF7F0000), // 5 brown
    Color(0xFF9C009C), // 6 purple
    Color(0xFF9A5200), // 7 orange -> dark orange
    Color(0xFF756800), // 8 yellow -> dark olive
    Color(0xFF006E00), // 9 light green -> dark green
    Color(0xFF00696D), // 10 cyan -> dark teal
    Color(0xFF00838F), // 11 light cyan -> material cyan 700
    Color(0xFF0000FC), // 12 light blue
    Color(0xFFA800A8), // 13 pink -> dark magenta
    Color(0xFF7F7F7F), // 14 grey
    Color(0xFF616161), // 15 light grey -> medium grey
)

private fun mircColorOrNull(code: Int?, lightTheme: Boolean): Color? =
    code?.let {
        if (lightTheme && it in mircColorsLight.indices) mircColorsLight[it]
        else MircPaletteArgb.getOrNull(it)?.let { rgb -> Color(0xFF000000L or rgb.toLong()) }
    }

/** Whether the app is currently rendering on a light surface — derived from the
 * theme's own background (not the OS setting) so the forced Sistema/Chiaro/Scuro
 * override is honored. */
@Composable
fun isLightTheme(): Boolean = MaterialTheme.colorScheme.background.luminance() > 0.5f

// A fixed link blue rather than a MaterialTheme color: this file builds the
// AnnotatedString outside of composition (mircAnnotatedString/withClickableLinks are
// plain functions reused by chat text and the expanded topic), and a link needs to read as a
// link the same way regardless of whatever mIRC color the surrounding text carries.
// Two variants: the bright one vanishes on white, the dark one is muddy on black.
private val darkLinkStyles = TextLinkStyles(
    style = SpanStyle(color = Color(0xFF4A9EFF), textDecoration = TextDecoration.Underline),
)
private val lightLinkStyles = TextLinkStyles(
    style = SpanStyle(color = Color(0xFF0B57D0), textDecoration = TextDecoration.Underline),
)

internal fun linkStylesFor(lightTheme: Boolean): TextLinkStyles =
    if (lightTheme) lightLinkStyles else darkLinkStyles

/** Handles a tap on a DCC delivery report's download path (see [DccFileLinkDetector]) —
 * default no-op so [mircAnnotatedString]'s other callers (e.g. expanded topic text) don't
 * need to know it exists. Chat screens provide the real
 * handler once at their root instead of threading a callback through every intermediate
 * row composable down to [withClickableLinks]'s two call sites. */
val LocalDccFileDownloadHandler = staticCompositionLocalOf<(path: String, filename: String?) -> Unit> { { _, _ -> } }

/** Opens a referenced channel in the current network; null outside a chat screen. */
val LocalIrcChannelLinkHandler = staticCompositionLocalOf<((String) -> Unit)?> { null }

/** The message with every mIRC control code consumed and none of its formatting kept —
 * for contexts that need plain text (a reply-quote preview), not a styled [AnnotatedString]. */
fun stripMircCodes(text: String): String = MircParser.parse(text).joinToString("") { it.text }

/** Server-owned `#2029` pref (see `DisplayPrefsDto.stripFormatting`): when true every
 * surface below renders mIRC-coded text as plain text instead. A CompositionLocal —
 * provided once in AppRoot from the local mirror, so chat transcript, topics, home
 * previews and directory all follow it without threading a boolean through every row
 * composable (same shape as [LocalDccFileDownloadHandler]). */
val LocalStripMircFormatting = staticCompositionLocalOf { false }

fun mircAnnotatedString(text: String, lightTheme: Boolean = false, stripFormatting: Boolean = false): AnnotatedString = buildAnnotatedString {
    val spans = if (stripFormatting) listOf(MircSpan(text = stripMircCodes(text))) else MircParser.parse(text)
    spans.forEach { span ->
        // An explicitly dark background (e.g. white-on-black highlights) keeps the
        // dark-theme foreground: remapping it for a light surface would print dark
        // on black. Only spans on a light/absent background get the light palette.
        val background = mircColorOrNull(span.background, lightTheme = false)
        val backgroundIsDark = background?.luminance()?.let { it < 0.35f } == true
        withStyle(
            SpanStyle(
                color = mircColorOrNull(span.foreground, lightTheme && !backgroundIsDark) ?: Color.Unspecified,
                background = background ?: Color.Unspecified,
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

/** Drops generic-URL ranges overlapped by a DCC link (inclusive ranges on both
 * sides) — a DCC delivery URL is also a plain https URL, and without this the tap
 * would hit the platform browser instead of the app's save flow. Pure for testability. */
internal fun withoutDccOverlaps(urls: List<IntRange>, dccRanges: List<IntRange>): List<IntRange> =
    urls.filter { url -> dccRanges.none { dcc -> url.first <= dcc.last && dcc.first <= url.last } }

/** Layers clickable [LinkAnnotation.Url] ranges on top of an already-built
 * [AnnotatedString] (which may already carry mIRC color/bold/etc. spans) — Text renders
 * a link's default styling and opens it via the platform URI handler automatically, no
 * manual tap handling needed. Channel references are linked only when a chat supplies
 * onChannelClick, so previews outside a chat keep their ordinary text behavior. */
fun withClickableLinks(
    annotated: AnnotatedString,
    linkStyles: TextLinkStyles = darkLinkStyles,
    onDccFileClick: (path: String, filename: String?) -> Unit = { _, _ -> },
    onChannelClick: ((channelName: String) -> Unit)? = null,
    channelLinkVisibleEndExclusive: Int = Int.MAX_VALUE,
): AnnotatedString {
    val dccLinks = DccFileLinkDetector.find(annotated.text)
    // A DCC delivery URL is also a plain https URL — DCC wins over the generic link
    // on any overlap so the tap opens the app's own save flow, not the browser.
    val ranges = withoutDccOverlaps(UrlDetector.find(annotated.text), dccLinks.map { it.pathRange })
    val channelClick = onChannelClick
    val channelLinks = if (channelClick == null) {
        emptyList()
    } else {
        ChannelReferenceDetector.find(annotated.text, channelLinkVisibleEndExclusive).filterNot { reference ->
            (ranges + dccLinks.map { it.pathRange }).any { occupied ->
                reference.range.first <= occupied.last && occupied.first <= reference.range.last
            }
        }
    }
    if (ranges.isEmpty() && dccLinks.isEmpty() && channelLinks.isEmpty()) return annotated
    return AnnotatedString.Builder(annotated).apply {
        ranges.forEach { range ->
            addLink(
                LinkAnnotation.Url(annotated.text.substring(range.first, range.last + 1), linkStyles),
                range.first,
                range.last + 1,
            )
        }
        dccLinks.forEach { link ->
            addLink(
                LinkAnnotation.Clickable("dcc_file", linkStyles) { onDccFileClick(link.path, link.filename) },
                link.pathRange.first,
                link.pathRange.last + 1,
            )
        }
        channelLinks.forEach { reference ->
            addLink(
                LinkAnnotation.Clickable("irc_channel", linkStyles) { channelClick?.invoke(reference.channelName) },
                reference.range.first,
                reference.range.last + 1,
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
    enableLinks: Boolean = true,
    stripFormatting: Boolean = LocalStripMircFormatting.current,
) {
    val lightTheme = isLightTheme()
    val dccFileHandler = LocalDccFileDownloadHandler.current
    val channelLinkHandler = LocalIrcChannelLinkHandler.current
    val visibleChannelLinkEnd = remember(text, maxLines, overflow) {
        mutableStateOf(if (maxLines < Int.MAX_VALUE) 0 else text.length)
    }
    val annotated = remember(text, enableLinks, lightTheme, dccFileHandler, channelLinkHandler, visibleChannelLinkEnd.value, stripFormatting) {
        val parsed = mircAnnotatedString(text, lightTheme, stripFormatting)
        if (enableLinks) {
            withClickableLinks(
                parsed,
                linkStylesFor(lightTheme),
                dccFileHandler,
                channelLinkHandler,
                visibleChannelLinkEnd.value,
            )
        } else {
            parsed
        }
    }
    Text(
        text = annotated,
        modifier = modifier,
        style = style,
        color = color,
        maxLines = maxLines,
        overflow = overflow,
        onTextLayout = { layout ->
            if (maxLines < Int.MAX_VALUE && layout.lineCount > 0) {
                val lastVisibleLine = (layout.lineCount - 1).coerceAtMost(maxLines - 1)
                val visibleEnd = layout.getLineEnd(lastVisibleLine, visibleEnd = true)
                if (visibleChannelLinkEnd.value != visibleEnd) {
                    visibleChannelLinkEnd.value = visibleEnd
                }
            }
        },
    )
}
