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
    @SerialName("status") val status: String? = null,
    @SerialName("data") val data: JsonElement? = null,
    @SerialName("msg") val msg: String? = null,
)

/** `listar-dispositivos` request; both paging fields are required (`docs/api-contract.md` §3). */
@Serializable
internal data class ListDevicesRequestDto(
    @SerialName("tamanhoPagina") val pageSize: Int,
    @SerialName("pagina") val page: Int,
)
