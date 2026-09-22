package io.github.npauloj.mibosmart.data.remote

import io.github.npauloj.mibosmart.domain.error.SmartHomeException
import io.github.npauloj.mibosmart.domain.lock.OpeningEvent
import io.github.npauloj.mibosmart.domain.lock.OpeningKind
import kotlinx.datetime.LocalDateTime

/**
 * A `historico-abertura` entry as the domain's [OpeningEvent] (ADR-004): `tempoLocal`, `nome` and
 * `tipo` — and the two Portuguese words the partner classifies openings with — stop here.
 *
 * The unknown `tipo` is carried across rather than rejected. SPEC L9 marks the list of types
 * `[ASSUMED]` — only `usuarioRemoto` and `interno` were ever observed — so a word this mapper does
 * not recognise is a gap in what was probed, not a broken payload.
 */
internal fun LockOpeningEventDto.toOpeningEvent(): OpeningEvent = OpeningEvent(
    at = localTime.toLocalWallClock(),
    kind = type.toOpeningKind(),
    // "" is what the partner sends for an opening nobody performed; the domain says that with null,
    // so the screen is never asked to render an empty name (SPEC U4).
    actor = name?.trim()?.takeIf { it.isNotEmpty() },
)

private fun String.toOpeningKind(): OpeningKind = when (val type = trim()) {
    REMOTE_USER -> OpeningKind.Remote
    INTERNAL -> OpeningKind.Local
    else -> OpeningKind.Unknown(type)
}

/**
 * `20260918T102735` as the wall-clock time it is (SPEC L9).
 *
 * The device list's `ultimaVezOnline` is the same compact shape with a `Z` and is parsed by
 * [toDevice] into an instant; this one has no suffix and none may be assumed, so it stays a
 * [LocalDateTime] and the zone is chosen above (`docs/api-contract.md` §5).
 *
 * A timestamp that is not this shape fails the whole read rather than costing one row, following
 * `readVolume`'s precedent for a documented field that came back undocumented (SPEC E3): the time is
 * the entry — a history line that cannot say when is not a line worth showing.
 */
private fun String.toLocalWallClock(): LocalDateTime {
    val match = COMPACT_LOCAL.matchEntire(trim())
        ?: throw SmartHomeException.UnexpectedResponse("a history entry's `tempoLocal` is not yyyyMMddTHHmmss")
    val (year, month, day, hour, minute, second) = match.destructured
    return try {
        LocalDateTime(year.toInt(), month.toInt(), day.toInt(), hour.toInt(), minute.toInt(), second.toInt())
    } catch (outOfRange: IllegalArgumentException) {
        throw SmartHomeException.UnexpectedResponse("a history entry's `tempoLocal` is not a date", outOfRange)
    }
}

private val COMPACT_LOCAL = Regex("""^(\d{4})(\d{2})(\d{2})T(\d{2})(\d{2})(\d{2})$""")
private const val REMOTE_USER = "usuarioRemoto"
private const val INTERNAL = "interno"
