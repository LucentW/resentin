package pm.antani.resentin.ui.common

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import android.graphics.Bitmap
import pm.antani.resentin.R
import pm.antani.resentin.net.dto.WhoisBundleDto

/**
 * The "user card": whois details + personal/moderation actions, reached both by
 * tapping a member in the channel list and by long-pressing a chat message.
 * [ownSigils] and [targetSigils] are this channel's sigils (empty outside a real
 * channel, e.g. in a query), and gate the privilege-toggle row: only shown when the
 * viewer is themself privileged (op or higher) and only for the sigils this
 * network's ircd advertises.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun UserCardSheet(
    whois: WhoisBundleDto,
    viewerUsername: String,
    ownSigils: String,
    targetSigils: String,
    availableModes: Map<String, String>,
    onDismiss: () -> Unit,
    onContactPrivately: (String) -> Unit,
    onKick: (String) -> Unit,
    onBan: (String) -> Unit,
    onSetMode: (nick: String, letter: Char, grant: Boolean) -> Unit,
    // Server /ignore toggle — personal like the DM button (needs no privilege),
    // so it stays visible wherever the card is.
    isIgnored: Boolean = false,
    onIgnore: (String) -> Unit = {},
    onUnignore: (String) -> Unit = {},
    // Peer avatar bitmap when the subject has one (M3b) — null while loading or
    // without one, in which case the tinted initial below stands in.
    avatarBitmap: Bitmap? = null,
    // Kick/ban/privilege toggles only make sense inside a real channel — hidden for a
    // query or the "$server" pseudo-chat, where there's no channel to moderate.
    showChannelActions: Boolean = true,
    // Only set when this sheet was opened by long-pressing a chat message (as opposed
    // to tapping a member in the channel list) — gates the two copy buttons below.
    messageText: String? = null,
) {
    // WHOIS cards can contain badges, channels and moderation controls. The default
    // bottom-sheet state may stop at the half-expanded anchor, which hides the action
    // rows until the user drags the sheet manually. Open this card fully every time;
    // the content itself remains scrollable on smaller screens.
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val clipboardManager = LocalClipboardManager.current
    var showPartialCopyDialog by remember { mutableStateOf(false) }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        // Scrollable: on small screens the action rows would otherwise sit below
        // the fold with no way to reach them.
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
        ) {
            val target = whois.target
            val nickColor = colorForNick(target, isLightTheme())
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Real avatar when the subject has one, else the same deterministic
                // tinted initial as the member list rows.
                val avatar = avatarBitmap
                if (avatar != null) {
                    Image(
                        bitmap = avatar.asImageBitmap(),
                        contentDescription = null,
                        modifier = Modifier.size(56.dp).clip(MaterialTheme.shapes.medium),
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .background(nickColor.copy(alpha = 0.18f), MaterialTheme.shapes.medium),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = target.firstOrNull()?.uppercase().orEmpty(),
                            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                            color = nickColor,
                        )
                    }
                }
                Spacer(modifier = Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(target, style = MaterialTheme.typography.headlineSmall)
                    val handle = listOfNotNull(
                        whois.user?.let { "$it@${whois.host ?: "?"}" },
                        whois.realname,
                    ).joinToString(" · ").takeIf { it.isNotBlank() }
                    if (handle != null) {
                        Text(
                            handle,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (!targetSigils.isBlank()) {
                        Text(
                            targetSigils,
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }

            whois.awayMessage?.let { away ->
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = stringResource(R.string.whois_away_line, away),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            MaterialTheme.colorScheme.surfaceContainerHighest,
                            MaterialTheme.shapes.medium,
                        )
                        .border(
                            1.dp,
                            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                            MaterialTheme.shapes.medium,
                        )
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }

            // Status badges — display-only chips, no actions.
            val badges = buildList {
                if (whois.isRegistered) add(stringResource(R.string.whois_registered_nick))
                if (whois.secure || whois.usingSsl) add(stringResource(R.string.whois_secure_connection))
                if (whois.isOperator) add(stringResource(R.string.irc_badge_operator))
                if (whois.isHelper) add(stringResource(R.string.irc_badge_network_helper))
                if (whois.isAdmin) add(stringResource(R.string.irc_badge_server_administrator))
                if (whois.isServicesAdmin) add(stringResource(R.string.irc_badge_services_administrator))
            }
            if (badges.isNotEmpty()) {
                FlowRow(modifier = Modifier.padding(top = 12.dp)) {
                    badges.forEach { badge ->
                        AssistChip(
                            onClick = {},
                            label = { Text(badge) },
                            modifier = Modifier.padding(end = 8.dp),
                        )
                    }
                }
            }
            if (whois.isOperator && whois.operText != null) {
                Text(
                    whois.operText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            whois.server?.let { server ->
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    stringResource(R.string.whois_server_line, "$server${whois.serverInfo?.let { " ($it)" }.orEmpty()}"),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            whois.account?.let {
                Text(
                    stringResource(R.string.whois_account_line, it),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            whois.channels?.takeIf { it.isNotEmpty() }?.let {
                Text(
                    stringResource(R.string.whois_channels_line, it.joinToString(" ")),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            val showContact = !target.equals(viewerUsername, ignoreCase = true)
            if (showContact) {
                Spacer(modifier = Modifier.height(16.dp))
                Row(modifier = Modifier.fillMaxWidth()) {
                    Button(
                        onClick = { onContactPrivately(target) },
                        shape = MaterialTheme.shapes.medium,
                        modifier = Modifier.weight(1f).padding(end = 8.dp),
                    ) {
                        Text(stringResource(R.string.whois_message_privately))
                    }
                    OutlinedButton(
                        onClick = { if (isIgnored) onUnignore(target) else onIgnore(target) },
                        shape = MaterialTheme.shapes.medium,
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(stringResource(if (isIgnored) R.string.whois_unignore else R.string.whois_ignore))
                    }
                }
            }

            if (messageText != null) {
                Row(modifier = Modifier.padding(top = 8.dp)) {
                    OutlinedButton(
                        onClick = { clipboardManager.setText(AnnotatedString(stripMircCodes(messageText))) },
                        shape = MaterialTheme.shapes.medium,
                        modifier = Modifier.padding(end = 8.dp),
                    ) {
                        Text(stringResource(R.string.whois_copy_message))
                    }
                    OutlinedButton(
                        onClick = { showPartialCopyDialog = true },
                        shape = MaterialTheme.shapes.medium,
                    ) {
                        Text(stringResource(R.string.whois_copy_message_partial))
                    }
                }
            }

            if (showChannelActions) {
                Spacer(modifier = Modifier.height(16.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                Spacer(modifier = Modifier.height(8.dp))
                Text(stringResource(R.string.whois_moderation), style = MaterialTheme.typography.labelLarge)
                Spacer(modifier = Modifier.height(8.dp))
                Row(modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(
                        onClick = { onKick(target) },
                        shape = MaterialTheme.shapes.medium,
                        modifier = Modifier.weight(1f).padding(end = 8.dp),
                    ) {
                        Text(stringResource(R.string.irc_action_kick))
                    }
                    Button(
                        onClick = { onBan(target) },
                        shape = MaterialTheme.shapes.medium,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer,
                            contentColor = MaterialTheme.colorScheme.onErrorContainer,
                        ),
                    ) {
                        Text(stringResource(R.string.irc_action_ban))
                    }
                }

                val privilegedModes =
                    PRIVILEGE_MODES.filter { mode -> availableModes.containsKey(mode.letter.toString()) }
                if (isPrivileged(ownSigils) && privilegedModes.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(stringResource(R.string.whois_channel_privileges), style = MaterialTheme.typography.labelLarge)
                    FlowRow(modifier = Modifier.padding(top = 8.dp)) {
                        privilegedModes.forEach { mode ->
                            val letter = mode.letter
                            val sigil = sigilOfMode(letter)
                            val hasIt = sigil != null && targetSigils.contains(sigil)
                            ResentinFilterChip(
                                selected = hasIt,
                                onClick = { onSetMode(target, letter, !hasIt) },
                                label = { Text(stringResource(mode.labelRes)) },
                                modifier = Modifier.padding(end = 8.dp, bottom = 8.dp),
                            )
                        }
                    }
                }
            }
        }
    }

    if (showPartialCopyDialog && messageText != null) {
        AlertDialog(
            onDismissRequest = { showPartialCopyDialog = false },
            shape = MaterialTheme.shapes.large,
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 0.dp,
            title = {
                Text(
                    stringResource(R.string.whois_copy_message_partial_title),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                )
            },
            text = {
                OutlinedTextField(
                    value = stripMircCodes(messageText),
                    onValueChange = {},
                    readOnly = true,
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                TextButton(onClick = { showPartialCopyDialog = false }) {
                    Text(stringResource(R.string.chat_dialog_close))
                }
            },
        )
    }
}
