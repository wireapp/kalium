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

package com.wire.kalium.cryptography

import kotlinx.coroutines.test.runTest
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertTrue

class CoreCryptoCentralTest {

    @Test
    fun givenCoreCryptoCentral_whenExportingDatabaseCopy_thenExportFileIsCreated() = runTest {
        val root = Files.createTempDirectory("cc-export-test").toFile()
        val keyStorePath = root.resolve("keystore").absolutePath
        val exportPath = root.resolve("keystore_export").absolutePath
        val passphrase = ByteArray(32) { 0 }

        try {
            val central = coreCryptoCentral(keyStorePath, passphrase)
            central.exportDatabaseCopy(exportPath)

            val originalDbFile = File("$keyStorePath/keystore")
            val exportedDbFile = File(exportPath)

            assertTrue(originalDbFile.exists())
            assertTrue(exportedDbFile.exists())
            assertTrue(exportedDbFile.length() > 0)
        } finally {
            root.deleteRecursively()
        }
    }
}
