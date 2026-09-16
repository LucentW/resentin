package pm.antani.resentin.ui.common

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.window.PopupProperties
import pm.antani.resentin.ui.theme.ResentinSpacing

/** One option of a [ResentinDropdown]: the selected row shows [label] (in
 * [fontFamily] when set), and picking it calls back with [value]. */
data class ResentinDropdownOption<T>(
    val value: T,
    val label: String,
    val fontFamily: FontFamily? = null,
)

/** App-wide single-select dropdown — full-width button showing the current
 * choice, menu with a check on the active row. Use for single-choice settings
 * and option pickers; toggles, filters and tab-like switchers keep chips. */
@Composable
fun <T> ResentinDropdown(
    selected: T,
    options: List<ResentinDropdownOption<T>>,
    onSelected: (T) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedOption = options.firstOrNull { it.value == selected } ?: options.first()

    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        OutlinedButton(
            onClick = { expanded = true },
            enabled = enabled,
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.medium,
            contentPadding = PaddingValues(horizontal = ResentinSpacing.large, vertical = ResentinSpacing.medium),
        ) {
            Text(
                text = selectedOption.label,
                modifier = Modifier.weight(1f),
                fontFamily = selectedOption.fontFamily,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Icon(
                imageVector = Icons.Outlined.KeyboardArrowDown,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.width(maxWidth),
            properties = PopupProperties(focusable = true),
        ) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option.label, fontFamily = option.fontFamily) },
                    onClick = {
                        onSelected(option.value)
                        expanded = false
                    },
                    trailingIcon = if (option.value == selected) {
                        {
                            Icon(
                                imageVector = Icons.Outlined.Check,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        }
                    } else {
                        null
                    },
                )
            }
        }
    }
}
