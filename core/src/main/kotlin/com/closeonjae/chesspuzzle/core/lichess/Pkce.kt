package com.closeonjae.chesspuzzle.core.lichess

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64

/**
 * RFC 7636 PKCE helpers for the Lichess Authorization Code + PKCE flow
 * (RESEARCH.md 3절). Lichess accepts unregistered public clients — only
 * `code_challenge_method=S256` is supported.
 */
object Pkce {
    private val random = SecureRandom()

    /** A cryptographically random code_verifier, 43-128 chars per RFC 7636 (this yields ~86). */
    fun generateCodeVerifier(): String {
        val bytes = ByteArray(64)
        random.nextBytes(bytes)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }

    /** code_challenge = BASE64URL(SHA256(ASCII(code_verifier))). */
    fun deriveCodeChallenge(codeVerifier: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(codeVerifier.toByteArray(Charsets.US_ASCII))
        return Base64.getUrlEncoder().withoutPadding().encodeToString(digest)
    }
}
