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
import com.wire.backup.ingest.BackupPeekResult
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

private val tempDir by lazy {
    Files.createTempDirectory("wireBackupVerification").toFile().also {
        it.deleteOnExit()
    }
}

suspend fun compareBackupFiles(
    backupFiles: List<File>,
    passwords: Map<String, String> = emptyMap()
): CompleteBackupComparisonResult = coroutineScope {
    tempDir.listFiles().forEach { it.deleteRecursively() }
    withContext(Dispatchers.IO) {
        var failures = mutableListOf<File>()
        val imports = backupFiles.map { file ->
            async {
                // Use file-specific password if available, otherwise use empty string
                val filePassword = passwords[file.absolutePath] ?: ""
                val importResult = importBackup(file.absolutePath, filePassword)
                if (importResult == null) {
                    failures.add(file)
                }
                importResult
            }
        }.awaitAll()
        val successList = imports.filterNotNull()
        if (successList.size == imports.size) {
            compareBackupMessages(successList)
        } else {
            CompleteBackupComparisonResult.Failure(failures)
        }
    }
}

suspend fun peekBackupFiles(backupFiles: List<File>): Map<File, BackupPeekResult> = coroutineScope {
    withContext(Dispatchers.IO) {
        backupFiles.map { file ->
            async {
                file to importerForFile(file).peekBackupFile(file.absolutePath)
            }
        }.awaitAll().toMap()
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
    importFilePath: String,
    importPassword: String
): BackupImport? {
    val importFile = File(importFilePath)
    val importer = importerForFile(importFile)
    return when (val result = importer.importFromFile(importFile.absolutePath, importPassword)) {
        is BackupImportResult.Failure -> {
            null
        }

        is BackupImportResult.Success -> {
            BackupImport(BackupId(importFile.nameWithoutExtension), result.pager)
        }
    }
}

private fun importerForFile(
    importFile: File
): MPBackupImporter {
    val unzipDir = File(tempDir, "unzipped-${importFile.name}")
    unzipDir.mkdirs()
    val unzipPath = unzipDir.toPath()
    val workPath = File(tempDir, "work-${importFile.name}")
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
    return importer
}
