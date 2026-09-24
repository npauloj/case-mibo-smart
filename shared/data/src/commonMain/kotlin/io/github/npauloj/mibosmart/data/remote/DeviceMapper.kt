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
 * The partner's device object as the domain's [Device] (ADR-004): every Portuguese field name
 * and every string-typed enumeration stops here.
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
    origin = if (origin.equals(ORIGIN_LINKED, ignoreCase = true)) {
        DeviceOrigin.Linked
    } else {
        DeviceOrigin.Shared
    },
    kind = DeviceClassifier.classify(model = model, isSubDevice = isSubDevice),
    parent = parentSerial?.takeIf { it.isNotBlank() }?.let(::DeviceId),
    productId = productId,
    parentProductId = parentProductId,
)

/** The `origem` the request carries for a chip (SPEC D4, `docs/api-contract.md` §3). */
internal val OriginFilter.wireValue: String
    get() = when (this) {
        OriginFilter.All -> "todos"
        OriginFilter.Linked -> "vinculados"
        OriginFilter.Shared -> "compartilhados"
    }

/** `ultimaVezOnline` as an instant, or null when it is absent or unreadable. */
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
