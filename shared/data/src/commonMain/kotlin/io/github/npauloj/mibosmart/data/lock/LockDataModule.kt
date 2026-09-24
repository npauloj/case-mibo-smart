package io.github.npauloj.mibosmart.data.lock

import io.github.npauloj.mibosmart.data.remote.smartHomeJson
import io.github.npauloj.mibosmart.domain.lock.LockRepository
import org.koin.core.module.Module
import org.koin.dsl.module

/** The lock feature's partner bindings, beside the code they wire (ADR-014). */
internal val lockDataModule: Module = module {
    single<LockRepository> { SmartHomeLockRepository(api = get(), sessionStore = get(), json = smartHomeJson) }
}
