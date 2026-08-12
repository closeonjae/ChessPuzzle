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
