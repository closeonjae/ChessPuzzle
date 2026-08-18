package com.closeonjae.chesspuzzle.core.lichess

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Fixtures are the literal example payload from Lichess's own OpenAPI spec
 * (`examples/openingExplorer-lichess.json.yaml`), transcribed in RESEARCH.md
 * 11-B절 — same approach as [LichessModelsTest].
 */
class OpeningExplorerTest {

    // Mirrors OpeningExplorerClient's Json config, so this exercises the real wire format.
    private val json = Json { ignoreUnknownKeys = true }

    private val exampleResponse = """
        {
          "white": 5061745,
          "draws": 492487,
          "black": 4458129,
          "moves": [
            {
              "uci": "c6d5", "san": "cxd5", "averageRating": 1806,
              "white": 4517660, "draws": 450366, "black": 4016728,
              "game": null, "opening": null
            },
            {
              "uci": "g8f6", "san": "Nf6", "averageRating": 1973,
              "white": 195502, "draws": 17425, "black": 184987,
              "game": null,
              "opening": { "eco": "D06", "name": "Queen's Gambit Declined: Marshall Defense, Tan Gambit" }
            }
          ],
          "topGames": [
            {
              "uci": "g8f6", "id": "EqJcFS1j", "winner": "white", "speed": "ultraBullet",
              "mode": "rated", "black": { "name": "toivok", "rating": 2708 },
              "white": { "name": "penguingim1", "rating": 2969 },
              "year": 2018, "month": "2018-04"
            }
          ],
          "opening": { "eco": "D10", "name": "Slav Defense: Exchange Variation" }
        }
    """.trimIndent()

    @Test
    fun `parses the official explorer example`() {
        val response = json.decodeFromString<ExplorerResponse>(exampleResponse)

        assertEquals("D10", response.opening?.eco)
        assertEquals("Slav Defense: Exchange Variation", response.opening?.name)
        assertEquals(5061745, response.white)
        assertEquals(492487, response.draws)
        assertEquals(4458129, response.black)
        assertEquals(10_012_361L, response.total)
        assertEquals(listOf("cxd5", "Nf6"), response.moves.map { it.san })
    }

    @Test
    fun `a move only carries an opening name when it reaches a named position`() {
        val moves = json.decodeFromString<ExplorerResponse>(exampleResponse).moves

        // Most moves don't rename the opening — the server fills this field
        // only on an exact dictionary hit (RESEARCH.md 11-B절).
        assertNull(moves[0].opening)
        assertEquals("D06", moves[1].opening?.eco)
        assertEquals(4_517_660L + 450_366 + 4_016_728, moves[0].total)
    }

    @Test
    fun `topGames and recentGames are ignored rather than modeled`() {
        // The app asks for topGames=0&recentGames=0, but the response above
        // still carries a topGames array — parsing must not fail on it.
        val response = json.decodeFromString<ExplorerResponse>(exampleResponse)
        assertEquals(2, response.moves.size)
    }

    @Test
    fun `an unnamed position parses with a null opening`() {
        val text = """{"white":3,"draws":0,"black":1,"moves":[],"topGames":[],"opening":null}"""
        val response = json.decodeFromString<ExplorerResponse>(text)
        assertNull(response.opening)
        assertTrue(response.moves.isEmpty())
        assertEquals(4L, response.total)
    }

    @Test
    fun `game counts in the billions abbreviate to B`() {
        // Regression: the watch rendered "1927.8M" for the starting position,
        // because the formatter stopped at millions. Real figure, read off the
        // device against the signed-in account's own rating bands.
        assertEquals("1.9B", formatGameCount(1_927_800_000L))
        assertEquals("1.0B", formatGameCount(1_000_000_000L))
        assertEquals("7.4B", formatGameCount(7_412_000_000L))
    }

    @Test
    fun `game counts abbreviate by magnitude`() {
        assertEquals("999", formatGameCount(999L))
        assertEquals("1.0K", formatGameCount(1_000L))
        assertEquals("9.9K", formatGameCount(9_900L))
        assertEquals("12K", formatGameCount(12_345L))
        assertEquals("999K", formatGameCount(999_999L))
        assertEquals("1.2M", formatGameCount(1_200_000L))
        assertEquals("4.8M", formatGameCount(4_800_000L))
        assertEquals("23M", formatGameCount(23_700_000L))
        assertEquals("0", formatGameCount(0L))
    }

    @Test
    fun `totals are summed as Long so a busy position cannot wrap negative`() {
        // 1.93 billion is already 90% of Int.MAX; three colours of a busier
        // position would overflow an Int sum.
        val response = ExplorerResponse(white = 900_000_000, draws = 127_800_000, black = 900_000_000)
        assertEquals(1_927_800_000L, response.total)
        assertTrue(response.total > Int.MAX_VALUE / 2)
    }

    @Test
    fun `rating bands cover the queried rating and the one above it`() {
        assertEquals(listOf(1600, 1800), ratingBandsFor(1712))
        assertEquals(listOf(1600, 1800), ratingBandsFor(1600))
        assertEquals(listOf(1400, 1600), ratingBandsFor(1599))
        assertEquals(listOf(0, 1000), ratingBandsFor(800))
        assertEquals(listOf(0, 1000), ratingBandsFor(999))
        assertEquals(listOf(1000, 1200), ratingBandsFor(1000))
    }

    @Test
    fun `the top band has nothing above it`() {
        assertEquals(listOf(2200, 2500), ratingBandsFor(2499))
        assertEquals(listOf(2500), ratingBandsFor(2500))
        assertEquals(listOf(2500), ratingBandsFor(3000))
    }

    @Test
    fun `a rating below every band still resolves to the lowest one`() {
        // Not reachable from a real Lichess rating, but the index math must
        // not fall off the front of the list if one ever shows up.
        assertEquals(listOf(0, 1000), ratingBandsFor(-1))
    }

    @Test
    fun `the explorer url carries the whole play sequence and the band filter`() {
        val url = explorerUrl(play = "d2d4,d7d5,c2c4,c7c6,c4d5", ratings = listOf(1600, 1800))

        assertTrue(url.startsWith("https://explorer.lichess.org/lichess?"), url)
        assertTrue(url.contains("&play=d2d4,d7d5,c2c4,c7c6,c4d5"), url)
        assertTrue(url.contains("&ratings=1600,1800"), url)
        assertTrue(url.contains("&speeds=blitz,rapid,classical"), url)
        assertTrue(url.contains("&moves=12"), url)
        // Requested explicitly: the defaults are 4 each and nothing renders them.
        assertTrue(url.contains("&topGames=0&recentGames=0"), url)
    }

    @Test
    fun `the initial position queries an empty play sequence`() {
        val url = explorerUrl(play = "", ratings = listOf(1600, 1800))
        assertTrue(url.endsWith("&play="), url)
    }

    @Test
    fun `account rating prefers rapid then blitz then classical`() {
        val text = """
            {
              "id": "someone", "username": "Someone",
              "perfs": {
                "blitz": { "games": 400, "rating": 1690 },
                "rapid": { "games": 120, "rating": 1745 },
                "classical": { "games": 3, "rating": 1800 }
              }
            }
        """.trimIndent()
        assertEquals(1745, json.decodeFromString<AccountResponse>(text).gameRating)
    }

    @Test
    fun `an unplayed perf is skipped rather than treated as a rating`() {
        // Lichess reports a provisional 1500 with games = 0 for perfs the
        // account never played; taking it would pick the wrong band.
        val text = """
            {
              "id": "someone", "username": "Someone",
              "perfs": {
                "rapid": { "games": 0, "rating": 1500 },
                "blitz": { "games": 250, "rating": 2050 }
              }
            }
        """.trimIndent()
        val account = json.decodeFromString<AccountResponse>(text)
        assertEquals(2050, account.gameRating)
        assertEquals(listOf(2000, 2200), ratingBandsFor(assertNotNull(account.gameRating)))
    }

    @Test
    fun `an account with no rated games has no rating to derive a band from`() {
        val text = """{"id":"someone","username":"Someone","perfs":{"rapid":{"games":0,"rating":1500}}}"""
        assertNull(json.decodeFromString<AccountResponse>(text).gameRating)
        // The caller falls back to the default, which lands on the middle bands.
        assertEquals(listOf(1600, 1800), ratingBandsFor(DEFAULT_GAME_RATING))
    }
}
