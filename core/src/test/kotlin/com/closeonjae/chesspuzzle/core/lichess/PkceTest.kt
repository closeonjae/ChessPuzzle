package com.closeonjae.chesspuzzle.core.lichess

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class PkceTest {

    @Test
    fun `code verifier length is within the RFC 7636 range`() {
        val verifier = Pkce.generateCodeVerifier()
        assertTrue(verifier.length in 43..128, "length was ${verifier.length}")
    }

    @Test
    fun `code verifier only uses the unreserved character set`() {
        val verifier = Pkce.generateCodeVerifier()
        assertTrue(verifier.matches(Regex("^[A-Za-z0-9_-]+$")))
    }

    @Test
    fun `two generated verifiers are not equal`() {
        assertNotEquals(Pkce.generateCodeVerifier(), Pkce.generateCodeVerifier())
    }

    @Test
    fun `code challenge derivation is deterministic and base64url encoded`() {
        val verifier = Pkce.generateCodeVerifier()
        val challenge1 = Pkce.deriveCodeChallenge(verifier)
        val challenge2 = Pkce.deriveCodeChallenge(verifier)
        assertEquals(challenge1, challenge2)
        // SHA-256 digest is 32 bytes -> 43 chars in unpadded base64url.
        assertEquals(43, challenge1.length)
        assertTrue(challenge1.matches(Regex("^[A-Za-z0-9_-]+$")))
    }

    @Test
    fun `known RFC 7636 appendix B test vector`() {
        // https://datatracker.ietf.org/doc/html/rfc7636#appendix-B
        val verifier = "dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk"
        val expectedChallenge = "E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM"
        assertEquals(expectedChallenge, Pkce.deriveCodeChallenge(verifier))
    }
}
