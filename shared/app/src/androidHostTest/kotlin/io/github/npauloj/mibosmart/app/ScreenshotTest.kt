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

/**
 * One golden per screen state, drawn on the host (ADR-024): six screens, 42 states.
 *
 * **The state list is not maintained here.** Each screen's `PreviewParameterProvider` is already the
 * inventory of its states — that is what the previews are for — so this file names those same states
 * and then asserts, per screen, that it named *all* of them. Add a state to a provider without adding
 * it here and `everyStateIsCaptured` fails; the golden set cannot silently fall behind the previews.
 *
 * Why not Roborazzi's preview-test generator, which would remove even this: it does not work in this
 * project. Turning it on makes `commonTest` lose `:shared:data` and Koin — it generates into a source
 * set named `androidMain` and, doing so, breaks the association an `androidLibrary` KMP target keeps
 * between its host tests and its main compilation. Measured, not assumed: with the generator off the
 * same tree compiles, with it on 124 errors appear in files the generator never touched. Recorded in
 * ADR-024, and worth revisiting when Roborazzi supports the KMP Android layout.
 *
 * `GraphicsMode.NATIVE` is mandatory — the legacy mode draws nothing and every image comes out blank.
 * The device and, crucially, the **locale** are pinned in `robolectric.properties`: Robolectric
 * defaults to `en`, which silently records goldens of `values-en/` while the app ships pt-BR.
 *
 * ## One class per screen
 *
 * Not cosmetic: Gradle hands **classes** to test forks, so forty-two captures in a single class run
 * in a single fork no matter what `maxParallelForks` says. Six classes are six units of work the
 * runner can spread. Measured on a developer machine before the split: the module's whole test task
 * takes 28 s and writes no image, while recording the same set takes **1611 s** — the captures are
 * effectively the entire cost of the screenshot step, and they were all serial.
 */
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
     * The player surface is an `expect` composable backed by Media3 on Android (ADR-005), which cannot
     * be instantiated on the host. `LocalInspectionMode` is what the previews already use to make it
     * render a placeholder instead — so the goldens show the same thing a preview does, and no socket
     * is opened and no streaming quota is spent by a test.
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


/**
 * Captures every named state and then checks the naming against the provider.
 *
 * The assertion runs **after** the captures so a drift failure still leaves the images behind to
 * look at; failing first would hide the very screens someone is trying to review.
 */
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