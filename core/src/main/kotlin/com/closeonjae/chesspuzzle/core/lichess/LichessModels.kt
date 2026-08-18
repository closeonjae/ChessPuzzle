package com.closeonjae.chesspuzzle.core.lichess

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// Shapes mirror the Lichess OpenAPI spec exactly as verified against the
// live raw.githubusercontent.com/lichess-org/api spec files — see
// RESEARCH.md 3/6절 for the source citations behind each field.

@Serializable
data class TokenResponse(
    @SerialName("token_type") val tokenType: String,
    @SerialName("access_token") val accessToken: String,
    @SerialName("expires_in") val expiresIn: Long,
)

@Serializable
data class Perf(
    val key: String,
    val name: String,
)

@Serializable
data class Player(
    val name: String,
    val id: String? = null,
    val color: String,
    val rating: Int? = null,
)

@Serializable
data class Game(
    val id: String,
    val perf: Perf,
    val rated: Boolean,
    val players: List<Player>,
    // Space-separated SAN tokens, no move numbers (RESEARCH.md 3절 example).
    val pgn: String,
    val clock: String? = null,
)

@Serializable
data class Puzzle(
    val id: String,
    val rating: Int,
    val plays: Int,
    // UCI moves, alternating solver / opponent, starting with the solver
    // (verified against lichess-org/lila's ui/puzzle/src/ctrl.ts + moveTest.ts).
    val solution: List<String>,
    val themes: List<String>,
    val initialPly: Int,
    val fen: String? = null,
    val lastMove: String? = null,
)

@Serializable
data class PuzzleAndGame(
    val game: Game,
    val puzzle: Puzzle,
)

@Serializable
data class PuzzleBatchSelectResponse(
    val puzzles: List<PuzzleAndGame>,
)

@Serializable
data class PuzzleSolution(
    val id: String,
    val win: Boolean,
    val rated: Boolean = true,
)

@Serializable
data class PuzzleBatchSolveRequest(
    val solutions: List<PuzzleSolution>,
)

@Serializable
data class PuzzleGlicko(
    val rating: Double,
    val deviation: Double,
)

@Serializable
data class PuzzleRound(
    val id: String,
    val win: Boolean,
    val ratingDiff: Int? = null,
)

@Serializable
data class PuzzleBatchSolveResponse(
    val puzzles: List<PuzzleAndGame> = emptyList(),
    val glicko: PuzzleGlicko? = null,
    val rounds: List<PuzzleRound> = emptyList(),
)

/**
 * One perf (time control) entry of `GET /api/account`. Only the two fields the
 * app reads are modeled; [games] matters because Lichess reports a
 * provisional 1500 for perfs the account has never actually played.
 */
@Serializable
data class PerfRating(
    val rating: Int? = null,
    val games: Int = 0,
)

@Serializable
data class AccountPerfs(
    val rapid: PerfRating? = null,
    val blitz: PerfRating? = null,
    val classical: PerfRating? = null,
)

@Serializable
data class AccountResponse(
    val id: String,
    val username: String,
    val perfs: AccountPerfs? = null,
) {
    /**
     * The rating the opening explorer's band filter is derived from (PLAN.md
     * 9.3절). Rapid first, then blitz, then classical — and only perfs that
     * have actually been played, since an unplayed perf still reports a
     * provisional 1500 that would silently pick the wrong band. Null when the
     * account has no rated game at all; the caller falls back to
     * [DEFAULT_GAME_RATING].
     *
     * The puzzle rating is deliberately not used here: it is a different
     * Glicko pool from game ratings, so it does not map onto the explorer's
     * game-rating bands.
     */
    val gameRating: Int?
        get() = listOfNotNull(perfs?.rapid, perfs?.blitz, perfs?.classical)
            .firstOrNull { it.games > 0 }
            ?.rating
}
