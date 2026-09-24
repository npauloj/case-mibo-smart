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

/** The partner's own player page in a `WKWebView` (ADR-005, SPEC V10). */
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

    /** Points the page at one session's monitor url. */
    fun load(monitorUrl: String?) {
        val target = monitorUrl?.let { NSURL.URLWithString(it) }
        if (target == null) {
            onEvent(PlayerEvent.DecodeError)
            return
        }
        loadedUrl = monitorUrl
        webView.loadRequest(NSURLRequest.requestWithURL(target))
    }

    /** Stops the page, unhooks it and drops its media. */
    fun release() {
        webView.navigationDelegate = null
        contentController.removeScriptMessageHandlerForName(PLAYBACK_CHANNEL)
        contentController.removeAllUserScripts()
        webView.stopLoading()
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
 * A page that finished loading has not necessarily played anything, and "loaded" is the only
 * thing `WKNavigationDelegate` can tell us.
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
