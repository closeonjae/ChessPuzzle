package com.closeonjae.chesspuzzle.core.lichess

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.FormBody
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException

// encodeDefaults = true: Lichess's documented request example includes
// "rated": true explicitly (RESEARCH.md 6절) — without this, kotlinx.serialization
// silently omits fields that equal their Kotlin default, which a test caught.
private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

/** Thrown for any non-2xx Lichess API response; [rawBody] is kept for debugging/logging. */
class LichessApiException(val httpCode: Int, val rawBody: String) :
    IOException("Lichess API error $httpCode: $rawBody")

/**
 * Talks to the Lichess OAuth + puzzle endpoints (RESEARCH.md 3/6절).
 * [clientId] is an arbitrary identifier — Lichess does not pre-register
 * public/PKCE clients.
 */
class LichessApiClient(
    private val clientId: String,
    private val http: OkHttpClient = OkHttpClient(),
) {
    /**
     * POST /api/token — exchanges an authorization code for a long-lived access token.
     *
     * Note: unlike a typical PKCE client, this app does not build the
     * `/oauth` authorize URL itself — on Wear OS that request goes through
     * `RemoteAuthClient`/`OAuthRequest.Builder` instead (RESEARCH.md 4절),
     * which composes the authorize URL internally. This method is the other
     * half of the flow: the code-for-token exchange, which is plain HTTP
     * and identical regardless of how the authorize step happened.
     */
    fun exchangeToken(redirectUri: String, code: String, codeVerifier: String): TokenResponse {
        val body = FormBody.Builder()
            .add("grant_type", "authorization_code")
            .add("code", code)
            .add("code_verifier", codeVerifier)
            .add("redirect_uri", redirectUri)
            .add("client_id", clientId)
            .build()
        val request = Request.Builder()
            .url("https://lichess.org/api/token")
            .post(body)
            .build()
        return execute(request)
    }

    /**
     * GET /api/account — the signed-in user's profile. Only called by the
     * opening screen, once per app run, to work out which explorer rating
     * bands count as "my level" (PLAN.md 9.3절).
     */
    fun fetchAccount(accessToken: String): AccountResponse {
        val request = Request.Builder()
            .url("https://lichess.org/api/account")
            .header("Authorization", "Bearer $accessToken")
            .get()
            .build()
        return execute(request)
    }

    /**
     * GET /api/puzzle/batch/{angle} — a batch of puzzles picked against the
     * signed-in user's own puzzle rating. `angle=mix` is Lichess's
     * recommended catch-all theme.
     */
    fun fetchPuzzleBatch(accessToken: String, angle: String = "mix", count: Int = 1): PuzzleBatchSelectResponse {
        val request = Request.Builder()
            .url("https://lichess.org/api/puzzle/batch/$angle?nb=$count")
            .header("Authorization", "Bearer $accessToken")
            .get()
            .build()
        return execute(request)
    }

    /**
     * POST /api/puzzle/batch/{angle} — reports solved puzzles and updates
     * the user's Glicko puzzle rating server-side (RESEARCH.md 6절 — this is
     * the only endpoint that actually moves the rating; /api/puzzle/next
     * does not). Optionally returns the next batch in the same call.
     */
    fun solvePuzzleBatch(
        accessToken: String,
        angle: String = "mix",
        solutions: List<PuzzleSolution>,
        nextBatchCount: Int = 1,
    ): PuzzleBatchSolveResponse {
        val payload = json.encodeToString(PuzzleBatchSolveRequest(solutions))
        val request = Request.Builder()
            .url("https://lichess.org/api/puzzle/batch/$angle?nb=$nextBatchCount")
            .header("Authorization", "Bearer $accessToken")
            .post(payload.toRequestBody("application/json".toMediaType()))
            .build()
        return execute(request)
    }

    private inline fun <reified T> execute(request: Request): T {
        http.newCall(request).execute().use { response ->
            val text = response.body.string()
            if (!response.isSuccessful) throw LichessApiException(response.code, text)
            return json.decodeFromString<T>(text)
        }
    }
}
