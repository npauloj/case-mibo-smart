package io.github.npauloj.mibosmart.app

import io.github.npauloj.mibosmart.app.session.FakeRefusedRequests
import io.github.npauloj.mibosmart.app.session.SessionStartup
import io.github.npauloj.mibosmart.domain.session.RefusedRequests
import io.github.npauloj.mibosmart.domain.session.SessionGuard
import io.github.npauloj.mibosmart.domain.session.SessionStore
import kotlin.time.Clock

/** The routing ViewModel over one store, which is how the app really wires it. */
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
