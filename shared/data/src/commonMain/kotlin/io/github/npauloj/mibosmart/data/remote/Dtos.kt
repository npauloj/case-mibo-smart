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
)
