package io.github.npauloj.mibosmart.app.devices

import io.github.npauloj.mibosmart.domain.device.Device
import io.github.npauloj.mibosmart.domain.device.DeviceKind
import io.github.npauloj.mibosmart.domain.device.DeviceOrigin
import io.github.npauloj.mibosmart.domain.device.ModelCatalog
import io.github.npauloj.mibosmart.domain.device.RawCodes
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant

/** One row of the device list, ready to render: no domain decision is left for the composable. */
data class DeviceRow(
    val id: String,
    val name: String,
    /** What the row prints for the model (SPEC D5, ADR-007). */
    val model: String,
    val kind: DeviceKind,
    val isOnline: Boolean,
    val origin: DeviceOrigin,
    /** The hub this device hangs from, by name, when the page also listed it (SPEC D6). */
    val parentName: String?,
    /** Only for an offline device: when the partner last saw it (SPEC U3). */
    val lastSeen: LastSeen?,
    /** Cameras and addressable locks open a screen; hubs and the rest do not (SPEC D6). */
    val isActionable: Boolean,
    /**
     * Why this row opens nothing although its kind normally would, or null when it does (SPEC
     * D6).
     */
    val unavailable: LockAddressing.Unavailable?,
)

/** "Visto pela última vez há X" as data rather than as a sentence (SPEC U3). */
sealed interface LastSeen {

    /** The partner has no `ultimaVezOnline` for this device: it has never been seen online (U3). */
    data object Never : LastSeen

    data class Ago(val amount: Int, val unit: ElapsedUnit) : LastSeen
}

enum class ElapsedUnit { Minutes, Hours, Days }

/** How long ago something happened, rounded to the largest unit that still reads as a number. */
data class Elapsed(val amount: Int, val unit: ElapsedUnit)

/** 45 min, 3 h, 12 d. */
internal fun Duration.rounded(): Elapsed = when {
    this < 1.hours -> Elapsed(inWholeMinutes.coerceAtLeast(1).toInt(), ElapsedUnit.Minutes)
    this < 1.days -> Elapsed(inWholeHours.toInt(), ElapsedUnit.Hours)
    else -> Elapsed(inWholeDays.toInt(), ElapsedUnit.Days)
}

/** The age of [this] as of [now], never negative — a device or a cache cannot be from the future. */
internal fun Instant.ageAt(now: Instant): Elapsed = (now - this).coerceAtLeast(Duration.ZERO).rounded()

/**
 * Domain devices as rows (SPEC D5, D6, U3).
 * @param catalog the partner's words for its own model codes (ADR-007).
 */
fun List<Device>.toRows(now: Instant, catalog: ModelCatalog = RawCodes): List<DeviceRow> {
    val namesById = associate { it.id to it.name }
    return map { device ->
        val unavailable = if (device.kind == DeviceKind.Lock) {
            addressing(device) as? LockAddressing.Unavailable
        } else {
            null
        }
        DeviceRow(
            id = device.id.value,
            name = device.name,
            model = catalog.nameOf(device.model),
            kind = device.kind,
            isOnline = device.isOnline,
            origin = device.origin,
            parentName = device.parent?.let(namesById::get),
            lastSeen = if (device.isOnline) null else device.lastSeen.toLastSeen(now),
            isActionable = device.isActionable && unavailable == null,
            unavailable = unavailable,
        )
    }
}

/**
 * What the catalogue calls [code], or [code] itself whenever the catalogue cannot say
 * (ADR-007).
 */
private fun ModelCatalog.nameOf(code: String): String =
    try {
        label(code)
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (_: Throwable) {
        code
    }

/** A device offline for a week does not need minutes — [rounded] decides which unit reads best. */
private fun Instant?.toLastSeen(now: Instant): LastSeen =
    (this ?: return LastSeen.Never).ageAt(now).let { LastSeen.Ago(it.amount, it.unit) }
