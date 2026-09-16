package pm.antani.resentin.ui.home

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import pm.antani.resentin.R
import pm.antani.resentin.ui.common.MircText

// Guided NickServ registration wizard dialog — cicchetto RegistrationWizardModal
// parity (six steps, see RegistrationWizard.kt). Dumb renderer: all sends,
// validation, timeout and auto-complete live in HomeViewModel. The NOTICE mirror
// shows raw service replies bound by id > stepSinceId — a structural bound,
// never a content parse.

private fun stepNumber(step: WizardStep): Int = when (step) {
    WizardStep.INTRO -> 1
    WizardStep.EMAIL -> 2
    WizardStep.PASSWORD -> 3
    WizardStep.REGISTER -> 4
    WizardStep.CODE -> 5
    WizardStep.VERIFY -> 6
}

@Composable
fun RegistrationWizardDialog(viewModel: HomeViewModel, onDismiss: () -> Unit) {
    val wiz by viewModel.registrationWizard.collectAsState()
    val state = wiz ?: return
    val mirror by viewModel.wizardMirror.collectAsState()
    val lines = remember(mirror, state.stepSinceId, state.servicesNick) {
        // ASCII case-fold: every casemapping variant agrees on A-Z for a
        // services nick, same pragmatic fold as AppContainer's topic join.
        mirror.filter { message ->
            message.id > state.stepSinceId &&
                message.kind == "notice" &&
                message.sender.equals(state.servicesNick, ignoreCase = true)
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = MaterialTheme.shapes.large,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 0.dp,
        title = {
            Text(
                text = stringResource(R.string.registration_wizard_title),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = stringResource(R.string.registration_wizard_step, stepNumber(state.step), 6),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                when (state.step) {
                    WizardStep.INTRO -> {
                        Text(
                            text = stringResource(R.string.registration_wizard_intro, state.networkSlug),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = stringResource(R.string.registration_wizard_intro_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    WizardStep.EMAIL -> {
                        Text(
                            text = stringResource(R.string.registration_wizard_email_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            value = state.email,
                            onValueChange = viewModel::setWizardEmail,
                            placeholder = { Text("you@example.com") },
                            singleLine = true,
                            shape = MaterialTheme.shapes.medium,
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Email,
                                imeAction = ImeAction.Next,
                            ),
                            keyboardActions = KeyboardActions(onNext = { viewModel.wizardNext() }),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    WizardStep.PASSWORD -> {
                        Text(
                            text = stringResource(
                                R.string.registration_wizard_password_hint,
                                WIZARD_MIN_PASSWORD,
                                WIZARD_MAX_PASSWORD,
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            value = state.password,
                            onValueChange = viewModel::setWizardPassword,
                            placeholder = { Text(stringResource(R.string.registration_wizard_password_placeholder)) },
                            singleLine = true,
                            shape = MaterialTheme.shapes.medium,
                            visualTransformation = PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Password,
                                imeAction = ImeAction.Next,
                            ),
                            keyboardActions = KeyboardActions(onNext = { viewModel.wizardNext() }),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    WizardStep.REGISTER -> {
                        Text(
                            text = stringResource(R.string.registration_wizard_register_sending),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Spacer(Modifier.height(8.dp))
                        NoticeMirror(lines = lines.map { it.body.orEmpty() })
                        if (state.timedOut) {
                            Spacer(Modifier.height(8.dp))
                            Text(
                                text = stringResource(R.string.registration_wizard_timeout),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    WizardStep.CODE -> {
                        Text(
                            text = stringResource(R.string.registration_wizard_code_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            value = state.code,
                            onValueChange = viewModel::setWizardCode,
                            placeholder = { Text(stringResource(R.string.registration_wizard_code_placeholder)) },
                            singleLine = true,
                            shape = MaterialTheme.shapes.medium,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                            keyboardActions = KeyboardActions(onDone = { viewModel.wizardNext() }),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    WizardStep.VERIFY -> {
                        if (state.succeeded) {
                            Text(
                                text = stringResource(R.string.registration_wizard_success, state.networkSlug),
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        } else {
                            Text(
                                text = stringResource(R.string.registration_wizard_verify_sending),
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            Spacer(Modifier.height(8.dp))
                            NoticeMirror(lines = lines.map { it.body.orEmpty() })
                            if (state.timedOut) {
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    text = stringResource(R.string.registration_wizard_verify_timeout),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
                state.error?.let { error ->
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = error,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        },
        confirmButton = {
            if (state.step != WizardStep.VERIFY) {
                TextButton(onClick = viewModel::wizardNext) {
                    Text(stringResource(R.string.registration_wizard_next))
                }
            }
        },
        dismissButton = {
            when (state.step) {
                WizardStep.REGISTER, WizardStep.VERIFY -> {
                    TextButton(
                        onClick = viewModel::retryWizardSend,
                        enabled = !state.pending && !state.succeeded,
                    ) {
                        Text(
                            if (state.pending) stringResource(R.string.registration_wizard_sending)
                            else stringResource(R.string.registration_wizard_retry),
                        )
                    }
                }
                WizardStep.EMAIL, WizardStep.PASSWORD, WizardStep.CODE -> {
                    TextButton(onClick = viewModel::wizardBack) {
                        Text(stringResource(R.string.registration_wizard_back))
                    }
                }
                WizardStep.INTRO -> Unit
            }
        },
    )
}

@Composable
private fun NoticeMirror(lines: List<String>) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 160.dp)
            .verticalScroll(rememberScrollState()),
    ) {
        if (lines.isEmpty()) {
            Text(
                text = stringResource(R.string.registration_wizard_waiting),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            lines.forEach { body ->
                MircText(
                    text = body,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}
