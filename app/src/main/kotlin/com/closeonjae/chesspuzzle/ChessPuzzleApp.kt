package com.closeonjae.chesspuzzle

import android.app.Application
import com.closeonjae.chesspuzzle.auth.LichessAuthManager
import com.closeonjae.chesspuzzle.auth.TokenStore
import com.closeonjae.chesspuzzle.core.lichess.LichessApiClient
import com.closeonjae.chesspuzzle.data.PuzzleRepository

/**
 * Hand-wired singleton container — two screens and a handful of
 * collaborators don't warrant a DI framework (CLAUDE.md 2조: no
 * unrequested configurability).
 */
class ChessPuzzleApp : Application() {
    lateinit var tokenStore: TokenStore
        private set
    lateinit var authManager: LichessAuthManager
        private set
    lateinit var puzzleRepository: PuzzleRepository
        private set

    override fun onCreate() {
        super.onCreate()
        val apiClient = LichessApiClient(clientId = LichessAuthManager.CLIENT_ID)
        tokenStore = TokenStore(this)
        authManager = LichessAuthManager(this, apiClient, tokenStore)
        puzzleRepository = PuzzleRepository(apiClient, tokenStore)
    }
}
