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
package com.wire.backup.verification

import com.wire.backup.data.BackupConversation
import com.wire.backup.data.BackupMessage
import com.wire.backup.data.BackupUser
import com.wire.backup.ingest.BackupImportResult
import com.wire.backup.ingest.ImportResultPager
import com.wire.backup.ingest.MPBackupImporter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.nio.file.Files
import java.nio.file.Path
import java.util.zip.ZipInputStream
import kotlin.io.path.absolutePathString

suspend fun compareBackupFiles(backupFiles: List<File>): CompleteBackupComparisonResult = coroutineScope {
    withContext(Dispatchers.IO) {
        val tempDir = Files.createTempDirectory("wireBackupVerification").toFile()
        tempDir.deleteOnExit()
        val imports = backupFiles.map {
            async {
                importBackup(tempDir, it.absolutePath, "")
            }
        }.awaitAll()
        compareBackupMessages(imports)
    }
}

class BackupImport(val backupId: BackupId, private val importResult: ImportResultPager) {
    val allMessages: List<BackupMessage> by lazy {
        val pager = importResult.messagesPager
        val content = mutableListOf<BackupMessage>()
        while (pager.hasMorePages()) {
            val currentPage = pager.nextPage()
            content.addAll(currentPage)
        }
        content
    }

    val allConversations: List<BackupConversation> by lazy {
        val pager = importResult.conversationsPager
        val content = mutableListOf<BackupConversation>()
        while (pager.hasMorePages()) {
            val currentPage = pager.nextPage()
            content.addAll(currentPage)
        }
        content
    }

    val allUsers: List<BackupUser> by lazy {
        val pager = importResult.usersPager
        val content = mutableListOf<BackupUser>()
        while (pager.hasMorePages()) {
            val currentPage = pager.nextPage()
            content.addAll(currentPage)
        }
        content
    }
}

internal suspend fun importBackup(
    tempFile: File,
    importFilePath: String,
    importPassword: String
): BackupImport {
    val importFile = File(importFilePath)
    val unzipDir = File(tempFile, "unzipped-${importFile.name}")
    unzipDir.mkdirs()
    val unzipPath = unzipDir.toPath()
    val workPath = File(tempFile, "work-${importFile.name}")
    val importer = MPBackupImporter(workPath.absolutePath) { path ->
        ZipInputStream(FileInputStream(path)).use { zipInputStream ->
            var ze = zipInputStream.nextEntry
            while (ze != null) {
                val resolvedPath: Path = unzipPath.resolve(ze.name).normalize()
                if (ze.isDirectory) {
                    Files.createDirectories(resolvedPath)
                } else {
                    Files.createDirectories(resolvedPath.parent)
                    Files.copy(zipInputStream, resolvedPath)
                }
                ze = zipInputStream.nextEntry
            }
        }
        unzipPath.absolutePathString()
    }
    val result = importer.importFromFile(importFile.absolutePath, importPassword)
    require(result is BackupImportResult.Success)
    return BackupImport(BackupId(importFile.nameWithoutExtension), result.pager)
}
