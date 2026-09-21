package io.github.npauloj.mibosmart.app

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.npauloj.mibosmart.app.devices.DeviceListScreen
import io.github.npauloj.mibosmart.app.session.TokenScreen
import io.github.npauloj.mibosmart.app.ui.AppTheme
import org.koin.compose.viewmodel.koinViewModel

/**
 * Startup routing (stored token, expiry guard) is the session slice that follows; here the app always
 * starts on the token screen and the accepted token opens the device list. Where the flag lives, and
 * why it is not `rememberSaveable`, is in [AppViewModel].
 */
@Composable
fun App(viewModel: AppViewModel = koinViewModel()) {
    AppTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            val authenticated by viewModel.authenticated.collectAsStateWithLifecycle()
            if (authenticated) {
                DeviceListScreen()
            } else {
                TokenScreen(onAuthenticated = viewModel::onAuthenticated)
            }
        }
    }
}
