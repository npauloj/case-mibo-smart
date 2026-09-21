package io.github.npauloj.mibosmart.data.remote

import io.github.npauloj.mibosmart.domain.error.SmartHomeException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** SPEC E1, E3 and the token-rejection rule of ADR-002, against the bodies of `docs/api-contract.md` §1. */
class EnvelopeReaderTest {

    private val reader = EnvelopeReader(smartHomeJson)

    @Test
    fun wrappedSuccess() {
        val data = reader.read("""{"statusCode":200,"body":{"status":"sucesso","data":[{"nome":"MFR 1001"}]}}""")

        assertEquals(1, data.jsonArray.size)
    }

    @Test
    fun flatSuccess() {
        val data = reader.read("""{"status":"sucesso","data":{"volume":1}}""")

        assertEquals(1, data.jsonObject.getValue("volume").jsonPrimitive.int)
    }

    @Test
    fun flatErrorMissingToken() {
        val failure = assertFailsWith<SmartHomeException.ApiError> {
            reader.read("""{"status":"erro","msg":"Token não está presente na requisição"}""")
        }

        assertEquals("Token não está presente na requisição", failure.serverMessage)
    }

    @Test
    fun flatErrorUnknownBecomesTokenRejected() {
        assertFailsWith<SmartHomeException.TokenRejected> {
            reader.read("""{"status":"erro","msg":"Erro desconhecido, por favor tente novamente mais tarde"}""")
        }
    }

    /** The same generic text inside a wrapped envelope is a server error, not a verdict on the token (§1.2). */
    @Test
    fun wrappedUnknownErrorIsNotTokenRejected() {
        assertFailsWith<SmartHomeException.ApiError> {
            reader.read("""{"statusCode":500,"body":{"status":"erro","msg":"Erro desconhecido"}}""")
        }
    }

    @Test
    fun malformedJsonIsUnexpectedResponse() {
        assertFailsWith<SmartHomeException.UnexpectedResponse> {
            reader.read("<html>gateway timeout</html>")
        }
    }

    @Test
    fun successWithoutDataIsUnexpectedResponse() {
        assertFailsWith<SmartHomeException.UnexpectedResponse> {
            reader.read("""{"status":"sucesso"}""")
        }
    }
}
