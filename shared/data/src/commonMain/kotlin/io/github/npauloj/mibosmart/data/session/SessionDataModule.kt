package io.github.npauloj.mibosmart.data.session

import io.github.npauloj.mibosmart.data.platform.vault.secureTokenStore
import io.github.npauloj.mibosmart.domain.session.SessionRepository
import io.github.npauloj.mibosmart.domain.session.SessionStore
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * The session feature's partner bindings, beside the code they wire (ADR-014).
 *
 * `:shared:data`'s aggregate lists this module in `di/DataModule.kt`; nothing else references it.
 */
internal val sessionDataModule: Module = module {
    single<SessionRepository> { SmartHomeSessionRepository(get()) }
    single<SessionStore> { VaultSessionStore(secureTokenStore()) }
}
