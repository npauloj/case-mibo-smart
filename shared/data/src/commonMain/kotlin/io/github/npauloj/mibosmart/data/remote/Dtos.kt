package io.github.npauloj.mibosmart.data.remote

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/**
 * Both response shapes of the partner API in one type (`docs/api-contract.md` §1.1): the
 * wrapped envelope fills [body], the flat one fills [status] / [data] / [msg] directly.
 */
@Serializable
internal data class ApiEnvelopeDto(
    @SerialName("body") val body: ApiEnvelopeDto? = null,
    /** Present only on the wrapped shape, where it carries the outcome the HTTP status does not. */
    @SerialName("statusCode") val statusCode: Int? = null,
    @SerialName("status") val status: String? = null,
    @SerialName("data") val data: JsonElement? = null,
    @SerialName("msg") val msg: String? = null,
)

/** `renovar-token` request (`docs/api-contract.md` §2, probed 2026-09-21). */
@Serializable
internal data class RenewTokenRequestDto(@SerialName("token") val token: String)

/**
 * `renovar-token` → the new credential and the life the partner gave it (`docs/api-contract.md`
 * §2).
 */
@Serializable
internal data class RenewedTokenDto(
    @SerialName("token") val token: String,
    @SerialName("tempoExpiracao") val lifetimeSeconds: Long,
)

/** `listar-dispositivos` request; both paging fields are required (`docs/api-contract.md` §3). */
@Serializable
internal data class ListDevicesRequestDto(
    @SerialName("tamanhoPagina") val pageSize: Int,
    @SerialName("pagina") val page: Int,
    @SerialName("origem") val origin: String,
)

/** One device as `listar-dispositivos` returns it (`docs/api-contract.md` §3). */
@Serializable
internal data class DeviceDto(
    @SerialName("ns") val serial: String,
    @SerialName("modelo") val model: String,
    @SerialName("nome") val name: String,
    @SerialName("status") val status: String,
    @SerialName("origem") val origin: String,
    @SerialName("subdispositivo") val isSubDevice: Boolean = false,
    @SerialName("idProduto") val productId: String = "",
    @SerialName("ultimaVezOnline") val lastSeen: String? = null,
    @SerialName("dispositivoPai") val parentSerial: String? = null,
    @SerialName("idProdutoDispositivoPai") val parentProductId: String? = null,
)

/** The gateway's error shape, which is not the partner's. */
@Serializable
internal data class GatewayErrorDto(val message: String)
