package com.closeonjae.chesspuzzle.core.lichess

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** Fixtures are the literal example payloads recorded in RESEARCH.md 3/6절 (from Lichess's own OpenAPI spec). */
class LichessModelsTest {

    // Mirrors LichessApiClient's Json config exactly, so this test exercises
    // the same wire format the app actually sends/receives.
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    @Test
    fun `parses the token response example`() {
        val text = """{"token_type":"Bearer","access_token":"test-access-token-not-a-real-secret","expires_in":31536000}"""
        val token = json.decodeFromString<TokenResponse>(text)
        assertEquals("Bearer", token.tokenType)
        assertEquals("test-access-token-not-a-real-secret", token.accessToken)
        assertEquals(31536000L, token.expiresIn)
    }

    @Test
    fun `parses the puzzle-next example response`() {
        val text = """
            {
              "game": {
                "id": "50ZuAmiN",
                "perf": { "key": "blitz", "name": "Blitz" },
                "rated": true,
                "players": [
                  { "name": "mjadidi", "id": "mjadidi", "color": "white", "rating": 1707 },
                  { "name": "B-A-L", "id": "b-a-l", "color": "black", "rating": 1679 }
                ],
                "pgn": "e4 e6 Nf3 d5 e5 c5 c3 Qb6",
                "clock": "5+1"
              },
              "puzzle": {
                "id": "QBX2O",
                "rating": 1632,
                "plays": 3889,
                "solution": ["f2g1", "h1g1", "c8c1"],
                "themes": ["mateIn2", "middlegame", "short", "attraction", "sacrifice"],
                "initialPly": 66
              }
            }
        """.trimIndent()
        val result = json.decodeFromString<PuzzleAndGame>(text)
        assertEquals("QBX2O", result.puzzle.id)
        assertEquals(1632, result.puzzle.rating)
        assertEquals(listOf("f2g1", "h1g1", "c8c1"), result.puzzle.solution)
        assertEquals(66, result.puzzle.initialPly)
        assertNull(result.puzzle.fen)
        assertEquals("blitz", result.game.perf.key)
        assertEquals(2, result.game.players.size)
    }

    @Test
    fun `parses the batch-solve response example including the updated glicko rating`() {
        val text = """
            {
              "puzzles": [],
              "glicko": { "rating": 1663.73, "deviation": 91.04 },
              "rounds": [ { "id": "8KtG9", "win": true, "ratingDiff": 14 } ]
            }
        """.trimIndent()
        val result = json.decodeFromString<PuzzleBatchSolveResponse>(text)
        assertEquals(1663.73, result.glicko?.rating)
        assertEquals(1, result.rounds.size)
        assertEquals(14, result.rounds.first().ratingDiff)
    }

    @Test
    fun `encodes the batch-solve request in the documented shape`() {
        val request = PuzzleBatchSolveRequest(solutions = listOf(PuzzleSolution(id = "QBX2O", win = true, rated = true)))
        val encoded = json.encodeToString(PuzzleBatchSolveRequest.serializer(), request)
        assertEquals("""{"solutions":[{"id":"QBX2O","win":true,"rated":true}]}""", encoded)
    }
}
