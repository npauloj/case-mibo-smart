package io.github.npauloj.mibosmart.app.session

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.npauloj.mibosmart.app.resources.Res
import io.github.npauloj.mibosmart.app.resources.account_back
import io.github.npauloj.mibosmart.app.resources.account_expired
import io.github.npauloj.mibosmart.app.resources.account_expires_in_hours
import io.github.npauloj.mibosmart.app.resources.account_expires_in_minutes
import io.github.npauloj.mibosmart.app.resources.account_logout
import io.github.npauloj.mibosmart.app.resources.account_logout_failed
import io.github.npauloj.mibosmart.app.resources.account_requests
import io.github.npauloj.mibosmart.app.resources.account_title
import io.github.npauloj.mibosmart.app.resources.account_token_label
import io.github.npauloj.mibosmart.app.resources.account_token_value
import io.github.npauloj.mibosmart.app.resources.session_expiring_soon
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

/**
 * Who is signed in, for how much longer, and the way out (SPEC S8, S9).
 *
 * @param onSignedOut called once the credential has left the device; the destination is the token
 *   screen.
 */
@Composable
fun AccountScreen(
    onBack: () -> Unit,
    onSignedOut: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: AccountViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(viewModel) {
        viewModel.signedOut.collect { onSignedOut() }
    }

    AccountScreenContent(
        state = state,
        onSignOut = viewModel::signOut,
        onBack = onBack,
        modifier = modifier,
    )
}

/** The screen as a pure function of its state, so every state has a preview and no ViewModel. */
@Composable
fun AccountScreenContent(
    state: AccountUiState,
    onSignOut: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        TextButton(onClick = onBack) { Text(stringResource(Res.string.account_back)) }
        Text(text = stringResource(Res.string.account_title), style = MaterialTheme.typography.headlineSmall)

        SessionCard(state)

        // Only in a debug build: outside one the counter is null and the line does not exist
        // (ADR-006).
        state.requestCount?.let { count ->
            Text(
                text = stringResource(Res.string.account_requests, count.toString()),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        if (state.signOutFailed) {
            Text(
                text = stringResource(Res.string.account_logout_failed),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
            )
        }

        Button(onClick = onSignOut, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(Res.string.account_logout))
        }
    }
}

/**
 * The credential and its deadline.
 *
 * The token is rendered as four dots and its last 4 characters — enough to tell two pasted tokens
 * apart, and the most SPEC S9 permits anywhere in the app (ADR-008).
 */
@Composable
private fun SessionCard(state: AccountUiState) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = stringResource(Res.string.account_token_label),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = stringResource(Res.string.account_token_value, state.tokenSuffix),
                style = MaterialTheme.typography.titleMedium,
            )
            ExpiryLine(state.expiry)
        }
    }
}

/**
 * "Expira em 1 h 47 min", "Expira em 7 min" or "Sessão expirada".
 *
 * The hours are dropped below one on purpose: "0 h 7 min" reads as a rounding artefact next to a
 * deadline the user is being warned about.
 */
@Composable
private fun ExpiryLine(expiry: SessionExpiry) {
    when (expiry) {
        // The vault has not answered; the card keeps its shape and says nothing it does not know.
        SessionExpiry.Unknown -> Unit

        is SessionExpiry.Remaining -> Text(
            text = if (expiry.hours > 0) {
                stringResource(
                    Res.string.account_expires_in_hours,
                    expiry.hours.toString(),
                    expiry.minutes.toString(),
                )
            } else {
                stringResource(Res.string.account_expires_in_minutes, expiry.minutes.toString())
            },
            style = MaterialTheme.typography.bodyMedium,
            color = if (expiry.soon) {
                MaterialTheme.colorScheme.error
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
        )

        SessionExpiry.Expired -> Text(
            text = stringResource(Res.string.account_expired),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error,
        )
    }

    if (expiry is SessionExpiry.Remaining && expiry.soon) {
        Text(
            text = stringResource(Res.string.session_expiring_soon),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
