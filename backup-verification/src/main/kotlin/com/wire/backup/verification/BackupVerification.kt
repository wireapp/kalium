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

import com.wire.backup.data.BackupMessage
import com.wire.backup.ingest.BackupImportResult
import com.wire.backup.ingest.MPBackupImporter
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.io.FileInputStream
import java.nio.file.Files
import java.nio.file.Path
import java.util.zip.ZipInputStream
import kotlin.io.path.absolutePathString

@OptIn(ExperimentalSerializationApi::class)
fun main(): Unit = runBlocking {
    val tempDir = Files.createTempDirectory("wireBackupVerification").toFile()
    tempDir.deleteOnExit()
    val android = importBackup(tempDir, "kalium/backup-verification/android.wbu", "")
    val ios = importBackup(tempDir, "kalium/backup-verification/iOS.wbu", "")
    val web = importBackup(tempDir, "kalium/backup-verification/web.wbu", "")

    val androidMessages = getAllMessages(android)
    val webMessages = getAllMessages(web)
    val iosMessages = getAllMessages(ios)
    val json = Json { prettyPrint = true; prettyPrintIndent = "  " }
    File("kalium/android.json").writeText(json.encodeToString(androidMessages))
    File("kalium/web.json").writeText(json.encodeToString(webMessages))
    File("kalium/iOS.json").writeText(json.encodeToString(iosMessages))
}

private fun getAllMessages(android: BackupImportResult.Success): List<BackupMessage> {
    val pager = android.pager.messagesPager
    val content = mutableListOf<BackupMessage>()
    while (pager.hasMorePages()) {
        val currentPage = pager.nextPage()
        content.addAll(currentPage)
    }
    return content
}

private suspend fun importBackup(
    tempFile: File,
    importFilePath: String,
    importPassword: String
): BackupImportResult.Success {
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
    return result
}
