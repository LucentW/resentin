package pm.antani.resentin.ui.chat

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FormatBold
import androidx.compose.material.icons.filled.FormatClear
import androidx.compose.material.icons.filled.FormatColorText
import androidx.compose.material.icons.filled.FormatItalic
import androidx.compose.material.icons.filled.FormatUnderlined
import androidx.compose.material.icons.outlined.AttachFile
import androidx.compose.material.icons.outlined.Mood
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import pm.antani.resentin.R
import pm.antani.resentin.mirc.MircParser
import pm.antani.resentin.ui.common.mircAnnotatedString
import pm.antani.resentin.ui.common.mircPaletteColor
import pm.antani.resentin.ui.common.stripMircCodes

private val ComposerEmojis = listOf(
    "😀", "😃", "😄", "😁", "😆", "😅", "😂", "🤣", "😊", "😇", "🙂", "🙃", "😉", "😌", "😍", "🥰",
    "😘", "😗", "😙", "😚", "😋", "😛", "😝", "😜", "🤪", "🤨", "🧐", "🤓", "😎", "🤩", "🥳", "😏",
    "😒", "😞", "😔", "😟", "😕", "🙁", "☹️", "😣", "😖", "😫", "😩", "🥺", "😢", "😭", "😤", "😠",
    "😡", "🤬", "🤯", "😳", "🥵", "🥶", "😱", "😨", "😰", "😥", "😓", "🤗", "🤔", "🫡", "🤭", "🤫",
    "🤥", "😶", "😐", "😑", "😬", "🙄", "😯", "😦", "😧", "😮", "😲", "🥱", "😴", "🤤", "😪", "😵",
    "🤐", "🥴", "🤢", "🤮", "🤧", "😷", "🤒", "🤕", "👍", "👎", "👏", "🙌", "🙏", "💪", "🤝", "❤️",
    "💔", "🔥", "✨", "🎉", "🎊", "🚀", "💡", "✅", "❌", "⚠️", "⭐", "💯", "🍀", "🎵", "☕", "🍕",
)

private const val IRC_BOLD = 2
private const val IRC_ITALIC = 29
private const val IRC_UNDERLINE = 31
private const val IRC_COLOR = 3
private const val IRC_RESET = 15
private data class ComposerFormatting(
    val bold: Boolean,
    val italic: Boolean,
    val underline: Boolean,
    val foreground: Int?,
    val background: Int?,
)

private fun composerFormattingAtCursor(value: TextFieldValue): ComposerFormatting {
    val cursor = value.selection.min.coerceIn(0, value.text.length)
    val span = MircParser.parse(value.text.substring(0, cursor) + "x").firstOrNull()
    return ComposerFormatting(
        bold = span?.bold == true,
        italic = span?.italic == true,
        underline = span?.underline == true,
        foreground = span?.foreground,
        background = span?.background,
    )
}

internal fun toggleIrcStyle(value: TextFieldValue, codePoint: Int): TextFieldValue {
    val code = Char(codePoint)
    val selection = value.selection
    if (selection.collapsed) {
        val text = value.text.substring(0, selection.start) + code + value.text.substring(selection.start)
        return TextFieldValue(text, TextRange(selection.start + 1))
    }

    val start = selection.min
    val end = selection.max
    val selected = value.text.substring(start, end)
    val text = value.text.substring(0, start) + code + selected + code + value.text.substring(end)
    return TextFieldValue(text, TextRange(start + 1, end + 1))
}

internal fun insertComposerText(value: TextFieldValue, inserted: String): TextFieldValue {
    val start = value.selection.min
    val end = value.selection.max
    val text = value.text.substring(0, start) + inserted + value.text.substring(end)
    return TextFieldValue(text, TextRange(start + inserted.length))
}

internal fun applyIrcColor(value: TextFieldValue, color: Int): TextFieldValue =
    applyIrcColors(value, foreground = color, background = null)

internal fun applyIrcColors(value: TextFieldValue, foreground: Int?, background: Int?): TextFieldValue {
    val prefix = buildString {
        append(Char(IRC_COLOR))
        foreground?.let { append(it.toString().padStart(2, '0')) }
        background?.let {
            append(',')
            append(it.toString().padStart(2, '0'))
        }
    }
    val selection = value.selection
    if (selection.collapsed) return insertComposerText(value, prefix)

    val start = selection.min
    val end = selection.max
    val selected = value.text.substring(start, end)
    val replacement = prefix + selected + Char(IRC_RESET)
    val text = value.text.substring(0, start) + replacement + value.text.substring(end)
    return TextFieldValue(text, TextRange(start + replacement.length))
}

internal fun clearIrcColors(value: TextFieldValue): TextFieldValue {
    if (value.selection.collapsed) return insertComposerText(value, Char(IRC_COLOR).toString())
    val start = value.selection.min
    val end = value.selection.max
    val replacement = stripIrcColors(value.text.substring(start, end))
    val text = value.text.substring(0, start) + replacement + value.text.substring(end)
    return TextFieldValue(text, TextRange(start + replacement.length))
}

private fun stripIrcColors(text: String): String {
    val result = StringBuilder(text.length)
    var index = 0
    while (index < text.length) {
        if (text[index] != Char(IRC_COLOR)) {
            result.append(text[index++])
            continue
        }
        index++
        repeat(2) {
            if (index < text.length && text[index].isDigit()) index++
        }
        if (index < text.length && text[index] == ',') {
            index++
            repeat(2) {
                if (index < text.length && text[index].isDigit()) index++
            }
        }
    }
    return result.toString()
}

internal fun clearIrcFormatting(value: TextFieldValue): TextFieldValue {
    if (value.selection.collapsed) return insertComposerText(value, Char(IRC_RESET).toString())
    val start = value.selection.min
    val end = value.selection.max
    val selected = value.text.substring(start, end)
    val replacement = stripMircCodes(selected)
    val text = value.text.substring(0, start) + replacement + value.text.substring(end)
    return TextFieldValue(text, TextRange(start + replacement.length))
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ComposerTools(
    visible: Boolean,
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    isUploading: Boolean,
    onAttachFile: () -> Unit,
) {
    var emojiSheetOpen by remember { mutableStateOf(false) }
    var colorSheetOpen by remember { mutableStateOf(false) }
    var colorTargetBackground by remember { mutableStateOf(false) }
    var selectedForeground by remember { mutableStateOf<Int?>(null) }
    var selectedBackground by remember { mutableStateOf<Int?>(null) }
    val formatting = composerFormattingAtCursor(value)

    fun openColorSheet() {
        selectedForeground = formatting.foreground
        selectedBackground = formatting.background
        colorTargetBackground = false
        colorSheetOpen = true
    }

    AnimatedVisibility(
        visible = visible,
        enter = expandVertically() + fadeIn(),
        exit = shrinkVertically() + fadeOut(),
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            tonalElevation = 1.dp,
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                IconButton(
                    onClick = onAttachFile,
                    enabled = !isUploading,
                    modifier = Modifier
                        .size(40.dp)
                        .background(MaterialTheme.colorScheme.surfaceContainer, CircleShape),
                ) {
                    if (isUploading) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    } else {
                        Icon(
                            Icons.Outlined.AttachFile,
                            contentDescription = stringResource(R.string.cd_attach_file),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                ComposerToolButton(
                    icon = Icons.Outlined.Mood,
                    label = stringResource(R.string.composer_tool_emoji),
                    onClick = { emojiSheetOpen = true },
                )
                ComposerToggleButton(
                    icon = Icons.Filled.FormatBold,
                    label = stringResource(R.string.composer_tool_bold),
                    checked = formatting.bold,
                    onClick = { onValueChange(toggleIrcStyle(value, IRC_BOLD)) },
                )
                ComposerToggleButton(
                    icon = Icons.Filled.FormatItalic,
                    label = stringResource(R.string.composer_tool_italic),
                    checked = formatting.italic,
                    onClick = { onValueChange(toggleIrcStyle(value, IRC_ITALIC)) },
                )
                ComposerToggleButton(
                    icon = Icons.Filled.FormatUnderlined,
                    label = stringResource(R.string.composer_tool_underline),
                    checked = formatting.underline,
                    onClick = { onValueChange(toggleIrcStyle(value, IRC_UNDERLINE)) },
                )
                ComposerToggleButton(
                    icon = Icons.Filled.FormatColorText,
                    label = stringResource(R.string.composer_tool_color),
                    checked = formatting.foreground != null || formatting.background != null,
                    onClick = ::openColorSheet,
                )
                ComposerToolButton(
                    icon = Icons.Filled.FormatClear,
                    label = stringResource(R.string.composer_tool_clear),
                    onClick = { onValueChange(clearIrcFormatting(value)) },
                )
            }
        }
    }

    if (emojiSheetOpen) {
        ComposerEmojiSheet(
            onPick = { emoji ->
                emojiSheetOpen = false
                onValueChange(insertComposerText(value, emoji))
            },
            onDismiss = { emojiSheetOpen = false },
        )
    }
    if (colorSheetOpen) {
        ComposerColorSheet(
            foreground = selectedForeground,
            background = selectedBackground,
            editingBackground = colorTargetBackground,
            onEditingBackgroundChange = { colorTargetBackground = it },
            onForegroundChange = { selectedForeground = it },
            onBackgroundChange = { selectedBackground = it },
            onRemove = {
                colorSheetOpen = false
                onValueChange(clearIrcColors(value))
            },
            onCancel = { colorSheetOpen = false },
            onApply = {
                colorSheetOpen = false
                onValueChange(applyIrcColors(value, selectedForeground, selectedBackground))
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ComposerEmojiSheet(onPick: (String) -> Unit, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Text(
            text = "Emoji",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
        )
        LazyVerticalGrid(
            columns = GridCells.Fixed(8),
            modifier = Modifier
                .fillMaxWidth()
                .height(420.dp)
                .padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            items(ComposerEmojis) { emoji ->
                Text(
                    text = emoji,
                    fontSize = 27.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .size(48.dp)
                        .clickable { onPick(emoji) },
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ComposerColorSheet(
    foreground: Int?,
    background: Int?,
    editingBackground: Boolean,
    onEditingBackgroundChange: (Boolean) -> Unit,
    onForegroundChange: (Int) -> Unit,
    onBackgroundChange: (Int) -> Unit,
    onRemove: () -> Unit,
    onCancel: () -> Unit,
    onApply: () -> Unit,
) {
    val previewCode = buildString {
        append(Char(IRC_COLOR))
        foreground?.let { append(it.toString().padStart(2, '0')) }
        background?.let {
            append(',')
            append(it.toString().padStart(2, '0'))
        }
    }

    ModalBottomSheet(onDismissRequest = onCancel) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 24.dp, end = 16.dp, top = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Colore IRC", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            Button(onClick = onApply) { Text("Applica") }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            TextButton(onClick = { onEditingBackgroundChange(false) }) {
                Text("Testo", fontWeight = if (!editingBackground) FontWeight.Bold else null)
            }
            TextButton(onClick = { onEditingBackgroundChange(true) }) {
                Text("Sfondo", fontWeight = if (editingBackground) FontWeight.Bold else null)
            }
        }
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHighest,
            shape = MaterialTheme.shapes.medium,
        ) {
            Text(
                text = mircAnnotatedString(previewCode + "Anteprima"),
                modifier = Modifier.padding(16.dp),
                style = MaterialTheme.typography.bodyLarge,
            )
        }
        LazyVerticalGrid(
            columns = GridCells.Fixed(8),
            modifier = Modifier
                .fillMaxWidth()
                .height(360.dp)
                .padding(horizontal = 20.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items((0..98).toList()) { color ->
                ComposerColorSwatch(
                    color = color,
                    selected = if (editingBackground) background == color else foreground == color,
                    onClick = {
                        if (editingBackground) onBackgroundChange(color) else onForegroundChange(color)
                    },
                )
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            TextButton(onClick = onRemove) { Text("Rimuovi colori") }
            TextButton(onClick = onCancel) { Text("Annulla") }
        }
    }
}

@Composable
private fun ComposerColorSwatch(color: Int, selected: Boolean, onClick: () -> Unit) {
    val swatch = mircPaletteColor(color)
    val labelColor = if (swatch.luminance() > 0.55f) Color.Black else Color.White
    Box(
        modifier = Modifier
            .size(42.dp)
            .clip(CircleShape)
            .background(swatch)
            .border(
                width = if (selected) 3.dp else 1.dp,
                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                shape = CircleShape,
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = color.toString().padStart(2, '0'),
            color = labelColor,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun ComposerToolButton(icon: ImageVector, label: String, onClick: () -> Unit) {
    IconButton(onClick = onClick, modifier = Modifier.size(48.dp)) {
        Icon(icon, contentDescription = label)
    }
}

@Composable
private fun ComposerToggleButton(
    icon: ImageVector,
    label: String,
    checked: Boolean,
    onClick: () -> Unit,
) {
    IconToggleButton(checked = checked, onCheckedChange = { onClick() }, modifier = Modifier.size(48.dp)) {
        Icon(icon, contentDescription = label)
    }
}
