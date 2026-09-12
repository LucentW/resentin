package pm.antani.resentin.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import pm.antani.resentin.R

/** Bottone tondo stile Resentin per le top bar: cerchio 40dp in
 * surfaceContainerHigh con bordo outlineVariant, glifo outlined 20dp. */
@Composable
fun ResentinHeaderAction(
    onClick: () -> Unit,
    icon: ImageVector,
    contentDescription: String,
    enabled: Boolean = true,
    loading: Boolean = false,
    badgeText: String? = null,
    iconTint: Color? = null,
    stateDescription: String? = null,
) {
    val accessibleStateDescription = stateDescription
        ?: if (loading) stringResource(R.string.cd_loading) else null
    val circle = @Composable {
        IconButton(
            onClick = onClick,
            enabled = enabled && !loading,
            modifier = Modifier
                .size(40.dp)
                .semantics(mergeDescendants = true) {
                    this.contentDescription = contentDescription
                    accessibleStateDescription?.let { this.stateDescription = it }
                }
                .background(
                    MaterialTheme.colorScheme.surfaceContainerHigh,
                    CircleShape,
                )
                .border(
                    1.dp,
                    MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                    CircleShape,
                ),
        ) {
            if (loading) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
            } else {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconTint ?: MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
    if (badgeText != null) {
        BadgedBox(
            badge = { Badge { Text(badgeText) } },
            modifier = Modifier.padding(horizontal = 6.dp),
        ) {
            circle()
        }
    } else {
        Box(modifier = Modifier.padding(horizontal = 6.dp)) {
            circle()
        }
    }
}
