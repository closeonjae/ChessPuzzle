package com.closeonjae.chesspuzzle.auth

import android.content.Context
import android.net.Uri
import androidx.wear.phone.interactions.authentication.CodeChallenge
import androidx.wear.phone.interactions.authentication.CodeVerifier
import androidx.wear.phone.interactions.authentication.OAuthRequest
import androidx.wear.phone.interactions.authentication.OAuthResponse
import androidx.wear.phone.interactions.authentication.RemoteAuthClient
import com.closeonjae.chesspuzzle.core.lichess.LichessApiClient
import com.closeonjae.chesspuzzle.core.lichess.Pkce
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.util.UUID
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Drives the Lichess OAuth2 Authorization Code + PKCE sign-in via
 * `RemoteAuthClient` (RESEARCH.md 4절): the watch builds the request, the
 * consent screen renders on the paired phone's browser, and the result is
 * relayed back automatically to `wear.googleapis.com/3p_auth/<package>` —
 * no phone-side app of ours is needed, matching the "standalone" decision.
 */
class LichessAuthManager(
    private val context: Context,
    private val api: LichessApiClient,
    private val tokenStore: TokenStore,
) {
    suspend fun signIn(): Result<String> {
        // core's Pkce.generateCodeVerifier() is the single tested source of
        // verifier generation (see :core PkceTest); wrapped in androidx's
        // CodeVerifier so OAuthRequest.Builder can derive the S256 challenge
        // itself (its CodeChallenge(CodeVerifier) uses the same algorithm —
        // confirmed against androidx-main source).
        val codeVerifier = CodeVerifier(Pkce.generateCodeVerifier())
        val codeChallenge = CodeChallenge(codeVerifier)
        val state = UUID.randomUUID().toString()

        val authProviderUrl = Uri.parse("https://lichess.org/oauth")
            .buildUpon()
            .appendQueryParameter("scope", SCOPE)
            .appendQueryParameter("state", state)
            .build()

        val request = OAuthRequest.Builder(context)
            .setAuthProviderUrl(authProviderUrl)
            .setCodeChallenge(codeChallenge)
            .setClientId(CLIENT_ID)
            .build()

        val client = RemoteAuthClient.create(context)
        return try {
            val response = requestAuthorization(client, request)
            val responseUrl = response.responseUrl
                ?: return Result.failure(IllegalStateException("Empty response from Lichess sign-in"))
            if (responseUrl.getQueryParameter("state") != state) {
                return Result.failure(IllegalStateException("OAuth state mismatch"))
            }
            val code = responseUrl.getQueryParameter("code")
                ?: return Result.failure(IllegalStateException("No authorization code returned"))

            val token = withContext(Dispatchers.IO) {
                api.exchangeToken(redirectUri = request.redirectUrl, code = code, codeVerifier = codeVerifier.value)
            }
            tokenStore.save(token.accessToken)
            Result.success(token.accessToken)
        } catch (t: Throwable) {
            Result.failure(t)
        } finally {
            client.close()
        }
    }

    private suspend fun requestAuthorization(
        client: RemoteAuthClient,
        request: OAuthRequest,
    ): OAuthResponse = suspendCancellableCoroutine { continuation ->
        client.sendAuthorizationRequest(
            request,
            { command -> command.run() },
            object : RemoteAuthClient.Callback() {
                override fun onAuthorizationResponse(request: OAuthRequest, response: OAuthResponse) {
                    if (continuation.isActive) continuation.resume(response)
                }

                override fun onAuthorizationError(request: OAuthRequest, errorCode: Int) {
                    if (continuation.isActive) {
                        continuation.resumeWithException(
                            IllegalStateException("Lichess sign-in failed (error code $errorCode)")
                        )
                    }
                }
            },
        )
    }

    companion object {
        // Lichess does not pre-register clients (RESEARCH.md 3절) — an
        // arbitrary unique string, chosen to match the app's package name.
        const val CLIENT_ID = "com.closeonjae.chesspuzzle"
        const val SCOPE = "puzzle:read puzzle:write"
    }
}
