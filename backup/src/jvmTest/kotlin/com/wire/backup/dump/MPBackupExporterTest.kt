/*
 * Wire
 * Copyright (C) 2024 Wire Swiss GmbH
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
package com.wire.backup.dump

import com.wire.backup.data.BackupQualifiedId
import com.wire.backup.data.BackupUser
import kotlinx.coroutines.runBlocking
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.random.Random
import kotlin.random.nextInt
import kotlin.test.Test

class MPBackupExporterTest {

    fun createZipFile(files: List<File>, outputZipFile: File): File {
        ZipOutputStream(FileOutputStream(outputZipFile)).use { zipOut ->
            files.forEach { file ->
                FileInputStream(file).use { fis ->
                    val zipEntry = ZipEntry(file.name)
                    zipOut.putNextEntry(zipEntry)
                    fis.copyTo(zipOut)
                    zipOut.closeEntry()
                }
            }
        }
        return outputZipFile
    }

    @Test
    fun e() = runBlocking {
        val userId = BackupQualifiedId("user", "domain")
        val subject = MPBackupExporter(userId, "TEST", "TEST-OUTPUT") { entries ->
            val files = entries.map { File(it) }
            val outputFile = File("zippedThingy.zip")
            createZipFile(files, outputFile)
            outputFile.absolutePath
        }

        fun randomUser(): BackupUser = Random.nextInt().let { random ->
            BackupUser(
                BackupQualifiedId("ID!$random", "domain"),
                "NAME!$random",
                "HANDLE!$random"
            )
        }
        repeat(2_001) {
            subject.add(randomUser())
        }
        val output = subject.finalize(null)
        println("EXPORTED TO $output")
    }
}
