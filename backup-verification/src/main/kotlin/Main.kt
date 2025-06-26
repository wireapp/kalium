/*
 * Wire
 * Copyright (C) 2025 Wire Swiss GmbH
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see http://www.gnu.org/licenses/.
 */

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.wire.backup.verification.CompleteBackupComparisonResult
import com.wire.backup.verification.compareBackupFiles
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File

fun main(): Unit = application {
    var screenState by remember {
        mutableStateOf(
            ScreenState(peekScreenState = PeekScreenState.WaitingForFileSelection)
        )
    }
    val coroutineScope = rememberCoroutineScope()
    // Track which screen is currently active
    var currentScreen by remember { mutableStateOf(AppScreen.FILE_SELECTION) }

    fun compareFiles(peekResult: PeekScreenState.ResultAvailable, files: List<File>) {
        // Clear any previously failed files before starting a new comparison
        val updatedPeekResult = peekResult.copy(failedFiles = emptySet())

        // Update state with analysing files status and clear failed files
        screenState = screenState.copy(
            peekScreenState = updatedPeekResult,
            comparisonScreenState = ComparisonScreenState.AnalyzingFiles(files.map { it.absolutePath })
        )

        // Notify to navigate to comparison screen
        currentScreen = AppScreen.COMPARISON_RESULT

        coroutineScope.launch(Dispatchers.IO) {
            val result = compareBackupFiles(files, updatedPeekResult.filePasswords)
            when (result) {
                is CompleteBackupComparisonResult.Success -> {
                    screenState = screenState.copy(
                        comparisonScreenState = ComparisonScreenState.ResultAvailable(
                            // Use the updated peekResult with cleared failedFiles
                            peekResult = (screenState.peekScreenState as PeekScreenState.ResultAvailable),
                            result = result
                        )
                    )
                }

                is CompleteBackupComparisonResult.Failure -> {
                    // Return to file selection screen with failed files info
                    currentScreen = AppScreen.FILE_SELECTION

                    // Update the peek result to include failed files
                    val updatedPeekState = when (val currentPeekState = screenState.peekScreenState) {
                        is PeekScreenState.ResultAvailable -> currentPeekState.copy(
                            failedFiles = result.errors.toSet()
                        )

                        else -> currentPeekState
                    }

                    screenState = screenState.copy(
                        peekScreenState = updatedPeekState,
                        comparisonScreenState = null
                    )
                }
            }
        }
    }

    Window(
        onCloseRequest = ::exitApplication,
        title = "Wire Backup Verifier",
        state = rememberWindowState(
            width = 1024.dp,
            height = 768.dp
        )
    ) {
        // Switch between screens based on currentScreen
        when (currentScreen) {
            AppScreen.FILE_SELECTION -> {
                FileSelectionScreen(
                    screenState = screenState,
                    onScreenStateChange = { newState ->
                        screenState = newState
                    },
                    onCompare = { peekState, files ->
                        compareFiles(peekState, files)
                    }
                )
            }

            AppScreen.COMPARISON_RESULT -> {
                ComparisonResultScreen(
                    comparisonState = screenState.comparisonScreenState,
                    onBackClick = {
                        currentScreen = AppScreen.FILE_SELECTION
                    }
                )
            }
        }
    }
}
