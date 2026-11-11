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
fun E2eChatScreen(vm: E2eChatViewModel, onBack: () -> Unit) {
    val messages by vm.messages.collectAsState()
    val state by vm.state.collectAsState()
    val shown = messages.filter { it.status != "opened" }
    val req = state.request
    // Consent: a conversation that has any row (a message, or the "opened" marker) exists.
    val consented = messages.isNotEmpty()
    val identityReady = state.identity == IdentityState.Ready
    var draft by remember { mutableStateOf("") }
    LaunchedEffect(Unit) { vm.refresh() }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = null) } },
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
                state.alarm -> Banner(stringResource(R.string.e2e_alarm, vm.peer), error = true)
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
            LazyColumn(modifier = Modifier.weight(1f).fillMaxWidth(), reverseLayout = true, contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                items(shown.asReversed(), key = { it.id }) { m ->
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
            if (consented && identityReady && !state.alarm) {
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
