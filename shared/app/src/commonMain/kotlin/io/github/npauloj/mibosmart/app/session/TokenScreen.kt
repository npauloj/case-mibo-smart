package io.github.npauloj.mibosmart.app.session

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
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
import io.github.npauloj.mibosmart.app.resources.token_subtitle
import io.github.npauloj.mibosmart.app.resources.token_title
import io.github.npauloj.mibosmart.app.resources.token_validate
import io.github.npauloj.mibosmart.app.resources.token_validating
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

/**
 * The way into the app (SPEC S1–S4): paste a token, validate it with one call, move on.
 *
 * @param onAuthenticated called once, when the partner accepted the token; the destination is the
 *   device list.
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

    TokenScreenContent(
        state = state,
        onTokenChange = viewModel::onTokenChange,
        onValidate = viewModel::validate,
        modifier = modifier,
    )
}

/** The screen as a pure function of its state, so every state has a preview and no ViewModel. */
@Composable
fun TokenScreenContent(
    state: TokenEntryUiState,
    onTokenChange: (String) -> Unit,
    onValidate: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Its replacement, `LocalClipboard`, has no way to read plain text from `commonMain`: the text
    // accessors are androidMain-only, which would mean an `expect/actual` for a paste button.
    @Suppress("DEPRECATION")
    val clipboard = LocalClipboardManager.current

    Column(
        modifier = modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(text = stringResource(Res.string.token_title), style = MaterialTheme.typography.headlineSmall)
        Text(text = stringResource(Res.string.token_subtitle), style = MaterialTheme.typography.bodyMedium)

        OutlinedTextField(
            value = state.token,
            onValueChange = onTokenChange,
            modifier = Modifier.fillMaxWidth(),
            enabled = !state.isValidating,
            singleLine = true,
            isError = state.fieldMessage != null,
            label = { Text(stringResource(Res.string.token_field_label)) },
            // The token is a credential, so the middle never reaches the screen; the prefix and the
            // last 4 characters do, so a truncated paste is visible without spending a request
            // (SPEC S1.1, S9, ADR-008).
            visualTransformation = TokenMask,
            supportingText = { TokenFieldSupport(state) },
            trailingIcon = {
                TextButton(
                    onClick = { clipboard.getText()?.text?.let(onTokenChange) },
                    enabled = !state.isValidating,
                ) { Text(stringResource(Res.string.token_paste)) }
            },
        )

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
 * What sits under the field: the message, when there is one, and always the counter that tells a
 * complete paste from a truncated one (SPEC S1.1).
 *
 * The counter reads as "12/35" on screen; screen readers get the same numbers as a sentence.
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
            // The server's sentence wins when there is one (SPEC S3.1): on a 403 the partner already
            // says what to do, and rewording it would only make it vaguer.
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
 * The single message the field can carry: the format hint of SPEC S1.2, or the named failure of the
 * last validation (SPEC U6).
 *
 * The two never compete — typing clears the error, and only a well-formed token can produce one — so
 * the screen has one place a message appears rather than two.
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
