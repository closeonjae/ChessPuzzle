package com.closeonjae.chesspuzzle

import android.app.Application
import com.closeonjae.chesspuzzle.auth.LichessAuthManager
import com.closeonjae.chesspuzzle.auth.TokenStore
import com.closeonjae.chesspuzzle.core.lichess.LichessApiClient
import com.closeonjae.chesspuzzle.core.lichess.OpeningExplorerClient
import com.closeonjae.chesspuzzle.data.OpeningRepository
import com.closeonjae.chesspuzzle.data.PuzzleRepository

/**
 * Hand-wired singleton container — a handful of screens and a handful of
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
    lateinit var openingRepository: OpeningRepository
        private set

    override fun onCreate() {
        super.onCreate()
        val apiClient = LichessApiClient(clientId = LichessAuthManager.CLIENT_ID)
        tokenStore = TokenStore(this)
        authManager = LichessAuthManager(this, apiClient, tokenStore)
        puzzleRepository = PuzzleRepository(apiClient, tokenStore)
        // Separate client: the opening explorer is a different host
        // (explorer.lichess.org), so it gets its own wrapper rather than
        // a second base URL threaded through LichessApiClient.
        openingRepository = OpeningRepository(apiClient, OpeningExplorerClient(), tokenStore)
    }
}
