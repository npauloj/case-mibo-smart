package io.github.npauloj.mibosmart.app

import io.github.npauloj.mibosmart.app.session.FakeRefusedRequests
import io.github.npauloj.mibosmart.app.session.SessionStartup
import io.github.npauloj.mibosmart.domain.session.RefusedRequests
import io.github.npauloj.mibosmart.domain.session.SessionGuard
import io.github.npauloj.mibosmart.domain.session.SessionStore
import kotlin.time.Clock

/**
 * The routing ViewModel over one store, which is how the app really wires it.
 *
 * Startup and the guard share the same [SessionStore] on purpose: the bug this shape prevents is a
 * guard that clears one vault while the app keeps routing from another (SPEC S5, S6).
 */
internal fun appViewModel(
    store: SessionStore,
    clock: Clock,
    refusedRequests: RefusedRequests = FakeRefusedRequests(),
): AppViewModel = AppViewModel(
    sessionStartup = SessionStartup(store),
    sessionGuard = SessionGuard(store),
    refusedRequests = refusedRequests,
    clock = clock,
)
