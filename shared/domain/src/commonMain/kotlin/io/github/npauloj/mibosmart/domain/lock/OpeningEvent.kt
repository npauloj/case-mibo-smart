package io.github.npauloj.mibosmart.domain.lock

import kotlinx.datetime.LocalDateTime

/**
 * One line of a lock's opening history: the door opened, at this moment, this way (SPEC L9).
 *
 * @property at when it happened, as **wall-clock time without a zone**. The partner sends
 *   `tempoLocal` with no offset and no `Z` (`docs/api-contract.md` §5), so a [LocalDateTime] is the
 *   honest type: turning it into an instant would mean inventing a zone the partner never stated.
 *   Whoever needs an age picks the zone and says so.
 * @property kind how the door was opened, as a closed set plus an escape hatch — see [OpeningKind].
 * @property actor who opened it, when the entry names anyone. It is `null` rather than `""` for the
 *   entries that carry no name, so the screen decides what to say instead of rendering a blank
 *   (SPEC U4). It is personal data: it is shown inside the authenticated session and never logged.
 */
data class OpeningEvent(
    val at: LocalDateTime,
    val kind: OpeningKind,
    val actor: String?,
)

/**
 * How the door was opened (SPEC L9).
 *
 * Only `usuarioRemoto` and `interno` were ever observed, and the SPEC marks the list `[ASSUMED]`:
 * the partner has never published the full set. [Unknown] is what that assumption costs — a type
 * nobody has seen is carried through and displayed, never filtered out and never folded into one of
 * the two known ones. A history that silently drops a way of opening a door is worse than a history
 * with a word in it the app does not recognise.
 */
sealed interface OpeningKind {

    /** `usuarioRemoto` — someone opened it from an app, and the entry usually names them. */
    data object Remote : OpeningKind

    /** `interno` — opened at the door itself, so there is nobody to name. */
    data object Local : OpeningKind

    /**
     * A `tipo` this app has never seen.
     *
     * @property type the partner's own word, unchanged. It reaches the screen because there is
     *   nothing better to show, which is the point of SPEC L9's `[ASSUMED]`.
     */
    data class Unknown(val type: String) : OpeningKind
}
