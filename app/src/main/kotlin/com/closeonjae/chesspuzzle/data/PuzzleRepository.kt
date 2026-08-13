package com.closeonjae.chesspuzzle.data

import com.closeonjae.chesspuzzle.auth.TokenStore
import com.closeonjae.chesspuzzle.core.lichess.LichessApiClient
import com.closeonjae.chesspuzzle.core.lichess.PuzzleAndGame
import com.closeonjae.chesspuzzle.core.lichess.PuzzleBatchSolveResponse
import com.closeonjae.chesspuzzle.core.lichess.PuzzleSolution
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

private const val ANGLE = "mix"

/**
 * Fetch/report loop backed by GET+POST /api/puzzle/batch/{angle}
 * (RESEARCH.md 6절 — the only pair that actually updates the solver's
 * puzzle rating server-side, unlike /api/puzzle/next).
 */
// open: the debug build variant substitutes a canned in-memory subclass
// for DebugPuzzlePreviewActivity (app/src/debug), to screen-check
// PuzzleScreen without a completed Lichess sign-in.
open class PuzzleRepository(
    private val api: LichessApiClient,
    private val tokenStore: TokenStore,
) {
    open suspend fun nextPuzzle(): Result<PuzzleAndGame> = runCatching {
        val token = requireToken()
        withContext(Dispatchers.IO) {
            api.fetchPuzzleBatch(token, angle = ANGLE, count = 1).puzzles.first()
        }
    }

    open suspend fun reportSolved(puzzleId: String, win: Boolean): Result<PuzzleBatchSolveResponse> = runCatching {
        val token = requireToken()
        withContext(Dispatchers.IO) {
            api.solvePuzzleBatch(
                token,
                angle = ANGLE,
                solutions = listOf(PuzzleSolution(id = puzzleId, win = win, rated = true)),
                nextBatchCount = 0,
            )
        }
    }

    private suspend fun requireToken(): String =
        tokenStore.accessToken.first() ?: error("Not signed in")
}
