package io.github.npauloj.mibosmart.data.remote

import io.github.npauloj.mibosmart.domain.lock.LockAddress
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * How a [LockAddress] becomes a partner request (`docs/api-contract.md` §5).
 *
 * The composite namespace is the partner's encoding and lives here, alone: the domain keeps the four
 * identities apart and never learns that they are joined by an underscore (ADR-004).
 */
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

    /**
     * `<lockNs>_<hubNs>_<hubIdProduto>` — the lock addressed as a sub-device of its hub (SPEC L1).
     *
     * The order is the contract's and is not a detail: the same three strings in any other order
     * address nothing, and the API answers "Dispositivo não encontrado" for a lock's plain namespace
     * (`docs/api-contract.md` §3).
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
 * `volume`, whose contract bug is quarantined here (`docs/api-contract.md` §5 and §7.4, SPEC L8).
 *
 * The Swagger lists `productId` as required while the property the API reads is `idProduto`; sending
 * both is what worked against the real endpoint. The duplication is deliberate and belongs to this
 * one request — no other lock call carries it.
 */
@Serializable
internal data class LockVolumeRequestDto(
    @SerialName("ns") val namespace: String,
    @SerialName("idProduto") val productId: String,
    @SerialName("productId") val productIdAsRequired: String,
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
