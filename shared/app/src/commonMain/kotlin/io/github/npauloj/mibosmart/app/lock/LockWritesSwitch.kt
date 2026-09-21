package io.github.npauloj.mibosmart.app.lock

import kotlin.jvm.JvmInline

/**
 * The lock-writes kill switch: `local.properties` → `BuildConfig` → here (SPEC L2, L7).
 *
 * It mirrors `LiveVideoSwitch`, with a stronger default and a different reason. The camera switch
 * protects a quota; this one protects a **physical door**: every write the lock screen can make ends
 * in a real building, on an account several people share. So it ships **off**, and the case can be
 * demonstrated end to end — reads, states, previews — without a single write reaching hardware until
 * someone opts in.
 *
 * Off means the writes short-circuit **before any request**: [ChangeVolume] and [EnableRemoteOpen]
 * check it first, the screen disables the volume selector and turns "Habilitar abertura remota" into
 * an explanation of what it would do. A disabled control is an affordance, not a kill switch — the
 * guarantee has to hold even if the screen is wrong.
 */
@JvmInline
value class LockWritesSwitch(val isOn: Boolean)
