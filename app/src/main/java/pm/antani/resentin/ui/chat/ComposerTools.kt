package pm.antani.resentin.ui.chat

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import pm.antani.resentin.R
import pm.antani.resentin.mirc.MircParser
import pm.antani.resentin.ui.common.mircAnnotatedString
import pm.antani.resentin.ui.common.mircPaletteColor
import pm.antani.resentin.ui.common.stripMircCodes


private const val IRC_BOLD = 2
private const val IRC_ITALIC = 29
private const val IRC_UNDERLINE = 31
private const val IRC_COLOR = 3
private const val IRC_RESET = 15
internal data class ComposerFormatting(
    val bold: Boolean,
    val italic: Boolean,
    val underline: Boolean,
    val foreground: Int?,
    val background: Int?,
)

internal fun composerFormattingAtCursor(value: TextFieldValue): ComposerFormatting {
    val cursor = value.selection.min.coerceIn(0, value.text.length)
    val span = MircParser.parse(value.text.substring(0, cursor) + "x").lastOrNull()
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
    onFocusComposer: () -> Unit,
) {
    var colorSheetOpen by remember { mutableStateOf(false) }
    var colorTargetBackground by remember { mutableStateOf(false) }
    var selectedForeground by remember { mutableStateOf<Int?>(null) }
    var selectedBackground by remember { mutableStateOf<Int?>(null) }
    val formatting = composerFormattingAtCursor(value)
    val hapticFeedback = LocalHapticFeedback.current

    fun commitToolValue(nextValue: TextFieldValue) {
        hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        onValueChange(nextValue)
        onFocusComposer()
    }

    fun openColorSheet() {
        selectedForeground = formatting.foreground
        selectedBackground = formatting.background
        colorTargetBackground = false
        colorSheetOpen = true
    }

    LaunchedEffect(visible) {
        if (!visible) colorSheetOpen = false
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
                ComposerToolSurface(
                    label = stringResource(R.string.cd_attach_file),
                    onClick = onAttachFile,
                    enabled = !isUploading,
                ) {
                    if (isUploading) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    } else {
                        Icon(
                            Icons.Outlined.AttachFile,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                ComposerFormatButton(
                    icon = Icons.Filled.FormatBold,
                    label = stringResource(R.string.composer_tool_bold),
                    checked = formatting.bold,
                    onClick = { commitToolValue(toggleIrcStyle(value, IRC_BOLD)) },
                )
                ComposerFormatButton(
                    icon = Icons.Filled.FormatItalic,
                    label = stringResource(R.string.composer_tool_italic),
                    checked = formatting.italic,
                    onClick = { commitToolValue(toggleIrcStyle(value, IRC_ITALIC)) },
                )
                ComposerFormatButton(
                    icon = Icons.Filled.FormatUnderlined,
                    label = stringResource(R.string.composer_tool_underline),
                    checked = formatting.underline,
                    onClick = { commitToolValue(toggleIrcStyle(value, IRC_UNDERLINE)) },
                )
                ComposerFormatButton(
                    icon = Icons.Filled.FormatColorText,
                    label = stringResource(R.string.composer_tool_color),
                    checked = colorSheetOpen || formatting.foreground != null || formatting.background != null,
                    onClick = {
                        hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        openColorSheet()
                    },
                    foregroundCode = formatting.foreground,
                    backgroundCode = formatting.background,
                )
                ComposerToolButton(
                    icon = Icons.Filled.FormatClear,
                    label = stringResource(R.string.composer_tool_clear),
                    onClick = { commitToolValue(clearIrcFormatting(value)) },
                )
            }
        }
    }

    if (colorSheetOpen) {
        ComposerColorSheet(
            foreground = selectedForeground,
            background = selectedBackground,
            editingBackground = colorTargetBackground,
            onEditingBackgroundChange = {
                hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                colorTargetBackground = it
            },
            onForegroundChange = {
                hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                selectedForeground = it
            },
            onBackgroundChange = {
                hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                selectedBackground = it
            },
            onRemove = {
                colorSheetOpen = false
                commitToolValue(clearIrcColors(value))
            },
            onCancel = { colorSheetOpen = false },
            onApply = {
                colorSheetOpen = false
                commitToolValue(applyIrcColors(value, selectedForeground, selectedBackground))
            },
        )
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
            Text(stringResource(R.string.composer_color_title), style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            Button(onClick = onApply) { Text(stringResource(R.string.composer_color_apply)) }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ComposerColorTargetButton(
                label = stringResource(R.string.composer_color_target_text),
                selected = !editingBackground,
                colorCode = foreground,
                onClick = { onEditingBackgroundChange(false) },
            )
            ComposerColorTargetButton(
                label = stringResource(R.string.composer_color_target_background),
                selected = editingBackground,
                colorCode = background,
                onClick = { onEditingBackgroundChange(true) },
            )
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
            TextButton(onClick = onRemove) { Text(stringResource(R.string.composer_color_remove)) }
            TextButton(onClick = onCancel) { Text(stringResource(R.string.composer_color_cancel)) }
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
private fun ComposerToolSurface(
    label: String,
    active: Boolean = false,
    enabled: Boolean = true,
    onClick: () -> Unit,
    content: @Composable () -> Unit,
) {
    val shape = RoundedCornerShape(16.dp)
    val containerColor by animateColorAsState(
        targetValue = when {
            !enabled -> MaterialTheme.colorScheme.surfaceContainer
            active -> MaterialTheme.colorScheme.primaryContainer
            else -> MaterialTheme.colorScheme.surfaceContainerHigh
        },
        label = "composer_tool_container",
    )
    val elevation by animateDpAsState(
        targetValue = if (active && enabled) 2.dp else 0.dp,
        label = "composer_tool_elevation",
    )

    Surface(
        modifier = Modifier
            .size(48.dp)
            .semantics(mergeDescendants = true) {
                contentDescription = label
                selected = active
            },
        shape = shape,
        color = containerColor,
        tonalElevation = elevation,
        border = if (active && enabled) {
            BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.55f))
        } else {
            null
        },
    ) {
        IconButton(
            onClick = onClick,
            enabled = enabled,
            modifier = Modifier.fillMaxSize(),
        ) {
            content()
        }
    }
}

@Composable
private fun ComposerToolButton(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    active: Boolean = false,
) {
    ComposerToolSurface(
        label = label,
        active = active,
        onClick = onClick,
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = if (active) MaterialTheme.colorScheme.onPrimaryContainer
            else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ComposerFormatButton(
    icon: ImageVector,
    label: String,
    checked: Boolean,
    onClick: () -> Unit,
    foregroundCode: Int? = null,
    backgroundCode: Int? = null,
) {
    val shape = RoundedCornerShape(16.dp)
    val containerColor by animateColorAsState(
        targetValue = if (checked) MaterialTheme.colorScheme.primaryContainer
        else MaterialTheme.colorScheme.surfaceContainerHigh,
        label = "composer_format_container",
    )
    val iconColor by animateColorAsState(
        targetValue = if (checked) MaterialTheme.colorScheme.onPrimaryContainer
        else MaterialTheme.colorScheme.onSurfaceVariant,
        label = "composer_format_icon",
    )
    val borderWidth by animateDpAsState(
        targetValue = if (checked) 1.dp else 0.dp,
        label = "composer_format_border",
    )
    val elevation by animateDpAsState(
        targetValue = if (checked) 2.dp else 0.dp,
        label = "composer_format_elevation",
    )

    Surface(
        modifier = Modifier
            .size(48.dp)
            .semantics(mergeDescendants = true) {
                contentDescription = label
                selected = checked
            },
        shape = shape,
        color = containerColor,
        tonalElevation = elevation,
        border = BorderStroke(
            width = borderWidth,
            color = MaterialTheme.colorScheme.primary.copy(alpha = if (checked) 0.7f else 0f),
        ),
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            IconButton(onClick = onClick, modifier = Modifier.fillMaxSize()) {
                Icon(icon, contentDescription = null, tint = iconColor)
            }
            AnimatedVisibility(
                visible = checked,
                enter = fadeIn() + scaleIn(),
                exit = fadeOut() + scaleOut(),
                modifier = Modifier.align(Alignment.BottomCenter),
            ) {
                Box(
                    modifier = Modifier
                        .padding(bottom = 4.dp)
                        .size(4.dp)
                        .background(MaterialTheme.colorScheme.primary, CircleShape),
                )
            }
            val colorCode = foregroundCode ?: backgroundCode
            if (colorCode != null) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(end = 6.dp, bottom = 6.dp)
                        .size(8.dp)
                        .background(mircPaletteColor(colorCode), CircleShape)
                        .border(
                            width = 1.dp,
                            color = MaterialTheme.colorScheme.surfaceContainerHighest,
                            shape = CircleShape,
                        ),
                )
            }
        }
    }
}
@Composable
private fun RowScope.ComposerColorTargetButton(
    label: String,
    selected: Boolean,
    colorCode: Int?,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(12.dp)
    TextButton(
        onClick = onClick,
        modifier = Modifier
            .weight(1f)
            .height(48.dp)
            .background(
                color = if (selected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent,
                shape = shape,
            )
            .border(
                width = if (selected) 1.dp else 0.dp,
                color = if (selected) MaterialTheme.colorScheme.secondary else Color.Transparent,
                shape = shape,
            )
            .semantics(mergeDescendants = true) {
                contentDescription = label
                this.selected = selected
            },
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = label,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                color = if (selected) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (colorCode != null) {
                Box(
                    modifier = Modifier
                        .size(12.dp)
                        .background(mircPaletteColor(colorCode), CircleShape)
                        .border(
                            width = 1.dp,
                            color = if (selected) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.outline,
                            shape = CircleShape,
                        ),
                )
            }
        }
    }
}
