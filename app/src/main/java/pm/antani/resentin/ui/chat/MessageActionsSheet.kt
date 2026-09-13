package pm.antani.resentin.ui.chat

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Reply
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.FormatQuote
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.SelectAll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import pm.antani.resentin.R
import pm.antani.resentin.ui.common.stripMircCodes
import pm.antani.resentin.ui.theme.ResentinSpacing

// Long-press menu on a chat message — cicchetto MessageContextMenu parity:
// Copy / Reply / !addquote / Select… (+ user card, which on Resentin used to
// own the long-press outright — the nick is too small a tap target to move it
// there). No forward: IRC has no such verb and faking one would mislead.
data class MessageMenuTarget(
    val messageId: Long,
    val sender: String,
    val text: String,
    val isChat: Boolean,
    val isAction: Boolean = false,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MessageActionsSheet(
    target: MessageMenuTarget,
    onDismiss: () -> Unit,
    onReply: () -> Unit,
    onAddQuote: () -> Unit,
    onSelect: () -> Unit,
    onUserCard: () -> Unit,
) {
    val clipboardManager = LocalClipboardManager.current
    val quotable = target.isChat && target.text.isNotBlank()
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(),
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 16.dp),
        ) {
            Text(
                text = target.sender,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 4.dp),
            )
            Spacer(Modifier.height(12.dp))
            MessageSheetActionRow(
                icon = Icons.Outlined.ContentCopy,
                text = stringResource(R.string.whois_copy_message),
                onClick = {
                    clipboardManager.setText(AnnotatedString(stripMircCodes(target.text)))
                    onDismiss()
                },
            )
            if (quotable) {
                MessageSheetActionRow(
                    icon = Icons.AutoMirrored.Outlined.Reply,
                    text = stringResource(R.string.cd_reply),
                    onClick = onReply,
                )
                MessageSheetActionRow(
                    icon = Icons.Outlined.FormatQuote,
                    text = ADDQUOTE_COMMAND.trim(),
                    onClick = onAddQuote,
                )
                MessageSheetActionRow(
                    icon = Icons.Outlined.SelectAll,
                    text = stringResource(R.string.chat_menu_select),
                    onClick = onSelect,
                )
            }
            MessageSheetActionRow(
                icon = Icons.Outlined.Person,
                text = stringResource(R.string.chat_menu_user_card),
                onClick = onUserCard,
            )
        }
    }
}

@Composable
private fun MessageSheetActionRow(
    icon: ImageVector,
    text: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .clickable(onClick = onClick)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(
            modifier = Modifier.size(40.dp),
            shape = MaterialTheme.shapes.extraSmall,
            color = MaterialTheme.colorScheme.surfaceVariant,
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.width(ResentinSpacing.medium))
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}
