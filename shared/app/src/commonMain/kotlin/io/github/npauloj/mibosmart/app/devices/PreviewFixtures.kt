package io.github.npauloj.mibosmart.app.devices

import io.github.npauloj.mibosmart.domain.device.Device
import io.github.npauloj.mibosmart.domain.device.DeviceClassifier
import io.github.npauloj.mibosmart.domain.device.DeviceId
import io.github.npauloj.mibosmart.domain.device.DeviceOrigin
import io.github.npauloj.mibosmart.domain.device.DeviceStatus
import io.github.npauloj.mibosmart.domain.device.orderedForList
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant

/**
 * Devices for the previews, with **placeholder** serials only — the test account's `ns` and
 * `idProduto` values never reach a versioned file (ADR-008).
 *
 * They are domain [Device]s put through the real classifier, ordering and [toRows], not hand-written
 * rows: a preview built from invented rows can show a layout the app can never produce, and the two
 * drift apart silently. This way the previews are also visual evidence of SPEC D5, U3 and U8.
 */
internal object PreviewFixtures {

    /** Declared before the page below: an object initialises its properties in source order. */
    private val NOW = Instant.parse("2026-09-21T12:00:00Z")

    /** A full page: 20 rows, the size SPEC D1 asks for, so the preview shows real scroll behaviour. */
    val fullPage: List<DeviceRow> = buildList {
        add(lock(1, "MFR 1001"))
        add(device(2, "MCA 1002", HUB_MODEL, id = HUB_ID, productId = HUB_PRODUCT_ID))
        add(device(3, "iM7 3M Full Color", "iM7-FC"))
        // SPEC U8 and D6 need a name that cannot fit on one line, to prove the row truncates.
        add(device(4, "Câmera da varanda dos fundos com um nome longo demais para caber na linha", "iM7-FC"))
        // SPEC U3: one device last seen days ago, one the partner has never seen online.
        add(device(5, "iM3-C", "iM3-C", isOnline = false, lastSeen = NOW - 12.days))
        add(lock(6, "MFR 2040", model = "IOT-MFR2040-IB", isOnline = false))
        add(device(7, "MSM 1001", "MSM 1001", isOnline = false, lastSeen = NOW - 3.hours))
        repeat(FULL_PAGE - size) { index ->
            add(device(index + FIRST_FILLER, "MTU 1001 ${index + 1}", "MTU 1001", isOnline = index % 2 == 0))
        }
    }.orderedForList().toRows(NOW)

    /**
     * SPEC D2 and D6: the list is paged, so a lock can be on screen while the hub that addresses it is
     * not — and then the row is shown, says why it opens nothing, and takes no tap.
     *
     * The hub is simply left out rather than faked absent: the rule under test is "assembled only from
     * rows the app has loaded", and this page has not loaded one.
     */
    val lockWithoutHub: List<DeviceRow> = listOf(
        lock(1, "MFR 1001"),
        device(3, "iM7 3M Full Color", "iM7-FC"),
        device(5, "iM3-C", "iM3-C", isOnline = false, lastSeen = NOW - 12.days),
    ).orderedForList().toRows(NOW)

    /** A lock always hangs off [HUB_ID] and always has both product ids — the addressable shape. */
    private fun lock(index: Int, name: String, model: String = "IOT-MFR1001-IB", isOnline: Boolean = true) =
        device(
            index = index,
            name = name,
            model = model,
            isOnline = isOnline,
            isSubDevice = true,
            parent = HUB_ID,
            productId = LOCK_PRODUCT_ID,
        )

    private fun device(
        index: Int,
        name: String,
        model: String,
        id: String = serial(index),
        isOnline: Boolean = true,
        isSubDevice: Boolean = false,
        parent: String? = null,
        lastSeen: Instant? = null,
        productId: String = "",
    ) = Device(
        id = DeviceId(id),
        name = name,
        model = model,
        status = if (isOnline) DeviceStatus.Online else DeviceStatus.Offline,
        lastSeen = lastSeen,
        origin = if (index % 3 == 0) DeviceOrigin.Shared else DeviceOrigin.Linked,
        kind = DeviceClassifier.classify(model, isSubDevice),
        parent = parent?.let(::DeviceId),
        productId = productId,
    )

    private fun serial(index: Int) = "PLACEHOLDER-NS-${index.toString().padStart(4, '0')}"

    private const val HUB_ID = "PLACEHOLDER-HUB-NS"
    private const val HUB_MODEL = "IOT-ZG2-IB"
    private const val HUB_PRODUCT_ID = "<hub-idProduto>"
    private const val LOCK_PRODUCT_ID = "<lock-idProduto>"
    private const val FULL_PAGE = 20
    private const val FIRST_FILLER = 8
}
