package io.github.npauloj.mibosmart.app.camera

import io.github.npauloj.mibosmart.app.camera.platform.MonitorPageWebPlayer
import io.github.npauloj.mibosmart.app.camera.platform.PlayerEvent
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/** The iOS player surface (SPEC V10, and the iOS half of V2 and V8). */
class LiveVideoPlayerIosTest {

    private val events = mutableListOf<PlayerEvent>()
    private val player = MonitorPageWebPlayer(events::add)

    @AfterTest
    fun tearDown() {
        player.release()
    }

    /**
     * SPEC V10 / V2: the surface *is* the WKWebView on `monitor_url`, and it is pointed at it
     * on composition — the session url expires 15 seconds after the partner minted it.
     */
    @Test
    fun rendersWebViewForMonitorUrl() {
        player.load(MONITOR_URL)

        assertEquals(MONITOR_URL, player.loadedUrl)
        assertSame(player, player.webView.navigationDelegate)
        assertEquals(
            1,
            player.webView.configuration.userContentController.userScripts.size,
            "the playback probe is what turns a playing <video> into FirstFrame",
        )
        assertTrue(events.isEmpty(), "unexpected events on load: $events")
    }

    /**
     * SPEC V8, iOS half: leaving the screen must drop the page, its media and every hook back
     * into the app — a teardown that still reported events would put a dead screen back on a
     * stream.
     */
    @Test
    fun disposeReleasesTheWebView() {
        player.load(MONITOR_URL)

        player.release()

        assertNull(player.loadedUrl)
        assertNull(player.webView.navigationDelegate)
        assertEquals(
            0,
            player.webView.configuration.userContentController.userScripts.size,
            "a released player must not re-inject its probe into the next page",
        )
    }

    /**
     * A session the partner opened without a player page cannot be shown here — this platform
     * decodes nothing itself.
     */
    @Test
    fun aSessionWithoutAMonitorPageIsNotPlayable() {
        player.load(null)

        assertNull(player.loadedUrl)
        assertEquals(listOf(PlayerEvent.DecodeError), events)
    }

    /** The page's vocabulary is the app's: the same `PlayerEvent`s the Android actual reports. */
    @Test
    fun playbackMessagesBecomePlayerEvents() {
        player.onPlaybackMessage("playing")
        player.onPlaybackMessage("ended")
        player.onPlaybackMessage("failed")
        player.onPlaybackMessage("something the page made up")
        player.onPlaybackMessage(null)

        assertEquals(
            listOf(PlayerEvent.FirstFrame, PlayerEvent.Ended, PlayerEvent.DecodeError),
            events,
        )
    }

    private companion object {
        const val MONITOR_URL = "https://monitor.invalid/player?session=test"
    }
}
