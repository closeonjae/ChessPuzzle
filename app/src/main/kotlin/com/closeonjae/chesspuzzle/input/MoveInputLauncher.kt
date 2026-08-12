package com.closeonjae.chesspuzzle.input

import android.app.RemoteInput
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.wear.input.RemoteInputIntentHelper

private const val RESULT_KEY = "move_input"

/**
 * Opens the watch's system input chooser (default keyboard, dictation,
 * handwriting — RESEARCH.md 10-A절, via `RemoteInputIntentHelper`) and
 * returns the typed text (expected format: SAN, e.g. "Nc3" — DESIGN.md 5절)
 * to [onResult], or null if the user cancelled without entering anything.
 *
 * Uses the platform `android.app.RemoteInput`, not the androidx compat
 * version — `RemoteInputIntentHelper.putRemoteInputsExtra` specifically
 * requires `List<android.app.RemoteInput>` (confirmed by the compiler, not
 * assumed).
 */
@Composable
fun rememberMoveInputLauncher(onResult: (String?) -> Unit): () -> Unit {
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        val text = result.data
            ?.let { RemoteInput.getResultsFromIntent(it) }
            ?.getCharSequence(RESULT_KEY)
            ?.toString()
        onResult(text)
    }
    return {
        val intent = RemoteInputIntentHelper.createActionRemoteInputIntent()
        val remoteInputs = listOf(
            RemoteInput.Builder(RESULT_KEY).setLabel("Move (e.g. Nc3)").build(),
        )
        RemoteInputIntentHelper.putRemoteInputsExtra(intent, remoteInputs)
        launcher.launch(intent)
    }
}
