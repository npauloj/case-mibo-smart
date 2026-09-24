package io.github.npauloj.mibosmart.app.lock

import kotlin.jvm.JvmInline

/** The lock-writes kill switch: `local.properties` → `BuildConfig` → here (SPEC L2, L7). */
@JvmInline
value class LockWritesSwitch(val isOn: Boolean)
