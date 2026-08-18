package com.closeonjae.chesspuzzle.core.lichess

import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request

private val json = Json { ignoreUnknownKeys = true }

/**
 * The opening explorer lives on its **own host** — `explorer.lichess.org`, not
 * `lichess.org` (stated outright in the spec's Opening Explorer tag
 * description; the `explorer.lichess.ovh` name in older third-party writeups is
 * the previous host). RESEARCH.md 11-A절.
 */
private const val EXPLORER_BASE_URL = "https://explorer.lichess.org/lichess"

/**
 * Rating buckets the `/lichess` endpoint accepts, each covering from its own
 * value up to the next one (`2500` covers everything above). Verified against
 * the spec's enum — RESEARCH.md 11-A절.
 */
val RATING_BANDS = listOf(0, 1000, 1200, 1400, 1600, 1800, 2000, 2200, 2500)

/** Used when the account has no rated game at all to derive a band from. */
const val DEFAULT_GAME_RATING = 1600

/** Time controls whose games count toward the statistics — bullet/ultraBullet excluded as too noisy for opening study. */
val EXPLORER_SPEEDS = listOf("blitz", "rapid", "classical")

/** How many candidate moves to ask for. The board only marks the top few; the rest are for the move list. */
const val EXPLORER_MOVE_COUNT = 12

/**
 * The solver's own band plus the one above it (PLAN.md 9.5절): one band alone
 * leaves too thin a sample at lower ratings for the move order to be stable,
 * three or more stops meaning "my level".
 */
fun ratingBandsFor(rating: Int): List<Int> {
    val index = RATING_BANDS.indexOfLast { it <= rating }.coerceAtLeast(0)
    return listOfNotNull(RATING_BANDS[index], RATING_BANDS.getOrNull(index + 1))
}

/**
 * Built as a plain string rather than through `HttpUrl.Builder` so the
 * comma-separated list parameters stay literal commas, exactly as the spec's
 * own curl examples show them (commas are legal sub-delims in a query).
 *
 * [play] is the **whole move sequence from the initial position**, comma
 * separated UCI. That is not an optimization detail — the server resolves the
 * opening name by walking `play` move by move and keeping the last position
 * that has a name (`classify_and_play` in lila-openingexplorer, read directly;
 * RESEARCH.md 11-A절). Sending a bare `fen` instead only ever names positions
 * that are themselves exact dictionary entries, so most lookups would come back
 * with `opening: null`.
 */
fun explorerUrl(play: String, ratings: List<Int>): String = buildString {
    append(EXPLORER_BASE_URL)
    append("?variant=standard")
    append("&speeds=").append(EXPLORER_SPEEDS.joinToString(","))
    append("&ratings=").append(ratings.joinToString(","))
    append("&moves=").append(EXPLORER_MOVE_COUNT)
    // Asked for explicitly: the app shows neither, and leaving the defaults
    // (4 each) would make the server assemble game references nobody reads.
    append("&topGames=0&recentGames=0")
    append("&play=").append(play)
}

/**
 * Reads aggregated Lichess games for one position (RESEARCH.md 11절). Same
 * OkHttp + kotlinx.serialization shape as [LichessApiClient]; only the host and
 * the query differ.
 */
class OpeningExplorerClient(private val http: OkHttpClient = OkHttpClient()) {

    /**
     * [accessToken] is optional — the explorer answers unauthenticated
     * requests too — but the app is signed in anyway and sending the token
     * costs nothing while counting against a friendlier rate limit
     * (RESEARCH.md 11-C절).
     */
    fun lookup(accessToken: String?, play: String, ratings: List<Int>): ExplorerResponse {
        val builder = Request.Builder().url(explorerUrl(play, ratings)).get()
        if (accessToken != null) builder.header("Authorization", "Bearer $accessToken")
        http.newCall(builder.build()).execute().use { response ->
            val text = response.body.string()
            if (!response.isSuccessful) throw LichessApiException(response.code, text)
            return json.decodeFromString<ExplorerResponse>(text)
        }
    }
}
