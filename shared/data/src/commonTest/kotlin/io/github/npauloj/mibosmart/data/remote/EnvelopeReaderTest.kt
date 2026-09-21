package io.github.npauloj.mibosmart.data.remote

import io.github.npauloj.mibosmart.domain.error.SmartHomeException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * SPEC E1, E3, S3 and S3.1, against the bodies really observed in `docs/api-contract.md` §1.
 *
 * The four authentication cases are transcribed from the probes of 2026-09-21 (ADR-012), not from the
 * documentation that preceded them — the previous version of this file was written from the docs, and
 * it passed while the app could not recognise a single rejected token.
 */
class EnvelopeReaderTest {

    private val reader = EnvelopeReader(smartHomeJson)

    @Test
    fun wrappedSuccess() {
        val data = reader.read(OK, """{"statusCode":200,"body":{"status":"sucesso","data":[{"nome":"MFR 1001"}]}}""")

        assertEquals(1, data.jsonArray.size)
    }

    @Test
    fun flatSuccess() {
        val data = reader.read(OK, """{"status":"sucesso","data":{"volume":1}}""")

        assertEquals(1, data.jsonObject.getValue("volume").jsonPrimitive.int)
    }

    /** 401 with a bare JSON string — the shape an absent or unrecognised token really answers with. */
    @Test
    fun unauthorizedIsTokenRejected() {
        assertFailsWith<SmartHomeException.TokenRejected> {
            reader.read(UNAUTHORIZED, "\"Não autorizado\"")
        }
        assertFailsWith<SmartHomeException.TokenRejected> {
            reader.read(UNAUTHORIZED, "\"Token não está presente na requisição\"")
        }
    }

    /** 403 carries an envelope whose message is fit to show the user as-is (SPEC S3.1). */
    @Test
    fun forbiddenIsTokenExpiredWithServerMessage() {
        val failure = assertFailsWith<SmartHomeException.TokenExpired> {
            reader.read(FORBIDDEN, """{"status":"erro","msg":"Token expirado, por favor gere um novo token"}""")
        }

        assertEquals("Token expirado, por favor gere um novo token", failure.serverMessage)
    }

    /**
     * The status decides; the body only supplies the nicer sentence. A 403 whose body cannot be read
     * is still an expired session, never "resposta inesperada".
     */
    @Test
    fun forbiddenWithUnparseableBodyStillExpires() {
        val failure = assertFailsWith<SmartHomeException.TokenExpired> {
            reader.read(FORBIDDEN, "<html>gateway timeout</html>")
        }

        assertNull(failure.serverMessage, "no message to show, but the verdict is unchanged")
    }

    /**
     * The regression this rewrite exists for: a bare JSON string is not an envelope, so a reader that
     * parsed before classifying turned every rejected token into [SmartHomeException.UnexpectedResponse]
     * — which is what the app showed on a real device (ADR-012).
     */
    @Test
    fun bareJsonStringIsNotUnexpectedResponse() {
        assertFailsWith<SmartHomeException.TokenRejected> {
            reader.read(UNAUTHORIZED, "\"Não autorizado\"")
        }
    }

    /** A 200 body still decides on `status`: business errors did not move (SPEC E1). */
    @Test
    fun flatErrorMissingToken() {
        val failure = assertFailsWith<SmartHomeException.ApiError> {
            reader.read(OK, """{"status":"erro","msg":"Token não está presente na requisição"}""")
        }

        assertEquals("Token não está presente na requisição", failure.serverMessage)
    }

    /**
     * "Erro desconhecido" is no longer a verdict on the credential (ADR-012 supersedes ADR-002's rule):
     * inside a 200 it is an ordinary server error, and the API never sends it for an auth failure.
     */
    @Test
    fun unknownErrorOnOkIsJustAnApiError() {
        val failure = assertFailsWith<SmartHomeException.ApiError> {
            reader.read(OK, """{"status":"erro","msg":"Erro desconhecido, por favor tente novamente mais tarde"}""")
        }

        assertEquals("Erro desconhecido, por favor tente novamente mais tarde", failure.serverMessage)
    }

    /**
     * The wrapped shape carries its own outcome while HTTP still says 200 (api-contract §1.1), and
     * `404` means one thing only: the device is not there. Mapping it to
     * [SmartHomeException.ApiError] — as this reader did while nothing could receive it — would put
     * the partner's raw `msg` on a screen and make a missing device indistinguishable from a server
     * fault the user should retry (SPEC E2, U6).
     */
    @Test
    fun wrappedError404IsDeviceNotFound() {
        assertFailsWith<SmartHomeException.DeviceNotFound> {
            reader.read(OK, """{"statusCode":404,"body":{"status":"erro","msg":"Dispositivo não encontrado"}}""")
        }
    }

    /** A wrapped `statusCode: 200` is an ordinary success: the new rule fires on 404 and nothing else. */
    @Test
    fun wrappedSuccessWithStatusCodeIsNotADeviceError() {
        val data = reader.read(OK, """{"statusCode":200,"body":{"status":"sucesso","data":[]}}""")

        assertEquals(0, data.jsonArray.size)
    }

    /**
     * The regression this amendment exists for: `cota-disponivel` answers 403 with the **gateway's**
     * shape for a perfectly valid token. Classifying it as an expiry would clear the session and send
     * the user back to the token screen for no reason (SPEC S6). Probed 2026-09-21.
     */
    @Test
    fun forbiddenWithGatewayShapeIsNotAnExpiry() {
        val failure = assertFailsWith<SmartHomeException.Forbidden> {
            reader.read(FORBIDDEN, """{"message":"Forbidden"}""")
        }

        assertEquals("Forbidden", failure.gatewayMessage)
    }

    /** The two 403 shapes are told apart by the body, and only by the body. */
    @Test
    fun thePartnerEnvelopeOnForbiddenIsStillAnExpiry() {
        assertFailsWith<SmartHomeException.TokenExpired> {
            reader.read(FORBIDDEN, """{"status":"erro","msg":"Token expirado, por favor gere um novo token"}""")
        }
        assertFailsWith<SmartHomeException.Forbidden> {
            reader.read(FORBIDDEN, """{"message":"Forbidden"}""")
        }
    }

    @Test
    fun malformedJsonIsUnexpectedResponse() {
        assertFailsWith<SmartHomeException.UnexpectedResponse> {
            reader.read(OK, "<html>gateway timeout</html>")
        }
    }

    @Test
    fun successWithoutDataIsUnexpectedResponse() {
        assertFailsWith<SmartHomeException.UnexpectedResponse> {
            reader.read(OK, """{"status":"sucesso"}""")
        }
    }

    private companion object {
        const val OK = 200
        const val UNAUTHORIZED = 401
        const val FORBIDDEN = 403
    }
}
