package io.github.npauloj.mibosmart.data.remote

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/**
 * Both response shapes of the partner API in one type (`docs/api-contract.md` §1.1): the wrapped
 * envelope fills [body], the flat one fills [status] / [data] / [msg] directly. The type refers to
 * itself because the wrapped `body` is exactly the flat shape.
 *
 * Partner field names exist only here, in `@SerialName`; the domain never sees them.
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

/**
 * `renovar-token` request (`docs/api-contract.md` §2, probed 2026-09-21).
 *
 * The token travels **twice** — here in the body and again in the `Authorization` header. That is what
 * the endpoint was measured to want, not a redundancy worth tidying away.
 */
@Serializable
internal data class RenewTokenRequestDto(@SerialName("token") val token: String)

/**
 * `renovar-token` → the new credential and the life the partner gave it (`docs/api-contract.md` §2).
 *
 * [lifetimeSeconds] is `tempoExpiracao`, **in seconds** — 7199 ≈ 2 h on the probe. It has no default:
 * a response without it is not this endpoint's answer, and silently falling back to a local 2 h count
 * is exactly the guess SPEC S10 exists to remove.
 */
@Serializable
internal data class RenewedTokenDto(
    @SerialName("token") val token: String,
    @SerialName("tempoExpiracao") val lifetimeSeconds: Long,
)

/**
 * `listar-dispositivos` request; both paging fields are required (`docs/api-contract.md` §3).
 *
 * No property here has a default: `kotlinx.serialization` omits defaults from the encoded body, and
 * SPEC D1 is an assertion about the exact bytes sent.
 */
@Serializable
internal data class ListDevicesRequestDto(
    @SerialName("tamanhoPagina") val pageSize: Int,
    @SerialName("pagina") val page: Int,
    @SerialName("origem") val origin: String,
)

/**
 * One device as `listar-dispositivos` returns it (`docs/api-contract.md` §3).
 *
 * Only the fields this case reads. `idProduto` is here although the list does not show it, because it
 * is `""` on some cameras and the lock slice needs to know that before it addresses one.
 *
 * [parentSerial] and [parentProductId] are the two the partner sends **only** when `subdispositivo`
 * is true, which is why they are nullable: a hub and a camera have no parent to name. Together with
 * this row's own `ns` and `idProduto` they are all four parts of a lock address (§5), so a lock is
 * addressable from its own row alone — no other row, and no second call, has to be found first.
 */
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

/**
 * The gateway's error shape, which is not the partner's.
 *
 * `{"message": "Forbidden"}` — capital-M, English, and no `status` — is what sits in front of the API
 * and answers a call this account may not make (`cota-disponivel`, probed 2026-09-21). The partner's
 * own errors always carry `status`/`msg`, so the presence of `message` alone is what tells a blocked
 * endpoint from an expired session on the same HTTP 403 (ADR-012).
 */
@Serializable
internal data class GatewayErrorDto(val message: String)
