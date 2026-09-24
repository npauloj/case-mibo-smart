package io.github.npauloj.mibosmart.app.session

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.npauloj.mibosmart.app.resources.Res
import io.github.npauloj.mibosmart.app.resources.token_counter
import io.github.npauloj.mibosmart.app.resources.token_counter_description
import io.github.npauloj.mibosmart.app.resources.token_error_expired
import io.github.npauloj.mibosmart.app.resources.token_error_failed
import io.github.npauloj.mibosmart.app.resources.token_error_format
import io.github.npauloj.mibosmart.app.resources.token_error_offline
import io.github.npauloj.mibosmart.app.resources.token_error_rejected
import io.github.npauloj.mibosmart.app.resources.token_error_unexpected
import io.github.npauloj.mibosmart.app.resources.token_field_label
import io.github.npauloj.mibosmart.app.resources.token_paste
import io.github.npauloj.mibosmart.app.resources.token_portal
import io.github.npauloj.mibosmart.app.resources.token_portal_hint
import io.github.npauloj.mibosmart.app.resources.token_subtitle
import io.github.npauloj.mibosmart.app.resources.token_title
import io.github.npauloj.mibosmart.app.resources.token_validate
import io.github.npauloj.mibosmart.app.resources.token_validating
import io.github.npauloj.mibosmart.app.ui.StateRail
import io.github.npauloj.mibosmart.app.ui.StateTone
import io.github.npauloj.mibosmart.app.ui.Tabular
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

/**
 * The way into the app (SPEC S1–S4): paste a token, validate it with one call, move on.
 * @param onAuthenticated called once, when the partner accepted the token; the destination is
 * the device list.
 */
@Composable
fun TokenScreen(
    onAuthenticated: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: TokenEntryViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(viewModel) {
        viewModel.openDeviceList.collect { onAuthenticated() }
    }

    val uriHandler = LocalUriHandler.current
    val portal: PortalUrl = koinInject()

    TokenScreenContent(
        state = state,
        onTokenChange = viewModel::onTokenChange,
        onValidate = viewModel::validate,
        onOpenPortal = { uriHandler.openUri(portal.value) },
        modifier = modifier,
    )
}

/** The screen as a pure function of its state, so every state has a preview and no ViewModel. */
@Composable
fun TokenScreenContent(
    state: TokenEntryUiState,
    onTokenChange: (String) -> Unit,
    onValidate: () -> Unit,
    onOpenPortal: () -> Unit,
    modifier: Modifier = Modifier,
) {
    @Suppress("DEPRECATION")
    val clipboard = LocalClipboardManager.current

    Column(
        modifier = modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 32.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Text(text = stringResource(Res.string.token_title), style = MaterialTheme.typography.headlineSmall)
        Text(
            text = stringResource(Res.string.token_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        StateRail(tone = state.tone()) {
            OutlinedTextField(
                value = state.token,
                onValueChange = onTokenChange,
                modifier = Modifier.fillMaxWidth(),
                enabled = !state.isValidating,
                singleLine = true,
                isError = state.fieldMessage != null,
                label = { Text(stringResource(Res.string.token_field_label)) },
                textStyle = Tabular,
                visualTransformation = TokenMask,
                supportingText = { TokenFieldSupport(state) },
                trailingIcon = {
                    TextButton(
                        onClick = { clipboard.getText()?.text?.let(onTokenChange) },
                        enabled = !state.isValidating,
                    ) { Text(stringResource(Res.string.token_paste)) }
                },
            )
        }

        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            TextButton(
                onClick = onOpenPortal,
                modifier = Modifier.align(Alignment.Start).offset(x = (-12).dp),
            ) {
                Text(stringResource(Res.string.token_portal))
            }
            Text(
                text = stringResource(Res.string.token_portal_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Button(
            onClick = onValidate,
            modifier = Modifier.fillMaxWidth(),
            enabled = state.canSubmit,
        ) {
            if (state.isValidating) {
                val validating = stringResource(Res.string.token_validating)
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp).semantics { contentDescription = validating },
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onPrimary,
                )
            } else {
                Text(stringResource(Res.string.token_validate))
            }
        }
    }
}

/**
 * Which tone the rail carries, which is the same question as "what does the app know right
 * now".
 */
private fun TokenEntryUiState.tone(): StateTone = when {
    fieldMessage != null -> StateTone.Failed
    isValidating -> StateTone.Waiting
    else -> StateTone.Settled
}

/**
 * What sits under the field: the message, when there is one, and always the counter that tells
 * a complete paste from a truncated one (SPEC S1.1).
 */
@Composable
private fun TokenFieldSupport(state: TokenEntryUiState) {
    val counted = state.characterCount.toString()
    val expected = TokenFormat.LENGTH.toString()
    val counterDescription = stringResource(Res.string.token_counter_description, counted, expected)

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = state.serverMessage ?: state.fieldMessage?.let { stringResource(it) }.orEmpty(),
            modifier = Modifier.weight(1f),
        )
        Text(
            text = stringResource(Res.string.token_counter, counted, expected),
            modifier = Modifier.semantics { contentDescription = counterDescription },
        )
    }
}

/**
 * The single message the field can carry: the format hint of SPEC S1.2, or the named failure of
 * the last validation (SPEC U6).
 */
private val TokenEntryUiState.fieldMessage: StringResource?
    get() = when {
        error != null -> error.message
        hasInvalidFormat -> Res.string.token_error_format
        else -> null
    }

/** One friendly sentence per category, never the server's own words (SPEC U6). */
private val TokenEntryError.message: StringResource
    get() = when (this) {
        TokenEntryError.TokenRejected -> Res.string.token_error_rejected
        TokenEntryError.TokenExpired -> Res.string.token_error_expired
        TokenEntryError.Offline -> Res.string.token_error_offline
        TokenEntryError.UnexpectedResponse -> Res.string.token_error_unexpected
        TokenEntryError.Failed -> Res.string.token_error_failed
    }
