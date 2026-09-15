package pm.antani.resentin.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import pm.antani.resentin.R
import pm.antani.resentin.ui.theme.ResentinSpacing

/** Small unread-count pill, same language as Home rows: grey for plain messages,
 * primary + `@` prefix for mentions, capped at `99+`. Shared by the archive rows
 * (#2099) and the archive launcher so the two never drift apart. */
@Composable
fun UnreadCountBadge(count: Int, isMention: Boolean = false, modifier: Modifier = Modifier) {
    val accessibilityLabel = pluralStringResource(
        if (isMention) R.plurals.home_unread_mentions_accessibility else R.plurals.home_unread_messages_accessibility,
        count,
        count,
    )
    val badgeText = (if (isMention) "@" else "") + (if (count > 99) "99+" else count.toString())
    val backgroundColor = if (isMention) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.surfaceVariant
    }
    val foregroundColor = if (isMention) {
        MaterialTheme.colorScheme.onPrimary
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    Box(
        modifier = modifier
            .semantics(mergeDescendants = true) { contentDescription = accessibilityLabel }
            .background(color = backgroundColor, shape = MaterialTheme.shapes.extraSmall)
            .padding(horizontal = ResentinSpacing.small, vertical = ResentinSpacing.xSmall),
    ) {
        Text(
            text = badgeText,
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
            color = foregroundColor,
        )
    }
}
