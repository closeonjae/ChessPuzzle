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

    // nextBatchCount = 1 (was 0): the solve POST hands back the next puzzle
    // in the very same response instead of PuzzleViewModel making a
    // separate GET for it afterwards. Two things this fixes together
    // (user report — "퍼즐 풀고 난 이후에 다시 똑같은 퍼즐을 풀도록 롤백되는 문제"):
    // firing that GET concurrently with this POST (rather than after it, to
    // prefetch faster) raced ahead of the server recording the solve, so it
    // could hand back the very puzzle just solved, still marked unsolved.
    // Bundling the next puzzle into this one call removes that race by
    // construction — the server can only return it once this solve is
    // already recorded — and cuts the solved→next-puzzle path from two
    // network round trips down to one (user report: next-puzzle loading
    // was too slow). PuzzleViewModel falls back to a separate GET only if
    // this doesn't come back with one (e.g. the debug fixture, or a solve
    // that itself failed).
    open suspend fun reportSolved(puzzleId: String, win: Boolean): Result<PuzzleBatchSolveResponse> = runCatching {
        val token = requireToken()
        withContext(Dispatchers.IO) {
            api.solvePuzzleBatch(
                token,
                angle = ANGLE,
                solutions = listOf(PuzzleSolution(id = puzzleId, win = win, rated = true)),
                nextBatchCount = 1,
            )
        }
    }

    private suspend fun requireToken(): String =
        tokenStore.accessToken.first() ?: error("Not signed in")
}
