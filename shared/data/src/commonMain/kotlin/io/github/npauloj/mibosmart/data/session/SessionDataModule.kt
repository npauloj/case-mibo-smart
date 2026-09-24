package io.github.npauloj.mibosmart.data.session

import io.github.npauloj.mibosmart.data.platform.vault.secureTokenStore
import io.github.npauloj.mibosmart.data.remote.smartHomeJson
import io.github.npauloj.mibosmart.domain.session.RefusedRequests
import io.github.npauloj.mibosmart.domain.session.SessionRepository
import io.github.npauloj.mibosmart.domain.session.SessionStore
import org.koin.core.module.Module
import org.koin.dsl.module

/** The session feature's partner bindings, beside the code they wire (ADR-014). */
internal val sessionDataModule: Module = module {
    single<SessionRepository> { SmartHomeSessionRepository(api = get(), json = smartHomeJson) }
    single<SessionStore> { VaultSessionStore(secureTokenStore()) }
    single<RefusedRequests> { SessionRefusals() }
}
