package com.closeonjae.chesspuzzle

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.closeonjae.chesspuzzle.auth.TokenStore
import com.closeonjae.chesspuzzle.core.lichess.ExplorerMove
import com.closeonjae.chesspuzzle.core.lichess.ExplorerOpening
import com.closeonjae.chesspuzzle.core.lichess.ExplorerResponse
import com.closeonjae.chesspuzzle.core.lichess.LichessApiClient
import com.closeonjae.chesspuzzle.core.lichess.OpeningExplorerClient
import com.closeonjae.chesspuzzle.data.OpeningRepository
import com.closeonjae.chesspuzzle.mode.ModeScreen
import com.closeonjae.chesspuzzle.opening.OpeningScreen
import com.closeonjae.chesspuzzle.opening.OpeningViewModel
import com.closeonjae.chesspuzzle.ui.theme.ChessPuzzleTheme

/**
 * Debug-only entry point (see app/src/debug/AndroidManifest.xml) for
 * screen-checking OpeningScreen without a completed Lichess sign-in — same
 * reason as [DebugPuzzlePreviewActivity]: the real RemoteAuthClient flow needs
 * a paired phone (RESEARCH.md 4절), which an emulator doesn't have.
 *
 * The canned answers are the real Ruy Lopez line (1.e4 e5 2.Nf3 Nc6 3.Bb5) with
 * roughly the shares Lichess reports for it, so the badges, the win/draw/loss
 * bars and the "which move renames the opening" line all render off
 * realistically-shaped data rather than placeholders. Any position the fixture
 * doesn't know answers as out-of-book, which is itself one of the states worth
 * looking at.
 *
 * Starts on the mode picker so that screen gets looked at too, and so the
 * back gesture out of a mode is exercised the same way MainActivity wires it.
 * Both buttons lead to the opening screen here — the puzzle screen has its own
 * preview activity with its own fixture.
 *
 * Launch with: adb shell am start -a com.closeonjae.chesspuzzle.PREVIEW_OPENING
 */
class DebugOpeningPreviewActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val repository = FakeOpeningRepository(
            LichessApiClient(clientId = "debug-preview"),
            OpeningExplorerClient(),
            TokenStore(this),
        )
        setContent {
            ChessPuzzleTheme {
                var opened by remember { mutableStateOf(false) }
                BackHandler(enabled = opened) { opened = false }

                if (opened) {
                    val viewModel: OpeningViewModel = viewModel(
                        factory = viewModelFactory { initializer { OpeningViewModel(repository) } },
                    )
                    OpeningScreen(viewModel = viewModel)
                } else {
                    ModeScreen(onPuzzles = { opened = true }, onOpenings = { opened = true })
                }
            }
        }
    }
}

private fun move(
    uci: String,
    san: String,
    white: Int,
    draws: Int,
    black: Int,
    opening: ExplorerOpening? = null,
) = ExplorerMove(uci, san, averageRating = 1700, white = white, draws = draws, black = black, opening = opening)

private class FakeOpeningRepository(
    api: LichessApiClient,
    explorer: OpeningExplorerClient,
    tokenStore: TokenStore,
) : OpeningRepository(api, explorer, tokenStore) {

    private val canned: Map<String, ExplorerResponse> = mapOf(
        "" to ExplorerResponse(
            opening = null,
            white = 24_600_000, draws = 1_400_000, black = 22_800_000,
            moves = listOf(
                move("e2e4", "e4", 12_100_000, 640_000, 11_000_000),
                move("d2d4", "d4", 6_900_000, 460_000, 6_200_000),
                move("g1f3", "Nf3", 2_100_000, 150_000, 1_900_000),
                move("c2c4", "c4", 1_400_000, 96_000, 1_200_000),
                move("g2g3", "g3", 480_000, 28_000, 430_000),
            ),
        ),
        "e2e4" to ExplorerResponse(
            opening = ExplorerOpening("B00", "King's Pawn Game"),
            white = 12_100_000, draws = 640_000, black = 11_000_000,
            moves = listOf(
                move("c7c5", "c5", 3_600_000, 190_000, 3_300_000, ExplorerOpening("B20", "Sicilian Defense")),
                move("e7e5", "e5", 3_100_000, 180_000, 2_700_000, ExplorerOpening("C20", "King's Pawn Game")),
                move("e7e6", "e6", 1_500_000, 82_000, 1_300_000, ExplorerOpening("C00", "French Defense")),
                move("c7c6", "c6", 1_100_000, 61_000, 940_000, ExplorerOpening("B10", "Caro-Kann Defense")),
                move("d7d5", "d5", 620_000, 30_000, 520_000, ExplorerOpening("B01", "Scandinavian Defense")),
            ),
        ),
        "e2e4,e7e5" to ExplorerResponse(
            opening = ExplorerOpening("C20", "King's Pawn Game"),
            white = 3_100_000, draws = 180_000, black = 2_700_000,
            moves = listOf(
                move("g1f3", "Nf3", 1_500_000, 92_000, 1_300_000, ExplorerOpening("C40", "King's Knight Opening")),
                move("f1c4", "Bc4", 620_000, 30_000, 540_000, ExplorerOpening("C23", "Bishop's Opening")),
                move("b1c3", "Nc3", 380_000, 21_000, 340_000, ExplorerOpening("C25", "Vienna Game")),
                move("f2f4", "f4", 290_000, 14_000, 250_000, ExplorerOpening("C30", "King's Gambit")),
                move("d2d4", "d4", 130_000, 7_600, 110_000, ExplorerOpening("C21", "Center Game")),
            ),
        ),
        "e2e4,e7e5,g1f3" to ExplorerResponse(
            opening = ExplorerOpening("C40", "King's Knight Opening"),
            white = 1_500_000, draws = 92_000, black = 1_300_000,
            moves = listOf(
                move("b8c6", "Nc6", 940_000, 58_000, 820_000, ExplorerOpening("C44", "King's Pawn Game: Normal Variation")),
                move("g8f6", "Nf6", 240_000, 16_000, 210_000, ExplorerOpening("C42", "Russian Game")),
                move("d7d6", "d6", 190_000, 11_000, 160_000, ExplorerOpening("C41", "Philidor Defense")),
                move("f7f5", "f5", 61_000, 3_100, 48_000, ExplorerOpening("C40", "Latvian Gambit")),
                move("f8c5", "Bc5", 26_000, 1_400, 23_000),
            ),
        ),
        "e2e4,e7e5,g1f3,b8c6" to ExplorerResponse(
            opening = ExplorerOpening("C44", "King's Pawn Game: Normal Variation"),
            white = 940_000, draws = 58_000, black = 820_000,
            moves = listOf(
                move("f1b5", "Bb5", 310_000, 21_000, 270_000, ExplorerOpening("C60", "Ruy Lopez")),
                move("f1c4", "Bc4", 280_000, 16_000, 250_000, ExplorerOpening("C50", "Italian Game")),
                move("d2d4", "d4", 140_000, 9_100, 120_000, ExplorerOpening("C44", "Scotch Game")),
                move("b1c3", "Nc3", 96_000, 6_200, 84_000, ExplorerOpening("C46", "Three Knights Opening")),
                move("f3e5", "Nxe5", 34_000, 1_500, 41_000),
            ),
        ),
        // The design mockup's own position — its five candidates are the ones
        // drawn on the board there, so the badges can be compared side by side.
        "e2e4,e7e5,g1f3,b8c6,f1b5" to ExplorerResponse(
            opening = ExplorerOpening("C60", "Ruy Lopez"),
            white = 310_000, draws = 21_000, black = 270_000,
            moves = listOf(
                move("a7a6", "a6", 128_000, 8_800, 112_000, ExplorerOpening("C70", "Ruy Lopez: Morphy Defense")),
                move("g8f6", "Nf6", 59_000, 4_100, 52_000, ExplorerOpening("C65", "Ruy Lopez: Berlin Defense")),
                move("f8c5", "Bc5", 28_000, 1_600, 25_000, ExplorerOpening("C64", "Ruy Lopez: Classical Variation")),
                move("d7d6", "d6", 22_000, 1_300, 18_000, ExplorerOpening("C62", "Ruy Lopez: Steinitz Defense")),
                move("g8e7", "Nge7", 18_000, 990, 16_000, ExplorerOpening("C60", "Ruy Lopez: Cozio Defense")),
                move("f7f5", "f5", 9_400, 420, 8_800, ExplorerOpening("C63", "Ruy Lopez: Schliemann Defense")),
                move("c6d4", "Nd4", 7_100, 380, 6_400, ExplorerOpening("C61", "Ruy Lopez: Bird Variation")),
            ),
        ),
        // Three taps from the start (e4, c5, Nf3) and carrying a deliberately
        // long name, so the top label's shrink-then-ellipsize path can be
        // looked at. The name is a real ECO one but belongs a few moves deeper
        // than this position — it is here for the layout, not the chess.
        "e2e4,c7c5,g1f3" to ExplorerResponse(
            opening = ExplorerOpening("B90", "Sicilian Defense: Najdorf Variation, English Attack"),
            white = 1_900_000, draws = 100_000, black = 1_700_000,
            moves = listOf(
                move("d7d6", "d6", 900_000, 48_000, 820_000),
                move("b8c6", "Nc6", 520_000, 29_000, 470_000),
                move("e7e6", "e6", 380_000, 21_000, 350_000),
                move("g8f6", "Nf6", 61_000, 3_400, 55_000),
                move("g7g6", "g6", 39_000, 2_100, 36_000),
            ),
        ),
        // Deliberately named but with no games recorded, to exercise the
        // "Sicilian Defense: Najdorf" long-name label against a real position.
        "e2e4,c7c5" to ExplorerResponse(
            opening = ExplorerOpening("B20", "Sicilian Defense"),
            white = 3_600_000, draws = 190_000, black = 3_300_000,
            moves = listOf(
                move("g1f3", "Nf3", 1_900_000, 100_000, 1_700_000),
                move("b1c3", "Nc3", 620_000, 32_000, 570_000, ExplorerOpening("B23", "Sicilian Defense: Closed")),
                move("c2c3", "c3", 340_000, 19_000, 300_000, ExplorerOpening("B22", "Sicilian Defense: Alapin Variation")),
                move("d2d4", "d4", 210_000, 9_800, 200_000),
                move("f2f4", "f4", 130_000, 6_400, 120_000, ExplorerOpening("B21", "Sicilian Defense: Grand Prix Attack")),
            ),
        ),
    )

    /** Anything the fixture doesn't cover renders as out-of-book — one of the states worth screen-checking. */
    private val outOfBook = ExplorerResponse()

    override suspend fun explore(play: String): Result<ExplorerResponse> =
        Result.success(canned[play] ?: outOfBook)
}
