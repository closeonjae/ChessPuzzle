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
    val total: Long get() = white.toLong() + draws + black
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
    /**
     * Long, not Int: the real watch reported **1,927,800,000** games for the
     * starting position at one rating band pair — already 90% of `Int.MAX`, so
     * summing the three colours as Int is one busier position away from
     * wrapping negative.
     */
    val total: Long get() = white.toLong() + draws + black
}

/**
 * Games counts for the ECO chip, which has room for about four characters —
 * not for 1,927,800,000.
 *
 * The billions tier is not hypothetical: without it the watch rendered
 * `1927.8M` for the starting position (seen on the device, not the emulator,
 * whose fixture never got that large).
 */
fun formatGameCount(total: Long): String = when {
    total >= 1_000_000_000 -> "%.1fB".format(total / 1_000_000_000.0)
    total >= 10_000_000 -> "${total / 1_000_000}M"
    total >= 1_000_000 -> "%.1fM".format(total / 1_000_000.0)
    total >= 10_000 -> "${total / 1_000}K"
    total >= 1_000 -> "%.1fK".format(total / 1_000.0)
    else -> total.toString()
}
