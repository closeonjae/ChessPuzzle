package com.closeonjae.chesspuzzle.core.lichess

import kotlinx.serialization.Serializable

// Shapes mirror the Lichess OpenAPI spec's OpeningExplorerLichess schema
// exactly, verified against the live spec files on
// raw.githubusercontent.com/lichess-org/api — RESEARCH.md 11-B절 records the
// schema and the official example payload these models are tested against.
//
// `topGames`/`recentGames`/`history` are deliberately absent: the app asks for
// `topGames=0&recentGames=0` and never requests history, and the client's Json
// is configured with `ignoreUnknownKeys` so anything the server sends anyway is
// dropped rather than needing a model.

/** ECO classification + curated name of a position (RESEARCH.md 11-D절 dataset). */
@Serializable
data class ExplorerOpening(
    val eco: String,
    val name: String,
)

/**
 * One candidate move from the current position, with how the games that
 * played it turned out. [opening] is non-null **only when this move reaches a
 * position that is itself named** — the server fills it with
 * `classify_exact(pos_after)` (RESEARCH.md 11-B절), so it is exactly the
 * "playing this makes it the X opening" label and is null for most moves.
 */
@Serializable
data class ExplorerMove(
    val uci: String,
    val san: String,
    val averageRating: Int = 0,
    val white: Int = 0,
    val draws: Int = 0,
    val black: Int = 0,
    val opening: ExplorerOpening? = null,
) {
    /** Games in the database that played this move — the popularity denominator's numerator. */
    val total: Int get() = white + draws + black
}

/**
 * A whole opening-explorer lookup: what this position is called, how its games
 * went overall, and which moves were played from it (already sorted by
 * popularity, most played first).
 *
 * [opening] is the name of the **most recent named position along the queried
 * `play` sequence**, not necessarily of this exact position — see
 * `OpeningExplorerClient` for why the full move sequence has to be sent.
 */
@Serializable
data class ExplorerResponse(
    val opening: ExplorerOpening? = null,
    val white: Int = 0,
    val draws: Int = 0,
    val black: Int = 0,
    val moves: List<ExplorerMove> = emptyList(),
) {
    val total: Int get() = white + draws + black
}
