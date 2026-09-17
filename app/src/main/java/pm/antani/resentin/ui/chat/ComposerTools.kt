package pm.antani.resentin.ui.chat

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import pm.antani.resentin.R
import pm.antani.resentin.ui.common.stripMircCodes

private val ComposerEmojis = listOf("😀", "😂", "🙂", "😉", "😍", "👍", "❤️", "🎉", "🔥", "🚀")

private const val IRC_BOLD = 2
private const val IRC_ITALIC = 29
private const val IRC_UNDERLINE = 31
private const val IRC_COLOR = 3

internal fun toggleIrcStyle(value: TextFieldValue, codePoint: Int): TextFieldValue {
    val code = Char(codePoint)
    val selection = value.selection
    if (selection.collapsed) {
        val text = value.text.substring(0, selection.start) + code + value.text.substring(selection.start)
        val cursor = selection.start + 1
        return TextFieldValue(text, TextRange(cursor))
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
    val cursor = start + inserted.length
    return TextFieldValue(text, TextRange(cursor))
}

internal fun applyIrcColor(value: TextFieldValue, color: Int): TextFieldValue {
    val prefix = Char(IRC_COLOR) + color.toString().padStart(2, '0')
    val selection = value.selection
    if (selection.collapsed) return insertComposerText(value, prefix)

    val start = selection.min
    val end = selection.max
    val selected = value.text.substring(start, end)
    val replacement = prefix + selected + Char(15)
    val text = value.text.substring(0, start) + replacement + value.text.substring(end)
    return TextFieldValue(text, TextRange(start + replacement.length))
}

internal fun clearIrcFormatting(value: TextFieldValue): TextFieldValue {
    val start = value.selection.min
    val end = value.selection.max
    val selected = value.text.substring(start, end)
    val replacement = stripMircCodes(selected)
    val text = value.text.substring(0, start) + replacement + value.text.substring(end)
    return TextFieldValue(text, TextRange(start + replacement.length))
}

@Composable
internal fun ComposerTools(
    visible: Boolean,
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    isUploading: Boolean,
    onAttachFile: () -> Unit,
) {
    var emojiMenuOpen by remember { mutableStateOf(false) }
    var colorMenuOpen by remember { mutableStateOf(false) }

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
                        .background(
                            MaterialTheme.colorScheme.surfaceContainer,
                            CircleShape,
                        ),
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
                Box {
                    ComposerToolButton(
                        icon = Icons.Outlined.Mood,
                        label = stringResource(R.string.composer_tool_emoji),
                        onClick = { emojiMenuOpen = true },
                    )
                    DropdownMenu(
                        expanded = emojiMenuOpen,
                        onDismissRequest = { emojiMenuOpen = false },
                    ) {
                        ComposerEmojis.forEach { emoji ->
                            DropdownMenuItem(
                                text = { Text(emoji) },
                                onClick = {
                                    emojiMenuOpen = false
                                    onValueChange(insertComposerText(value, emoji))
                                },
                            )
                        }
                    }
                }

                ComposerToolButton(
                    icon = Icons.Filled.FormatBold,
                    label = stringResource(R.string.composer_tool_bold),
                    onClick = { onValueChange(toggleIrcStyle(value, IRC_BOLD)) },
                )
                ComposerToolButton(
                    icon = Icons.Filled.FormatItalic,
                    label = stringResource(R.string.composer_tool_italic),
                    onClick = { onValueChange(toggleIrcStyle(value, IRC_ITALIC)) },
                )
                ComposerToolButton(
                    icon = Icons.Filled.FormatUnderlined,
                    label = stringResource(R.string.composer_tool_underline),
                    onClick = { onValueChange(toggleIrcStyle(value, IRC_UNDERLINE)) },
                )

                Box {
                    ComposerToolButton(
                        icon = Icons.Filled.FormatColorText,
                        label = stringResource(R.string.composer_tool_color),
                        onClick = { colorMenuOpen = true },
                    )
                    DropdownMenu(
                        expanded = colorMenuOpen,
                        onDismissRequest = { colorMenuOpen = false },
                    ) {
                        (0..15).forEach { color ->
                            DropdownMenuItem(
                                text = { Text("${color.toString().padStart(2, '0')}  ${mircColorName(color)}") },
                                onClick = {
                                    colorMenuOpen = false
                                    onValueChange(applyIrcColor(value, color))
                                },
                            )
                        }
                    }
                }
                ComposerToolButton(
                    icon = Icons.Filled.FormatClear,
                    label = stringResource(R.string.composer_tool_clear),
                    onClick = { onValueChange(clearIrcFormatting(value)) },
                )
            }
        }
    }
}

@Composable
private fun ComposerToolButton(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
) {
    IconButton(
        onClick = onClick,
        modifier = Modifier.size(48.dp),
    ) {
        Icon(icon, contentDescription = label)
    }
}

private fun mircColorName(color: Int): String = when (color) {
    0 -> "White"
    1 -> "Black"
    2 -> "Blue"
    3 -> "Green"
    4 -> "Red"
    5 -> "Brown"
    6 -> "Purple"
    7 -> "Orange"
    8 -> "Yellow"
    9 -> "Light green"
    10 -> "Cyan"
    11 -> "Light cyan"
    12 -> "Light blue"
    13 -> "Pink"
    14 -> "Grey"
    else -> "Light grey"
}
