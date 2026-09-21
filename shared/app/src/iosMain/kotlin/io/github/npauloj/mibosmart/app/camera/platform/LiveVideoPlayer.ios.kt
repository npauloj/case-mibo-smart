package io.github.npauloj.mibosmart.app.camera.platform

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.viewinterop.UIKitView
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.ObjCSignatureOverride
import kotlinx.cinterop.readValue
import platform.CoreGraphics.CGRectZero
import platform.Foundation.NSError
import platform.Foundation.NSURL
import platform.Foundation.NSURLRequest
import platform.WebKit.WKAudiovisualMediaTypeNone
import platform.WebKit.WKNavigation
import platform.WebKit.WKNavigationDelegateProtocol
import platform.WebKit.WKScriptMessage
import platform.WebKit.WKScriptMessageHandlerProtocol
import platform.WebKit.WKUserContentController
import platform.WebKit.WKUserScript
import platform.WebKit.WKUserScriptInjectionTime
import platform.WebKit.WKWebView
import platform.WebKit.WKWebViewConfiguration
import platform.darwin.NSObject

/**
 * The partner's own player page in a `WKWebView` (ADR-005, SPEC V10).
 *
 * iOS plays [monitorUrl], not [url]: `AVPlayer` cannot read a raw fragmented-MP4 HTTP stream without
 * an HLS manifest, and VLCKit was rejected, so the decoding burden stays on the page the partner
 * already ships. This is the only path — nothing here is conditioned on anyone having watched the
 * page work first. Whether it plays is discovered at runtime: the surface reports `FirstFrame` only
 * when a `<video>` element actually starts, so a page that loads but never plays runs out U1's
 * first-frame budget and the screen offers "Abrir no player web" (SPEC V10, V-02).
 *
 * The web view is created by `remember(monitorUrl)` and released by `DisposableEffect`, so leaving
 * the screen or a new session tears down the page and its media — the half of SPEC V8 the ViewModel
 * cannot do, because it does not own a player.
 */
@Composable
actual fun LiveVideoPlayer(
    url: String,
    monitorUrl: String?,
    onEvent: (PlayerEvent) -> Unit,
    modifier: Modifier,
) {
    if (LocalInspectionMode.current) {
        Box(modifier.background(MaterialTheme.colorScheme.surfaceVariant))
        return
    }
    // The delegate outlives a recomposition; without this it would keep calling yesterday's lambda.
    val latestOnEvent by rememberUpdatedState(onEvent)
    val player = remember(monitorUrl) { MonitorPageWebPlayer { latestOnEvent(it) } }
    DisposableEffect(player) {
        player.load(monitorUrl)
        onDispose { player.release() }
    }

    UIKitView(factory = { player.webView }, modifier = modifier)
}

/**
 * The `WKWebView` that shows one monitor page, and the translation of what the page does into
 * [PlayerEvent]s.
 *
 * It is a class beside the composable rather than logic inside it because this is the part worth
 * testing: `LiveVideoPlayerIosTest` builds one, loads a url and releases it without a Compose
 * runtime, and the macOS CI job is the only place any of it runs at all (ADR-013).
 */
@OptIn(ExperimentalForeignApi::class)
internal class MonitorPageWebPlayer(
    private val onEvent: (PlayerEvent) -> Unit,
) : NSObject(), WKNavigationDelegateProtocol, WKScriptMessageHandlerProtocol {

    private val contentController = WKUserContentController()

    val webView: WKWebView = WKWebView(
        frame = CGRectZero.readValue(),
        configuration = WKWebViewConfiguration().apply {
            userContentController = contentController
            // The page is a live stream inside a 16:9 frame: it must play there, and it must not
            // wait for a tap the user has no reason to give.
            allowsInlineMediaPlayback = true
            mediaTypesRequiringUserActionForPlayback = WKAudiovisualMediaTypeNone
        },
    )

    /** The url currently loaded, for the test and for nothing else. Null before [load], after [release]. */
    var loadedUrl: String? = null
        private set

    init {
        contentController.addUserScript(
            WKUserScript(
                source = PLAYBACK_PROBE,
                injectionTime = WKUserScriptInjectionTime.WKUserScriptInjectionTimeAtDocumentEnd,
                forMainFrameOnly = false,
            ),
        )
        contentController.addScriptMessageHandler(this, PLAYBACK_CHANNEL)
        webView.navigationDelegate = this
        webView.scrollView.scrollEnabled = false
    }

    /**
     * Points the page at one session's monitor url.
     *
     * No url, or one the system cannot parse, is reported as `DecodeError` rather than left on an
     * empty page: this platform decodes nothing itself, so a session without a player page is a
     * session nothing can show — and an unplayable stream must stop spending quota at once (V8).
     */
    fun load(monitorUrl: String?) {
        val target = monitorUrl?.let { NSURL.URLWithString(it) }
        if (target == null) {
            onEvent(PlayerEvent.DecodeError)
            return
        }
        loadedUrl = monitorUrl
        webView.loadRequest(NSURLRequest.requestWithURL(target))
    }

    /**
     * Stops the page, unhooks it and drops its media.
     *
     * The delegate and the message handler go first on purpose: a teardown must not turn into a
     * `NetworkError` that puts the screen back on a stream the user already left. Removing the
     * handler is also what breaks the retain cycle — `WKUserContentController` holds it strongly, so
     * a player that skipped this would keep itself, its web view and the page alive after the screen
     * was gone.
     */
    fun release() {
        webView.navigationDelegate = null
        contentController.removeScriptMessageHandlerForName(PLAYBACK_CHANNEL)
        contentController.removeAllUserScripts()
        webView.stopLoading()
        // Navigating away is what releases the decoder; `stopLoading` alone leaves a playing video.
        webView.loadHTMLString(string = "", baseURL = null)
        loadedUrl = null
    }

    /** What the page reported, as this app's vocabulary. Unknown messages are ignored. */
    internal fun onPlaybackMessage(body: String?) {
        val event = when (body) {
            PLAYING -> PlayerEvent.FirstFrame
            ENDED -> PlayerEvent.Ended
            FAILED -> PlayerEvent.DecodeError
            else -> return
        }
        onEvent(event)
    }

    override fun userContentController(
        userContentController: WKUserContentController,
        didReceiveScriptMessage: WKScriptMessage,
    ) {
        onPlaybackMessage(didReceiveScriptMessage.body as? String)
    }

    // The page itself could not be fetched: the stream dropped, in this surface's vocabulary.
    // `@ObjCSignatureOverride` is required because the two WebKit selectors erase to the same Kotlin
    // signature; without it the compiler reads them as conflicting overloads.
    @ObjCSignatureOverride
    override fun webView(webView: WKWebView, didFailNavigation: WKNavigation?, withError: NSError) {
        onEvent(PlayerEvent.NetworkError)
    }

    @ObjCSignatureOverride
    override fun webView(
        webView: WKWebView,
        didFailProvisionalNavigation: WKNavigation?,
        withError: NSError,
    ) {
        onEvent(PlayerEvent.NetworkError)
    }

    /** The web content process died — retrying the same url will not bring it back (SPEC V5). */
    override fun webViewWebContentProcessDidTerminate(webView: WKWebView) {
        onEvent(PlayerEvent.DecodeError)
    }
}

/** The message channel the injected probe posts on, and the three words it may post. */
private const val PLAYBACK_CHANNEL = "mibosmartPlayback"
private const val PLAYING = "playing"
private const val ENDED = "ended"
private const val FAILED = "failed"

/**
 * A page that finished loading has not necessarily played anything, and "loaded" is the only thing
 * `WKNavigationDelegate` can tell us. This watches the page's `<video>` elements instead, so
 * `FirstFrame` means a frame — which is what leaves U1's timeout able to catch a page that shows
 * nothing on `ManagedMediaSource`-only iOS (SPEC V9).
 *
 * It is a file-level constant and not a companion field because a subclass of an Objective-C class
 * cannot have one.
 */
private val PLAYBACK_PROBE = """
    (function () {
      function post(name) {
        window.webkit.messageHandlers.$PLAYBACK_CHANNEL.postMessage(name);
      }
      function watch(video) {
        if (video.dataset.mibosmart) return;
        video.dataset.mibosmart = '1';
        if (video.readyState >= 2 && !video.paused) post('$PLAYING');
        video.addEventListener('playing', function () { post('$PLAYING'); });
        video.addEventListener('ended', function () { post('$ENDED'); });
        video.addEventListener('error', function () { post('$FAILED'); });
      }
      function scan() {
        Array.prototype.forEach.call(document.querySelectorAll('video'), watch);
      }
      scan();
      new MutationObserver(scan).observe(document.documentElement, {
        childList: true,
        subtree: true
      });
    })();
""".trimIndent()
