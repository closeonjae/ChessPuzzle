package com.closeonjae.chesspuzzle.data

import com.closeonjae.chesspuzzle.auth.TokenStore
import com.closeonjae.chesspuzzle.core.lichess.DEFAULT_GAME_RATING
import com.closeonjae.chesspuzzle.core.lichess.ExplorerResponse
import com.closeonjae.chesspuzzle.core.lichess.LichessApiClient
import com.closeonjae.chesspuzzle.core.lichess.OpeningExplorerClient
import com.closeonjae.chesspuzzle.core.lichess.ratingBandsFor
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** How many looked-up positions to keep. A whole opening walk is a few dozen at most, so this never evicts in practice — it only stops a very long session growing without bound. */
private const val CACHE_SIZE = 64

/**
 * Opening-explorer lookups for the line being explored (PLAN.md 9.4절).
 *
 * Two things here are not incidental:
 *
 * - **The cache.** Lichess asks for one request at a time and answers a
 *   429 with "wait a minute" (RESEARCH.md 11-C절). Stepping back through a
 *   line is the most common thing this screen does, and every position in it
 *   has already been looked up — so undo costs zero requests, and so does
 *   re-walking a line the user just backed out of.
 * - **The rating bands.** Fetched once per app run from the account's own
 *   game rating, then kept: they can't change mid-session, and re-deriving
 *   them would mean a second request behind every single lookup.
 */
// open: the debug build variant can substitute a canned subclass, same as
// PuzzleRepository does for the puzzle screen's preview activity.
open class OpeningRepository(
    private val api: LichessApiClient,
    private val explorer: OpeningExplorerClient,
    private val tokenStore: TokenStore,
) {
    private val cache = object : LinkedHashMap<String, ExplorerResponse>(CACHE_SIZE, 0.75f, true) {
        override fun removeEldestEntry(eldest: Map.Entry<String, ExplorerResponse>): Boolean = size > CACHE_SIZE
    }

    private val bandsLock = Mutex()
    private var cachedBands: List<Int>? = null

    /**
     * The rating bands to filter by. Falls back to the default band pair if
     * the account lookup fails or the account has no rated game — a
     * still-useful screen beats a blocked one, and the puzzle screen is
     * unaffected either way.
     */
    private suspend fun ratingBands(token: String): List<Int> = bandsLock.withLock {
        cachedBands ?: run {
            val rating = runCatching { withContext(Dispatchers.IO) { api.fetchAccount(token).gameRating } }
                .getOrNull() ?: DEFAULT_GAME_RATING
            ratingBandsFor(rating).also { cachedBands = it }
        }
    }

    /** Cached lookups only — lets the caller show a position instantly instead of dimming it while a request it doesn't need runs. */
    fun cached(play: String): ExplorerResponse? = synchronized(cache) { cache[play] }

    /**
     * [play] is the whole comma-separated UCI sequence from the initial
     * position — see `explorerUrl`'s doc for why the sequence and not a FEN.
     *
     * Cancellation is rethrown rather than folded into the `Result`: the caller
     * cancels the in-flight lookup every time a move is played, and a
     * cancellation reported as a failure would flash "Connection Lost" on a
     * screen that is merely moving on to the next position.
     */
    open suspend fun explore(play: String): Result<ExplorerResponse> = try {
        Result.success(
            cached(play) ?: run {
                val token = tokenStore.accessToken.first()
                val bands = ratingBands(token ?: error("Not signed in"))
                withContext(Dispatchers.IO) { explorer.lookup(token, play, bands) }
                    .also { synchronized(cache) { cache[play] = it } }
            },
        )
    } catch (e: CancellationException) {
        throw e
    } catch (e: Throwable) {
        Result.failure(e)
    }
}
