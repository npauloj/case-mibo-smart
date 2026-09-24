package io.github.npauloj.mibosmart.app.devices

import io.github.npauloj.mibosmart.domain.device.Device
import io.github.npauloj.mibosmart.domain.device.DeviceId
import io.github.npauloj.mibosmart.domain.device.DeviceKind
import io.github.npauloj.mibosmart.domain.device.DeviceListPreferences
import io.github.npauloj.mibosmart.domain.device.DeviceOrigin
import io.github.npauloj.mibosmart.domain.device.DeviceStatus
import io.github.npauloj.mibosmart.domain.device.OriginFilter
import kotlin.time.Instant

/** The use case as every test builds it: a fake partner, and a chip nobody has chosen yet. */
internal fun listDevices(
    repository: FakeDeviceRepository,
    preferences: DeviceListPreferences = FakeDeviceListPreferences(),
) = ListDevices(deviceRepository = repository, preferences = preferences)

/**
 * Page 1 of [origin], with the stored page allowed to answer for it — the call the list makes
 * when it opens (SPEC D1, D8). Tests about paging state the page and the cache explicitly.
 */
internal suspend fun ListDevices.firstPage(origin: OriginFilter = OriginFilter.All) =
    this(origin = origin, page = 1, mayUseCache = true)

/**
 * Domain devices for the app-side tests, with **placeholder** serials only — the test account's
 * `ns` values never enter a versioned file (ADR-008).
 * @param model the partner's model code.
 */
internal fun device(
    name: String,
    id: String = "PLACEHOLDER-$name",
    kind: DeviceKind = DeviceKind.Camera,
    isOnline: Boolean = true,
    lastSeen: Instant? = null,
    parent: DeviceId? = null,
    origin: DeviceOrigin = DeviceOrigin.Linked,
    productId: String = PRODUCT_ID,
    parentProductId: String? = null,
    model: String = "irrelevant",
) = Device(
    id = DeviceId(id),
    name = name,
    model = model,
    status = if (isOnline) DeviceStatus.Online else DeviceStatus.Offline,
    lastSeen = lastSeen,
    origin = origin,
    kind = kind,
    parent = parent,
    productId = productId,
    parentProductId = parentProductId,
)

/**
 * A lock and the hub it hangs off — every part of the address is on the **lock's** row
 * (`docs/api-contract.md` §3), and the hub row is there only so the list has one to name (SPEC
 * D6).
 * @param lockProductId the lock's own `idProduto`; blank is the case where the partner sent
 * none.
 * @param hubProductId the hub's `idProduto`, which the partner puts on the lock's row as
 * `idProdutoDispositivoPai`; null is a row that arrived without it and blank is one that
 * arrived empty — neither can be addressed.
 * @param withHub false leaves the hub row out, which is a lock on page 1 whose hub is on page 2
 * (SPEC D2) — and which changes nothing about the address.
 */
internal fun lockAndHub(
    lockProductId: String = LOCK_PRODUCT_ID,
    hubProductId: String? = HUB_PRODUCT_ID,
    withHub: Boolean = true,
): List<Device> = listOfNotNull(
    device(
        name = LOCK_NAME,
        id = LOCK_NAMESPACE,
        kind = DeviceKind.Lock,
        parent = DeviceId(HUB_NAMESPACE),
        productId = lockProductId,
        parentProductId = hubProductId,
    ),
    device(
        name = HUB_NAME,
        id = HUB_NAMESPACE,
        kind = DeviceKind.Hub,
        productId = hubProductId.orEmpty(),
    ).takeIf { withHub },
)

/**
 * The shapes `docs/api-contract.md` §5 uses, and nothing from the test account: a real `ns` or
 * `idProduto` opens a real door and never enters a versioned file (ADR-008, CLAUDE.md).
 */
internal const val LOCK_NAMESPACE = "<lock-ns>"
internal const val HUB_NAMESPACE = "<hub-ns>"
internal const val LOCK_PRODUCT_ID = "<lock-idProduto>"
internal const val HUB_PRODUCT_ID = "<hub-idProduto>"
internal const val LOCK_NAME = "MFR 1001"
internal const val HUB_NAME = "MCA 1002"

/** What a device that is not part of a lock address carries — present, and not worth naming. */
private const val PRODUCT_ID = "<idProduto>"
