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
    /** Cameras and locks open a screen; hubs and the rest do not (SPEC D6). */
    val isActionable: Boolean,
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

/** Domain devices as rows (SPEC D6, U3). [now] is a parameter so the elapsed text is testable. */
fun List<Device>.toRows(now: Instant): List<DeviceRow> {
    val namesById = associate { it.id to it.name }
    return map { device ->
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
            isActionable = device.isActionable,
        )
    }
}

/**
 * Rounds down to the largest unit that still reads as a number: 45 min, 3 h, 12 d.
 *
 * A device offline for a week does not need minutes, and "há 0 min" is not a sentence — anything more
 * recent than a minute is reported as one.
 */
private fun Instant?.toLastSeen(now: Instant): LastSeen {
    // A device clock ahead of ours would otherwise report a negative age: clamp, never subtract.
    val elapsed = (now - (this ?: return LastSeen.Never)).coerceAtLeast(Duration.ZERO)
    return when {
        elapsed < 1.hours -> LastSeen.Ago(elapsed.inWholeMinutes.coerceAtLeast(1).toInt(), ElapsedUnit.Minutes)
        elapsed < 1.days -> LastSeen.Ago(elapsed.inWholeHours.toInt(), ElapsedUnit.Hours)
        else -> LastSeen.Ago(elapsed.inWholeDays.toInt(), ElapsedUnit.Days)
    }
}
