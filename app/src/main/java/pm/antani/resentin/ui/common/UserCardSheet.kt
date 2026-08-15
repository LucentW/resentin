package pm.antani.resentin.ui.common

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import pm.antani.resentin.R
import pm.antani.resentin.net.dto.WhoisBundleDto

/**
 * The "user card": whois details + moderation actions, reached both by tapping a
 * member in the channel list and by long-pressing a chat message. [ownSigils] and
 * [targetSigils] are this channel's sigils (empty outside a real channel, e.g. in a
 * query), and gate the privilege-toggle row: only shown when the viewer is themself
 * privileged (op or higher) and only for the sigils this network's ircd advertises.
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
    // Kick/ban/privilege toggles only make sense inside a real channel — hidden for a
    // query or the "$server" pseudo-chat, where there's no channel to moderate.
    showChannelActions: Boolean = true,
    // Only set when this sheet was opened by long-pressing a chat message (as opposed
    // to tapping a member in the channel list) — gates the two copy buttons below.
    messageText: String? = null,
) {
    val sheetState = rememberModalBottomSheetState()
    val clipboardManager = LocalClipboardManager.current
    var showPartialCopyDialog by remember { mutableStateOf(false) }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(modifier = Modifier.fillMaxWidth().padding(24.dp)) {
            Text(whois.target, style = MaterialTheme.typography.headlineSmall)
            whois.user?.let { user -> Text("$user@${whois.host ?: "?"}") }
            whois.realname?.let { Text(it) }
            whois.server?.let { server ->
                Text(
                    stringResource(R.string.whois_server_line, "$server${whois.serverInfo?.let { " ($it)" }.orEmpty()}"),
                )
            }
            whois.account?.let { Text(stringResource(R.string.whois_account_line, it)) }
            whois.awayMessage?.let { Text(stringResource(R.string.whois_away_line, it)) }
            if (whois.isOperator) {
                Text(
                    text = "⚡ IRC Operator" + (whois.operText?.let { " — $it" } ?: ""),
                    color = MaterialTheme.colorScheme.error,
                )
            }
            if (whois.isServicesAdmin) Text("Services Administrator", color = MaterialTheme.colorScheme.error)
            if (whois.isAdmin) Text("Server Administrator", color = MaterialTheme.colorScheme.error)
            if (whois.isHelper) Text("Network Helper")
            if (whois.isRegistered) Text(stringResource(R.string.whois_registered_nick))
            if (whois.secure || whois.usingSsl) Text(stringResource(R.string.whois_secure_connection))

            val target = whois.target
            val showContact = !target.equals(viewerUsername, ignoreCase = true)
            if (showContact) {
                OutlinedButton(onClick = { onContactPrivately(target) }, modifier = Modifier.padding(top = 16.dp)) {
                    Text(stringResource(R.string.whois_message_privately))
                }
            }

            if (messageText != null) {
                Row(modifier = Modifier.padding(top = 8.dp)) {
                    OutlinedButton(
                        onClick = { clipboardManager.setText(AnnotatedString(stripMircCodes(messageText))) },
                        modifier = Modifier.padding(end = 8.dp),
                    ) {
                        Text(stringResource(R.string.whois_copy_message))
                    }
                    OutlinedButton(onClick = { showPartialCopyDialog = true }) {
                        Text(stringResource(R.string.whois_copy_message_partial))
                    }
                }
            }

            if (showChannelActions) {
                Row(modifier = Modifier.padding(top = 8.dp)) {
                    OutlinedButton(onClick = { onKick(target) }, modifier = Modifier.padding(end = 8.dp)) {
                        Text("Kick")
                    }
                    Button(onClick = { onBan(target) }) {
                        Text("Ban")
                    }
                }

                val privilegedModes =
                    PRIVILEGE_MODES.filter { (letter, _) -> availableModes.containsKey(letter.toString()) }
                if (isPrivileged(ownSigils) && privilegedModes.isNotEmpty()) {
                    HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))
                    Text(stringResource(R.string.whois_channel_privileges), style = MaterialTheme.typography.labelLarge)
                    FlowRow(modifier = Modifier.padding(top = 8.dp)) {
                        privilegedModes.forEach { (letter, label) ->
                            val sigil = sigilOfMode(letter)
                            val hasIt = sigil != null && targetSigils.contains(sigil)
                            OutlinedButton(
                                onClick = { onSetMode(target, letter, !hasIt) },
                                modifier = Modifier.padding(end = 8.dp, bottom = 8.dp),
                            ) {
                                Text(if (hasIt) stringResource(R.string.whois_remove_privilege, label) else label)
                            }
                        }
                    }
                }
            }
        }
    }

    if (showPartialCopyDialog && messageText != null) {
        AlertDialog(
            onDismissRequest = { showPartialCopyDialog = false },
            title = { Text(stringResource(R.string.whois_copy_message_partial_title)) },
            text = {
                OutlinedTextField(
                    value = stripMircCodes(messageText),
                    onValueChange = {},
                    readOnly = true,
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
