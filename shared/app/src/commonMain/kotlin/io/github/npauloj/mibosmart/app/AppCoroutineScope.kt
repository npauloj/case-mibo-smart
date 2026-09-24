package io.github.npauloj.mibosmart.app

import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/** The app's own scope: work that has to outlive the screen that started it (SPEC V8). */
class AppCoroutineScope(
    override val coroutineContext: CoroutineContext = SupervisorJob() + Dispatchers.Default,
) : CoroutineScope
