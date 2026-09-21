package io.github.npauloj.mibosmart.app

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.npauloj.mibosmart.app.resources.Res
import io.github.npauloj.mibosmart.app.resources.device_list_placeholder
import io.github.npauloj.mibosmart.app.resources.device_list_title
import io.github.npauloj.mibosmart.app.session.TokenScreen
import io.github.npauloj.mibosmart.app.ui.AppTheme
import org.jetbrains.compose.resources.stringResource

@Composable
fun App() {
    AppTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            // Startup routing (stored token, expiry guard) is the session slice that follows; here the
            // app always starts on the token screen and the accepted token opens the destination below.
            var authenticated by rememberSaveable { mutableStateOf(false) }
            if (authenticated) {
                DeviceListPlaceholder()
            } else {
                TokenScreen(onAuthenticated = { authenticated = true })
            }
        }
    }
}

/** Stands in for the device list until the device slice replaces it. */
@Composable
private fun DeviceListPlaceholder() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(modifier = Modifier.padding(24.dp)) {
            Text(
                text = stringResource(Res.string.device_list_title),
                style = MaterialTheme.typography.headlineSmall,
            )
            Text(
                text = stringResource(Res.string.device_list_placeholder),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}
