package io.github.npauloj.mibosmart.data.remote

import io.github.npauloj.mibosmart.domain.error.SmartHomeException
import io.github.npauloj.mibosmart.domain.lock.OpeningEvent
import io.github.npauloj.mibosmart.domain.lock.OpeningKind
import kotlinx.datetime.LocalDateTime

/**
 * A `historico-abertura` entry as the domain's [OpeningEvent] (ADR-004): `tempoLocal`, `nome`
 * and `tipo` — and the two Portuguese words the partner classifies openings with — stop here.
 */
internal fun LockOpeningEventDto.toOpeningEvent(): OpeningEvent = OpeningEvent(
    at = localTime.toLocalWallClock(),
    kind = type.toOpeningKind(),
    actor = name?.trim()?.takeIf { it.isNotEmpty() },
)

private fun String.toOpeningKind(): OpeningKind = when (val type = trim()) {
    REMOTE_USER -> OpeningKind.Remote
    INTERNAL -> OpeningKind.Local
    else -> OpeningKind.Unknown(type)
}

/** `20260918T102735` as the wall-clock time it is (SPEC L9). */
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
