package io.github.npauloj.mibosmart.app.session

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.npauloj.mibosmart.app.resources.Res
import io.github.npauloj.mibosmart.app.resources.token_error_failed
import io.github.npauloj.mibosmart.app.resources.token_error_offline
import io.github.npauloj.mibosmart.app.resources.token_error_rejected
import io.github.npauloj.mibosmart.app.resources.token_error_unexpected
import io.github.npauloj.mibosmart.app.resources.token_field_label
import io.github.npauloj.mibosmart.app.resources.token_paste
import io.github.npauloj.mibosmart.app.resources.token_subtitle
import io.github.npauloj.mibosmart.app.resources.token_title
import io.github.npauloj.mibosmart.app.resources.token_validate
import io.github.npauloj.mibosmart.app.resources.token_validating
import kotlinx.coroutines.launch
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
    val scope = rememberCoroutineScope()

    LaunchedEffect(viewModel) {
        viewModel.openDeviceList.collect { onAuthenticated() }
    }

    TokenScreenContent(
        state = state,
        onTokenChange = viewModel::onTokenChange,
        onValidate = { scope.launch { viewModel.onValidate() } },
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
            isError = state.error != null,
            label = { Text(stringResource(Res.string.token_field_label)) },
            // The token is a credential: it is masked on screen as well as in logs (ADR-008).
            visualTransformation = PasswordVisualTransformation(),
            trailingIcon = {
                TextButton(
                    onClick = { clipboard.getText()?.text?.let(onTokenChange) },
                    enabled = !state.isValidating,
                ) { Text(stringResource(Res.string.token_paste)) }
            },
        )

        state.error?.let { error ->
            Text(
                text = stringResource(error.message),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
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

/** One friendly sentence per category, never the server's own words (SPEC U6). */
private val TokenEntryError.message: StringResource
    get() = when (this) {
        TokenEntryError.TokenRejected -> Res.string.token_error_rejected
        TokenEntryError.Offline -> Res.string.token_error_offline
        TokenEntryError.UnexpectedResponse -> Res.string.token_error_unexpected
        TokenEntryError.Failed -> Res.string.token_error_failed
    }
