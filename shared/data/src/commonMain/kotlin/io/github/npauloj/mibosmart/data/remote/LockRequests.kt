package io.github.npauloj.mibosmart.data.remote

import io.github.npauloj.mibosmart.domain.lock.LockAddress
import io.github.npauloj.mibosmart.domain.lock.LockCommand
import io.github.npauloj.mibosmart.domain.lock.VolumeLevel
import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** How a [LockAddress] becomes a partner request (`docs/api-contract.md` §5). */
internal object LockRequests {

    /** The two fields every lock read sends: the composite namespace and the lock's own product id. */
    fun read(address: LockAddress): LockReadRequestDto = LockReadRequestDto(
        namespace = compositeNamespace(address),
        productId = address.lockProductId,
    )

    /** The volume read, which sends the product id under both names (SPEC L8). */
    fun volume(address: LockAddress): LockVolumeRequestDto = LockVolumeRequestDto(
        namespace = compositeNamespace(address),
        productId = address.lockProductId,
        productIdAsRequired = address.lockProductId,
    )

    /** The volume write: the same address, plus the 0..3 the partner speaks (SPEC L7). */
    fun changeVolume(address: LockAddress, volume: VolumeLevel): LockChangeVolumeRequestDto =
        LockChangeVolumeRequestDto(
            namespace = compositeNamespace(address),
            productId = address.lockProductId,
            volume = volume.level,
        )

    /** The command itself: the same address, plus the state the door is asked for (SPEC L3). */
    fun command(address: LockAddress, command: LockCommand): LockCommandRequestDto =
        LockCommandRequestDto(
            namespace = compositeNamespace(address),
            productId = address.lockProductId,
            isOpen = command.opensTheDoor,
        )

    /** The one request that changes a door's security posture (SPEC L2). */
    fun enableRemoteOpen(address: LockAddress): LockEnableRemoteOpenRequestDto =
        LockEnableRemoteOpenRequestDto(
            namespace = compositeNamespace(address),
            productId = address.lockProductId,
        )

    /** The opening history: the only lock call that does **not** carry `idProduto` (SPEC L9). */
    fun openingHistory(address: LockAddress, entries: Int): LockHistoryRequestDto =
        LockHistoryRequestDto(namespace = compositeNamespace(address), quantity = entries)

    /**
     * `<lockNs>_<hubNs>_<hubIdProduto>` — the lock addressed as a sub-device of its hub (SPEC
     * L1).
     */
    private fun compositeNamespace(address: LockAddress): String =
        listOf(address.lock.value, address.hub.value, address.hubProductId).joinToString(SEPARATOR)

    private const val SEPARATOR = "_"
}

/** `status-abertura` and `status-abrir-remoto`: both take the same two fields. */
@Serializable
internal data class LockReadRequestDto(
    @SerialName("ns") val namespace: String,
    @SerialName("idProduto") val productId: String,
)

/**
 * `volume`, whose contract bug is quarantined here (`docs/api-contract.md` §5 and §7.4, SPEC
 * L8).
 */
@Serializable
internal data class LockVolumeRequestDto(
    @SerialName("ns") val namespace: String,
    @SerialName("idProduto") val productId: String,
    @SerialName("productId") val productIdAsRequired: String,
)

/** `mudar-volume`: `{ ns, idProduto, volume: 0..3 }` (`docs/api-contract.md` §5). */
@Serializable
internal data class LockChangeVolumeRequestDto(
    @SerialName("ns") val namespace: String,
    @SerialName("idProduto") val productId: String,
    @SerialName("volume") val volume: Int,
)

/** `controle-fechadura`: `{ ns, idProduto, aberto: true|false }` (`docs/api-contract.md` §5). */
@Serializable
internal data class LockCommandRequestDto(
    @SerialName("ns") val namespace: String,
    @SerialName("idProduto") val productId: String,
    @SerialName("aberto") val isOpen: Boolean,
)

/** `habilitar-abrir-remoto`: `{ ns, idProduto, habilitar: true }` (`docs/api-contract.md` §5). */
@OptIn(ExperimentalSerializationApi::class)
@Serializable
internal data class LockEnableRemoteOpenRequestDto(
    @SerialName("ns") val namespace: String,
    @SerialName("idProduto") val productId: String,
) {
    @EncodeDefault(EncodeDefault.Mode.ALWAYS)
    @SerialName("habilitar")
    val enable: Boolean = true
}

/**
 * `historico-abertura`: `{ ns, quantidade }` — **no `idProduto`** (`docs/api-contract.md` §5).
 */
@Serializable
internal data class LockHistoryRequestDto(
    @SerialName("ns") val namespace: String,
    @SerialName("quantidade") val quantity: Int,
)

/** One entry of `historico-abertura` → `{ "tempoLocal", "nome", "tipo" }` (SPEC L9). */
@Serializable
internal data class LockOpeningEventDto(
    @SerialName("tempoLocal") val localTime: String,
    @SerialName("tipo") val type: String,
    @SerialName("nome") val name: String? = null,
)

/** `status-abertura` → `{ "aberto": true }`. */
@Serializable
internal data class LockOpenStateDto(@SerialName("aberto") val isOpen: Boolean)

/** `status-abrir-remoto` → `{ "habilitado": true }`. */
@Serializable
internal data class LockRemoteOpenDto(@SerialName("habilitado") val isEnabled: Boolean)

/** `volume` → `{ "volume": 1 }`, an integer 0..3. */
@Serializable
internal data class LockVolumeDto(@SerialName("volume") val level: Int)
