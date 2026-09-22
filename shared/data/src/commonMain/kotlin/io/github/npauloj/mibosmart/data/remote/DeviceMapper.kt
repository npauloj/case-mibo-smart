package io.github.npauloj.mibosmart.data.remote

import io.github.npauloj.mibosmart.domain.device.Device
import io.github.npauloj.mibosmart.domain.device.DeviceClassifier
import io.github.npauloj.mibosmart.domain.device.DeviceId
import io.github.npauloj.mibosmart.domain.device.DeviceOrigin
import io.github.npauloj.mibosmart.domain.device.DeviceStatus
import io.github.npauloj.mibosmart.domain.device.OriginFilter
import kotlin.time.Instant
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant

/**
 * The partner's device object as the domain's [Device] (ADR-004): every Portuguese field name and
 * every string-typed enumeration stops here.
 *
 * The identity stays the plain `ns`. The composite address a lock endpoint needs
 * (`<lock-ns>_<hub-ns>_<hub-idProduto>`, `docs/api-contract.md` §5) is built by the slice that calls
 * those endpoints: encoding it here would put a lock detail in the identity of every camera and hub.
 * What this mapper does owe that slice is the raw `idProduto` of every device — the hub's is one of
 * the three parts of the namespace, and neither is recoverable from a later call the budget would pay
 * for (ADR-006).
 */
internal fun DeviceDto.toDevice(): Device = Device(
    id = DeviceId(serial),
    name = name,
    model = model,
    status = if (status.equals(STATUS_ONLINE, ignoreCase = true)) {
        DeviceStatus.Online
    } else {
        DeviceStatus.Offline
    },
    lastSeen = lastSeen.toCompactInstantOrNull(),
    // The request filter is plural ("vinculados") and the device field singular ("vinculado") — one of
    // the contract's own contradictions (api-contract §7.8). Anything not linked is shared with us.
    origin = if (origin.equals(ORIGIN_LINKED, ignoreCase = true)) {
        DeviceOrigin.Linked
    } else {
        DeviceOrigin.Shared
    },
    kind = DeviceClassifier.classify(model = model, isSubDevice = isSubDevice),
    parent = parentSerial?.takeIf { it.isNotBlank() }?.let(::DeviceId),
    // Carried verbatim, blank included: "the partner sent none" is a fact the lock edge acts on
    // (SPEC L1), and normalising it to null here would only move the same decision one layer up.
    productId = productId,
)

/**
 * The `origem` the request carries for a chip (SPEC D4, `docs/api-contract.md` §3).
 *
 * The three Portuguese words stop here, like every other wire vocabulary (ADR-004). They are
 * **plural** on the way out and singular on the way back on each device — one of the contract's own
 * contradictions, and the reason this mapping is not shared with [toDevice]'s.
 */
internal val OriginFilter.wireValue: String
    get() = when (this) {
        OriginFilter.All -> "todos"
        OriginFilter.Linked -> "vinculados"
        OriginFilter.Shared -> "compartilhados"
    }

/**
 * `ultimaVezOnline` as an instant, or null when it is absent or unreadable.
 *
 * The partner sends compact ISO-8601 in UTC (`20260918T132704Z`), which `Instant.parse` rejects — it
 * wants the separators. A timestamp is decoration on a row, so a shape this function does not know
 * costs the "visto pela última vez" line and nothing else: the device still lists (SPEC U3, E3).
 */
private fun String?.toCompactInstantOrNull(): Instant? {
    val match = COMPACT_UTC.matchEntire(this?.trim().orEmpty()) ?: return null
    val (year, month, day, hour, minute, second) = match.destructured
    return try {
        LocalDateTime(year.toInt(), month.toInt(), day.toInt(), hour.toInt(), minute.toInt(), second.toInt())
            .toInstant(TimeZone.UTC)
    } catch (_: IllegalArgumentException) {
        null
    }
}

private val COMPACT_UTC = Regex("""^(\d{4})(\d{2})(\d{2})T(\d{2})(\d{2})(\d{2})Z$""")
private const val STATUS_ONLINE = "online"
private const val ORIGIN_LINKED = "vinculado"
