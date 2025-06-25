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

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.Button
import androidx.compose.material.Card
import androidx.compose.material.Divider
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wire.backup.ingest.BackupPeekResult
import com.wire.backup.verification.peekBackupFiles
import io.github.vinceglb.filekit.FileKit
import io.github.vinceglb.filekit.dialogs.FileKitMode
import io.github.vinceglb.filekit.dialogs.FileKitType
import io.github.vinceglb.filekit.dialogs.openFilePicker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File
import java.nio.file.Paths

@Composable
fun FileSelectionScreen(
    screenState: ScreenState,
    onScreenStateChange: (ScreenState) -> Unit,
    onCompare: (peekState: PeekScreenState.ResultAvailable, files: List<File>) -> Unit
) {
    val peekState = screenState.peekScreenState

    Column(modifier = Modifier.padding(16.dp)) {
        // Display header and basic app info
        Text(
            "Wire Backup Verifier",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        Text(
            "This tool compares Wire backup files (.wbu) to identify differences in messages between backups.",
            fontSize = 16.sp,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        // Display the file selection card
        FileSelectionCard(
            screenState = screenState,
            peekState = peekState,
            onScreenStateChange = onScreenStateChange,
            onPasswordChange = { filePath, password ->
                if (peekState is PeekScreenState.ResultAvailable) {
                    // Create a new map with the updated password
                    val updatedPasswords = peekState.filePasswords.toMutableMap().apply {
                        put(filePath, password)
                    }

                    // Update the screen state with the new passwords, preserving failedFiles
                    onScreenStateChange(
                        screenState.copy(
                            peekScreenState = peekState.copy(
                                filePasswords = updatedPasswords
                                // failedFiles will be preserved by copy()
                            )
                        )
                    )
                }
            }
        )

        // Show compare button if we have results with at least 2 files
        if (peekState is PeekScreenState.ResultAvailable) {
            SelectedFilesList(peekState, onCompare)
        }
    }
}

@Suppress("CyclomaticComplexMethod")
@Composable
private fun FileSelectionCard(
    screenState: ScreenState,
    peekState: PeekScreenState,
    onScreenStateChange: (ScreenState) -> Unit,
    onPasswordChange: (String, String) -> Unit
) {
    val scope = rememberCoroutineScope()

    // Function to peek backup files and update the state
    fun peekFiles(files: List<File>) {
        scope.launch(Dispatchers.IO) {
            val peekResults = peekBackupFiles(files)

            // Preserve existing passwords and failed files when updating peek results
            val (existingPasswords, existingFailedFiles) = if (peekState is PeekScreenState.ResultAvailable) {
                peekState.filePasswords to peekState.failedFiles
            } else {
                emptyMap<String, String>() to emptySet<File>()
            }

            onScreenStateChange(
                screenState.copy(
                    peekScreenState = PeekScreenState.ResultAvailable(
                        result = peekResults,
                        filePasswords = existingPasswords,
                        failedFiles = existingFailedFiles
                    )
                )
            )
        }
    }

    // Function to add new backup files
    fun addFiles() {
        scope.launch(Dispatchers.IO) {
            val result = FileKit.openFilePicker(
                type = FileKitType.File(listOf("wbu")),
                FileKitMode.Multiple()
            )
            result?.let { fileResults ->
                if (fileResults.isNotEmpty()) {
                    val newFiles = fileResults.map { it.file }

                    // Get current selected files
                    val currentFiles = when (val currentPeekState = screenState.peekScreenState) {
                        is PeekScreenState.ResultAvailable -> currentPeekState.result.keys.toList()
                        else -> emptyList()
                    }

                    val updatedFiles = (currentFiles + newFiles).distinct()

                    if (updatedFiles.isNotEmpty()) {
                        peekFiles(updatedFiles)
                    }
                }
            }
        }
    }

    // Function to remove a backup file
    fun removeFile(file: File) {
        // Get current files
        val currentPeekState = screenState.peekScreenState
        if (currentPeekState is PeekScreenState.ResultAvailable) {
            val updatedFiles = currentPeekState.result.keys.toList().filter { it != file }

            if (updatedFiles.isNotEmpty()) {
                peekFiles(updatedFiles)
            } else {
                // No files left, reset to waiting state
                onScreenStateChange(
                    screenState.copy(
                        peekScreenState = PeekScreenState.WaitingForFileSelection,
                        comparisonScreenState = null // Clear comparison state when no files
                    )
                )
            }
        }
    }

    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        elevation = 4.dp
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Selected Backup Files",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )

                Button(onClick = { addFiles() }) {
                    Text("Add Files")
                }
            }

            Spacer(Modifier.size(8.dp))

            // Get files from peek state if available
            val selectedFiles = if (peekState is PeekScreenState.ResultAvailable) {
                peekState.result.keys.toList()
            } else {
                emptyList()
            }

            if (selectedFiles.isEmpty()) {
                Text(
                    "No backup files selected. Please add backup files to continue.",
                    color = Color.Gray,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            } else {
                FileList(
                    selectedFiles = selectedFiles,
                    onRemoveFile = { removeFile(it) },
                    peekState = peekState,
                    onPasswordChange = onPasswordChange,
                    failedFiles = if (peekState is PeekScreenState.ResultAvailable) peekState.failedFiles else emptySet()
                )
            }
        }
    }
}

@Composable
private fun SelectedFilesList(
    peekState: PeekScreenState.ResultAvailable,
    onCompare: (peekState: PeekScreenState.ResultAvailable, files: List<File>) -> Unit
) {
    val fileCount = peekState.result.keys.size
    if (fileCount >= 2) {
        Spacer(Modifier.size(16.dp))
        Button(
            onClick = {
                onCompare(peekState, peekState.result.keys.toList())
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Run Backup Comparison", fontSize = 16.sp)
        }
    } else {
        Text(
            "Add at least two backup files to compare",
            color = Color.Gray,
            modifier = Modifier.padding(vertical = 8.dp)
        )
    }
}

@Composable
private fun FileList(
    selectedFiles: List<File>,
    onRemoveFile: (File) -> Unit,
    peekState: PeekScreenState,
    onPasswordChange: (String, String) -> Unit,
    failedFiles: Set<File> = emptySet()
) {
    LazyColumn(modifier = Modifier.fillMaxWidth()) {
        items(selectedFiles) { file ->
            FileItem(selectedFiles, file, failedFiles, onRemoveFile, peekState, onPasswordChange)
        }
    }
}

@Composable
private fun FileItem(
    selectedFiles: List<File>,
    file: File,
    failedFiles: Set<File>,
    onRemoveFile: (File) -> Unit,
    peekState: PeekScreenState,
    onPasswordChange: (String, String) -> Unit
) {
    if (selectedFiles.indexOf(file) > 0) {
        Spacer(modifier = Modifier.size(8.dp))
        Divider(thickness = 2.dp)
        Spacer(modifier = Modifier.size(8.dp))
    }

    val fileName = Paths.get(file.absolutePath).fileName.toString()
    val filePath = file.absolutePath

    Column(modifier = Modifier.padding(12.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Check if this file is in the failedFiles set
                    if (failedFiles.contains(file)) {
                        Icon(
                            Icons.AutoMirrored.Default.ArrowBack,
                            contentDescription = "Import failed",
                            tint = Color.Red,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.size(8.dp))
                    }
                    Text(
                        fileName,
                        fontWeight = FontWeight.Bold
                    )
                }
                Text(
                    filePath,
                    fontSize = 12.sp,
                    color = Color.Gray
                )
            }

            IconButton(
                onClick = { onRemoveFile(file) },
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = "Remove file",
                    tint = Color.Red
                )
            }
        }

        // Show file details if available
        if (peekState is PeekScreenState.ResultAvailable) {
            val fileResult = peekState.result[file]
            if (fileResult != null) {
                Spacer(Modifier.size(8.dp))
                Divider()
                Spacer(Modifier.size(8.dp))

                when (fileResult) {
                    is BackupPeekResult.Success -> {
                        Row(modifier = Modifier.padding(vertical = 2.dp)) {
                            Text("Version: ", fontWeight = FontWeight.Medium)
                            Text(fileResult.version)
                        }
                        Row(modifier = Modifier.padding(vertical = 2.dp)) {
                            Text("Encrypted: ", fontWeight = FontWeight.Medium)
                            Text(fileResult.isEncrypted.toString())
                        }

                        // Add password field if the file is encrypted
                        if (fileResult.isEncrypted) {
                            Spacer(Modifier.size(8.dp))

                            // Get current password or empty string if not set
                            val currentPassword = peekState.filePasswords[filePath] ?: ""

                            OutlinedTextField(
                                value = currentPassword,
                                onValueChange = { newPassword ->
                                    onPasswordChange(filePath, newPassword)
                                },
                                label = { Text("Password") },
                                placeholder = { Text("Enter password for this backup") },
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }

                    is BackupPeekResult.Failure.UnsupportedVersion -> {
                        Text(
                            "Unsupported Version: ${fileResult.backupVersion}",
                            color = Color.Red
                        )
                    }

                    is BackupPeekResult.Failure.UnknownFormat -> {
                        Text(
                            "Unknown Format",
                            color = Color.Red
                        )
                    }
                }
            }
        }
    }
}
