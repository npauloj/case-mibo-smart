package io.github.npauloj.mibosmart.app.devices

import io.github.npauloj.mibosmart.domain.device.Device
import io.github.npauloj.mibosmart.domain.device.DeviceKind
import io.github.npauloj.mibosmart.domain.device.DeviceOrigin
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant

/** One row of the device list, ready to render: no domain decision is left for the composable. */
data class DeviceRow(
    val id: String,
    val name: String,
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
     * Why this row opens nothing although its kind normally would, or null when it does (SPEC D6).
     *
     * Only a lock can carry one, and only because the address is assembled from loaded rows rather
     * than fetched: the row stays on screen and explains itself instead of taking a tap it cannot
     * honour (SPEC U6).
     */
    val unavailable: LockAddressing.Unavailable?,
)

/**
 * "Visto pela última vez há X" as data rather than as a sentence (SPEC U3).
 *
 * The composable picks the string resource, so the rule — which unit, rounded how — is unit-tested
 * without a resource loader, and pt-BR and en render the same numbers.
 */
sealed interface LastSeen {

    /** The partner has no `ultimaVezOnline` for this device: it has never been seen online (U3). */
    data object Never : LastSeen

    data class Ago(val amount: Int, val unit: ElapsedUnit) : LastSeen
}

enum class ElapsedUnit { Minutes, Hours, Days }

/**
 * How long ago something happened, rounded to the largest unit that still reads as a number.
 *
 * Two sentences in this screen count time — "visto pela última vez há X" on a row (SPEC U3) and
 * "última atualização há X" on the stale banner (SPEC D8) — and they round the same way because
 * they round here. The composable picks the words.
 */
data class Elapsed(val amount: Int, val unit: ElapsedUnit)

/**
 * 45 min, 3 h, 12 d.
 *
 * "há 0 min" is not a sentence, so anything more recent than a minute is reported as one; a clock
 * that runs ahead of ours would otherwise report a negative age, so the duration is clamped by the
 * callers before it gets here.
 */
internal fun Duration.rounded(): Elapsed = when {
    this < 1.hours -> Elapsed(inWholeMinutes.coerceAtLeast(1).toInt(), ElapsedUnit.Minutes)
    this < 1.days -> Elapsed(inWholeHours.toInt(), ElapsedUnit.Hours)
    else -> Elapsed(inWholeDays.toInt(), ElapsedUnit.Days)
}

/** The age of [this] as of [now], never negative — a device or a cache cannot be from the future. */
internal fun Instant.ageAt(now: Instant): Elapsed = (now - this).coerceAtLeast(Duration.ZERO).rounded()

/** Domain devices as rows (SPEC D6, U3). [now] is a parameter so the elapsed text is testable. */
fun List<Device>.toRows(now: Instant): List<DeviceRow> {
    val namesById = associate { it.id to it.name }
    return map { device ->
        // The same rule the tap will use (SPEC D6): a lock the list cannot address is drawn as one
        // that says why, never as one that opens a screen with nothing to talk to.
        val unavailable = if (device.kind == DeviceKind.Lock) {
            addressing(device) as? LockAddressing.Unavailable
        } else {
            null
        }
        DeviceRow(
            id = device.id.value,
            name = device.name,
            model = device.model,
            kind = device.kind,
            isOnline = device.isOnline,
            origin = device.origin,
            // Only when the hub is on the same page: page 2 is D-02's, and inventing a name for a hub
            // this load never saw would be worse than the row saying nothing about its parent.
            parentName = device.parent?.let(namesById::get),
            lastSeen = if (device.isOnline) null else device.lastSeen.toLastSeen(now),
            isActionable = device.isActionable && unavailable == null,
            unavailable = unavailable,
        )
    }
}

/** A device offline for a week does not need minutes — [rounded] decides which unit reads best. */
private fun Instant?.toLastSeen(now: Instant): LastSeen =
    (this ?: return LastSeen.Never).ageAt(now).let { LastSeen.Ago(it.amount, it.unit) }
