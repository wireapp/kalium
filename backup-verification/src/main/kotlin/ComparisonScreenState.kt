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

import com.wire.backup.ingest.BackupPeekResult
import com.wire.backup.verification.CompleteBackupComparisonResult
import java.io.File

data class ScreenState(
    val peekScreenState: PeekScreenState,
    val comparisonScreenState: ComparisonScreenState? = null
)

sealed interface ComparisonScreenState {
    /**
     * State when files are being analyzed for comparison
     */
    data class AnalyzingFiles(val fileNames: List<String>) : ComparisonScreenState

    /**
     * State when comparison results are available
     */
    data class ResultAvailable(
        val peekResult: PeekScreenState.ResultAvailable,
        val result: CompleteBackupComparisonResult.Success
    ) : ComparisonScreenState
}

/**
 * State for backup peek operation
 */
sealed interface PeekScreenState {
    /**
     * Initial state when no files are selected for peeking
     */
    data object WaitingForFileSelection : PeekScreenState

    /**
     * State when peek results are available
     */
    data class ResultAvailable(
        val result: Map<File, BackupPeekResult>,
        val filePasswords: Map<String, String> = emptyMap(),
        val failedFiles: Set<File> = emptySet()
    ) : PeekScreenState
}
