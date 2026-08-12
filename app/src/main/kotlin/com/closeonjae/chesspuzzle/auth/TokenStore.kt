package com.closeonjae.chesspuzzle.auth

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.tokenDataStore by preferencesDataStore(name = "lichess_auth")
private val ACCESS_TOKEN_KEY = stringPreferencesKey("access_token")

/**
 * Persists the Lichess access token. Plain DataStore, not
 * EncryptedSharedPreferences — PLAN.md 6절 트레이드오프: androidx.security.crypto
 * has a long history of dependency-version churn, and this is a personal,
 * single-user watch, so the simpler/more reliably-buildable option wins.
 * Tokens are long-lived (~1 year) with no refresh token (RESEARCH.md 3절).
 */
class TokenStore(private val context: Context) {
    val accessToken: Flow<String?> = context.tokenDataStore.data.map { it[ACCESS_TOKEN_KEY] }

    suspend fun save(accessToken: String) {
        context.tokenDataStore.edit { it[ACCESS_TOKEN_KEY] = accessToken }
    }

    suspend fun clear() {
        context.tokenDataStore.edit { it.remove(ACCESS_TOKEN_KEY) }
    }
}
