package io.github.npauloj.mibosmart.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import com.github.takahirom.roborazzi.captureRoboImage
import io.github.npauloj.mibosmart.app.camera.LiveVideoScreenContent
import io.github.npauloj.mibosmart.app.camera.StreamStateProvider
import io.github.npauloj.mibosmart.app.devices.DeviceListScreenContent
import io.github.npauloj.mibosmart.app.devices.DeviceListUiStateProvider
import io.github.npauloj.mibosmart.app.lock.LockScreenContent
import io.github.npauloj.mibosmart.app.lock.LockUiStateProvider
import io.github.npauloj.mibosmart.app.lock.OpeningHistoryContent
import io.github.npauloj.mibosmart.app.lock.OpeningHistoryUiStateProvider
import io.github.npauloj.mibosmart.app.session.AccountScreenContent
import io.github.npauloj.mibosmart.app.session.AccountUiStateProvider
import io.github.npauloj.mibosmart.app.session.TokenEntryUiStateProvider
import io.github.npauloj.mibosmart.app.session.TokenScreenContent
import io.github.npauloj.mibosmart.app.ui.AppTheme
import kotlin.test.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode

/** One golden per screen state, drawn on the host (ADR-024): six screens, 42 states. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class TokenScreenshotTest {

    @Test
    fun captures() = captureAll(
        screen = "TokenScreen",
        provider = TokenEntryUiStateProvider(),
        states = with(TokenEntryUiStateProvider) {
            listOf("Idle" to Idle, "Typing" to Typing, "InvalidFormat" to InvalidFormat, "Validating" to Validating, "Error" to Error)
        },
    ) { state ->
        TokenScreenContent(state = state, onTokenChange = {}, onValidate = {}, onOpenPortal = {})
    }
}

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class DeviceListScreenshotTest {

    @Test
    fun captures() = captureAll(
        screen = "DeviceListScreen",
        provider = DeviceListUiStateProvider(),
        states = with(DeviceListUiStateProvider) {
            listOf("Loading" to Loading, "Success" to Success, "LoadingMore" to LoadingMore, "Empty" to Empty, "Error" to Error, "Stale" to Stale)
        },
    ) { state ->
        DeviceListScreenContent(
            state = state,
            onRetry = {},
            onRefresh = {},
            onSelectFilter = {},
            onLoadMore = {},
            onCameraTap = {},
            onLockTap = {},
        )
    }
}

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class LockScreenshotTest {

    @Test
    fun captures() = captureAll(
        screen = "LockScreen",
        provider = LockUiStateProvider(),
        states = with(LockUiStateProvider) {
            listOf(
                "Loading" to Loading, "Locked" to Locked, "Unlocked" to Unlocked,
                "CommandSent" to CommandSent, "CommandExpired" to CommandExpired,
                "CommandCheckFailed" to CommandCheckFailed, "CommandFailed" to CommandFailed,
                "RemoteOpenDisabled" to RemoteOpenDisabled,
                "VolumeChanging" to VolumeChanging, "VolumeFailed" to VolumeFailed,
                "VolumeUnknown" to VolumeUnknown,
                "WritesDisabled" to WritesDisabled, "Offline" to Offline, "Error" to Error,
            )
        },
    ) { state ->
        LockScreenContent(
            state = state,
            onRetry = {},
            onCommand = {},
            onVerify = {},
            onChangeVolume = {},
            onEnableRemoteOpen = {},
            onBack = {},
        )
    }
}

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class LiveVideoScreenshotTest {

    /**
     * The player surface is an `expect` composable backed by Media3 on Android (ADR-005), which
     * cannot be instantiated on the host.
     */
    @Test
    fun captures() = captureAll(
        screen = "LiveVideoScreen",
        provider = StreamStateProvider(),
        states = with(StreamStateProvider) {
            listOf(
                "Creating" to Creating, "Live" to Live, "Reconnecting" to Reconnecting,
                "QuotaExceeded" to QuotaExceeded, "Offline" to Offline,
                "NoLiveCapability" to NoLiveCapability, "Failed" to Failed,
                "FailedWithoutFallback" to FailedWithoutFallback, "WebFallback" to WebFallback,
            )
        },
    ) { state ->
        CompositionLocalProvider(LocalInspectionMode provides true) {
            LiveVideoScreenContent(
                cameraName = "Câmera da sala",
                state = state,
                onPlayerEvent = {},
                onRetry = {},
                onWebPlayer = {},
                onCloseWebPlayer = {},
                onBack = {},
            )
        }
    }
}

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class OpeningHistoryScreenshotTest {

    @Test
    fun captures() = captureAll(
        screen = "OpeningHistory",
        provider = OpeningHistoryUiStateProvider(),
        states = with(OpeningHistoryUiStateProvider) {
            listOf("Loading" to Loading, "List" to List, "Empty" to Empty, "Error" to Error)
        },
    ) { state ->
        OpeningHistoryContent(state = state, onRetry = {}, onBack = {})
    }
}

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class AccountScreenshotTest {

    @Test
    fun captures() = captureAll(
        screen = "AccountScreen",
        provider = AccountUiStateProvider(),
        states = with(AccountUiStateProvider) {
            listOf("Valid" to Valid, "ExpiringSoon" to ExpiringSoon, "Renewing" to Renewing, "RenewalFailed" to RenewalFailed, "Expired" to Expired)
        },
    ) { state ->
        AccountScreenContent(state = state, onRenew = {}, onSignOut = {}, onBack = {})
    }
}

/** Captures every named state and then checks the naming against the provider. */
private fun <T> captureAll(
    screen: String,
    provider: PreviewParameterProvider<T>,
    states: List<Pair<String, T>>,
    content: @Composable (T) -> Unit,
) {
    states.forEach { (name, state) ->
        captureRoboImage("src/androidHostTest/goldens/${screen}_$name.png") {
            AppTheme { content(state) }
        }
    }
    assertEquals(
        provider.values.count(),
        states.size,
        "$screen has states the goldens do not cover — add them to this list, or the screenshot " +
            "set silently falls behind the previews it is supposed to mirror",
    )
}