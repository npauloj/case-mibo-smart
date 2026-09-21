package io.github.npauloj.mibosmart.app

import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * The app's own scope: work that has to outlive the screen that started it (SPEC V8).
 *
 * `viewModelScope` is the wrong place for the only two things that belong here — closing a streaming
 * session and any other promise made to the partner — because it is already cancelled by the time a
 * ViewModel is cleared, so the request would never leave the device. The supervisor job means one
 * failed piece of cleanup does not take the next one down with it.
 *
 * It is injected rather than a global so a test can hand it a scheduler it controls; nothing in the
 * app ever cancels it.
 */
class AppCoroutineScope(
    override val coroutineContext: CoroutineContext = SupervisorJob() + Dispatchers.Default,
) : CoroutineScope
