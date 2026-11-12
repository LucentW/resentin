package pm.antani.resentin.ui.e2e

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import pm.antani.resentin.R
import pm.antani.resentin.e2e.IdentityState

/** One encrypted conversation with [vm]'s peer. Messages are our own decrypted copies. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun E2eChatScreen(vm: E2eChatViewModel, onBack: () -> Unit, onOpen: (Boolean) -> Unit = {}) {
    val messages by vm.messages.collectAsState()
    val state by vm.state.collectAsState()
    val shown = messages.filter { it.status != "opened" }
    val req = state.request
    // Consent: a conversation that has any row (a message, or the "opened" marker) exists.
    val consented = messages.isNotEmpty()
    val identityReady = state.identity == IdentityState.Ready
    var draft by remember { mutableStateOf("") }
    LaunchedEffect(Unit) { vm.refresh() }
    val keys by vm.keys.collectAsState()
    val sas by vm.sas.collectAsState()
    keys?.let { (own, peer) ->
        androidx.compose.material3.AlertDialog(
            onDismissRequest = vm::closeKeys,
            title = { Text(stringResource(R.string.e2e_keys)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(stringResource(R.string.e2e_key_of, vm.peer), fontWeight = FontWeight.SemiBold)
                    if (peer == null) Text(stringResource(R.string.e2e_peer_no_key)) else {
                        androidx.compose.foundation.text.selection.SelectionContainer { Text(fingerprint(peer.master), fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace) }
                        Text(stringResource(if (peer.verified) R.string.e2e_verified else R.string.e2e_not_verified), style = MaterialTheme.typography.bodySmall)
                        if (!peer.verified) TextButton(onClick = vm::markVerified) { Text(stringResource(R.string.e2e_mark_verified)) }
                    }
                    Text(stringResource(R.string.e2e_key_yours), fontWeight = FontWeight.SemiBold)
                    if (own != null) OwnKeyBlock(own)
                    Text(stringResource(R.string.e2e_compare_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            },
            confirmButton = { TextButton(onClick = vm::closeKeys) { Text(stringResource(R.string.e2e_dismiss)) } },
            dismissButton = {
                if (peer != null && !peer.verified && sas == null) {
                    TextButton(onClick = { vm.closeKeys(); vm.sasStep("start") }) { Text(stringResource(R.string.e2e_sas_start)) }
                }
            },
        )
    }
    // The window is protected while this screen is up (MainActivity decides, from the opt-out).
    androidx.compose.runtime.DisposableEffect(Unit) {
        onOpen(true)
        onDispose { onOpen(false) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = null) } },
                actions = {
                    IconButton(onClick = vm::loadKeys) { Icon(Icons.Outlined.Key, contentDescription = stringResource(R.string.e2e_keys)) }
                },
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.Lock, contentDescription = null, modifier = Modifier.padding(end = 8.dp))
                        Column {
                            Text(vm.peer, fontWeight = FontWeight.SemiBold, maxLines = 1)
                            Text(
                                stringResource(R.string.e2e_chat_subtitle, vm.slug),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                },
            )
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).imePadding()) {
            when {
                state.identity is IdentityState.Elsewhere -> E2eElsewhereCard(vm.slug, onReplace = vm::replaceIdentity)
                state.alarm != null -> KeyAlarmCard(vm.peer, state.alarm!!, onAccept = vm::acceptAlarm, onKeep = vm::dismissAlarm)
                req?.state == "incoming" -> E2eRequestCard(
                    stringResource(R.string.e2e_request_incoming, req.peer, vm.slug),
                    primary = stringResource(R.string.e2e_accept) to { vm.accept(req) },
                    secondary = stringResource(R.string.e2e_refuse) to { vm.refuse(req) },
                )
                req?.state == "outgoing" -> Banner(stringResource(R.string.e2e_waiting_peer, vm.peer))
                req?.state == "refused" || req?.state == "expired" -> E2eRequestCard(
                    stringResource(if (req.state == "refused") R.string.e2e_request_refused else R.string.e2e_request_expired, req.peer),
                    primary = stringResource(R.string.e2e_ask_again) to { vm.dismiss(req); vm.ask() },
                    secondary = stringResource(R.string.e2e_dismiss) to { vm.dismiss(req) },
                )
                !consented && identityReady -> E2eRequestCard(
                    stringResource(R.string.e2e_not_consented, vm.peer),
                    primary = stringResource(R.string.e2e_ask) to { vm.ask() },
                )
            }
            sas?.let { SasCard(it, vm::sasStep) }
            LazyColumn(modifier = Modifier.weight(1f).fillMaxWidth(), reverseLayout = true, contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                items(shown.asReversed(), key = { it.id }) { m ->
                    if (m.status == "system") { SystemRow(m.body, vm.peer); return@items }
                    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = if (m.outgoing) Alignment.CenterEnd else Alignment.CenterStart) {
                        Column(
                            modifier = Modifier
                                .background(
                                    if (m.outgoing) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                                    RoundedCornerShape(12.dp),
                                )
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                        ) {
                            Text(m.body)
                            when (m.status) {
                                "pending" -> Text(stringResource(R.string.e2e_status_pending), style = MaterialTheme.typography.labelSmall)
                                "failed" -> Text(stringResource(R.string.e2e_status_failed), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
            }
            if (consented && identityReady && state.alarm == null) {
                Row(modifier = Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = draft,
                        onValueChange = { draft = it },
                        modifier = Modifier.weight(1f),
                        placeholder = { Text(stringResource(R.string.e2e_composer_hint)) },
                        maxLines = 4,
                    )
                    IconButton(enabled = draft.isNotBlank(), onClick = { vm.send(draft); draft = "" }) {
                        Icon(Icons.AutoMirrored.Outlined.Send, contentDescription = stringResource(R.string.e2e_send))
                    }
                }
            }
        }
    }
}

@Composable
private fun Banner(text: String, error: Boolean = false) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        colors = CardDefaults.cardColors(containerColor = if (error) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.secondaryContainer),
    ) { Text(text, modifier = Modifier.padding(12.dp), style = MaterialTheme.typography.bodyMedium) }
}

/** The verification in progress: asked, waiting, the 7 emoji to compare, or waiting for the other side. */
@Composable
private fun SasCard(v: pm.antani.resentin.e2e.SasView, step: (String) -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            when {
                v.phase == "asked" -> {
                    Text(stringResource(R.string.e2e_sas_asked))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End), modifier = Modifier.fillMaxWidth()) {
                        TextButton(onClick = { step("cancel") }) { Text(stringResource(R.string.e2e_refuse)) }
                        androidx.compose.material3.Button(onClick = { step("accept") }) { Text(stringResource(R.string.e2e_accept)) }
                    }
                }
                v.phase == "compare" && v.confirmed -> {
                    Text(stringResource(R.string.e2e_sas_waiting_peer))
                    TextButton(onClick = { step("cancel") }) { Text(stringResource(R.string.e2e_cancel)) }
                }
                v.phase == "compare" && v.emoji != null -> {
                    Text(stringResource(R.string.e2e_sas_compare), fontWeight = FontWeight.SemiBold)
                    // 7 emoji, two rows: the user reads them out against the other person's screen.
                    v.emoji.chunked(4).forEach { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            row.forEach { i ->
                                val (glyph, name) = pm.antani.resentin.e2e.SAS_EMOJI[i]
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(glyph, fontSize = androidx.compose.ui.unit.TextUnit(30f, androidx.compose.ui.unit.TextUnitType.Sp))
                                    Text(name, style = MaterialTheme.typography.labelSmall)
                                }
                            }
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End), modifier = Modifier.fillMaxWidth()) {
                        TextButton(onClick = { step("mismatch") }) { Text(stringResource(R.string.e2e_sas_no_match)) }
                        androidx.compose.material3.Button(onClick = { step("match") }) { Text(stringResource(R.string.e2e_sas_match)) }
                    }
                }
                else -> {
                    Text(stringResource(R.string.e2e_sas_working))
                    TextButton(onClick = { step("cancel") }) { Text(stringResource(R.string.e2e_cancel)) }
                }
            }
        }
    }
}

/** A line the verification leaves in the chat ("kind|byPeer|reason"). */
@Composable
private fun SystemRow(body: String, peer: String) {
    val (kind, by, reason) = body.split("|").let { Triple(it.getOrElse(0) { "" }, it.getOrElse(1) { "" } == "true", it.getOrElse(2) { "" }) }
    val text = when (kind) {
        "asked" -> stringResource(if (by) R.string.e2e_sas_note_asked_peer else R.string.e2e_sas_note_asked_you, peer)
        "verified" -> stringResource(R.string.e2e_sas_note_verified, peer)
        else -> stringResource(if (by) R.string.e2e_sas_note_cancelled_peer else R.string.e2e_sas_note_cancelled_you, peer, reason)
    }
    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Text(text, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** The contact's master key changed under us: nothing is sent until the user takes the new key or keeps the old one. */
@Composable
private fun KeyAlarmCard(peer: String, alarm: pm.antani.resentin.e2e.AlarmRec, onAccept: () -> Unit, onKeep: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.e2e_alarm, peer), fontWeight = FontWeight.SemiBold)
            Text(stringResource(R.string.e2e_alarm_pinned), style = MaterialTheme.typography.labelMedium)
            Text(fingerprint(alarm.pinned), fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace, style = MaterialTheme.typography.bodySmall)
            Text(stringResource(R.string.e2e_alarm_new), style = MaterialTheme.typography.labelMedium)
            Text(fingerprint(alarm.presented), fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace, style = MaterialTheme.typography.bodySmall)
            Text(stringResource(R.string.e2e_alarm_hint), style = MaterialTheme.typography.bodySmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End), modifier = Modifier.fillMaxWidth()) {
                TextButton(onClick = onKeep) { Text(stringResource(R.string.e2e_alarm_keep)) }
                androidx.compose.material3.Button(onClick = onAccept) { Text(stringResource(R.string.e2e_alarm_accept)) }
            }
        }
    }
}
