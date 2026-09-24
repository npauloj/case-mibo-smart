package io.github.npauloj.mibosmart.domain.lock

import kotlinx.datetime.LocalDateTime

/**
 * One line of a lock's opening history: the door opened, at this moment, this way (SPEC L9).
 * @property at when it happened, as **wall-clock time without a zone**.
 * @property kind how the door was opened, as a closed set plus an escape hatch — see
 * [OpeningKind].
 * @property actor who opened it, when the entry names anyone.
 */
data class OpeningEvent(
    val at: LocalDateTime,
    val kind: OpeningKind,
    val actor: String?,
)

/** How the door was opened (SPEC L9). */
sealed interface OpeningKind {

    /** `usuarioRemoto` — someone opened it from an app, and the entry usually names them. */
    data object Remote : OpeningKind

    /** `interno` — opened at the door itself, so there is nobody to name. */
    data object Local : OpeningKind

    /**
     * A `tipo` this app has never seen.
     * @property type the partner's own word, unchanged.
     */
    data class Unknown(val type: String) : OpeningKind
}
