/*
 * Wire
 * Copyright (C) 2026 Wire Swiss GmbH
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

package com.wire.kalium.logic.util

import com.wire.kalium.common.functional.Either
import com.wire.kalium.logic.data.asset.FakeKaliumFileSystem
import okio.Buffer
import okio.Path
import okio.buffer
import okio.use
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class BackupUtilsCompatibilityTest {

    @Test
    fun givenAppleZipFixture_whenExtracting_thenFilesAreDecoded() {
        assertZipFixtureExtracts(APPLE_ZIP_FIXTURE.hexToByteArray())
    }

    @Test
    fun givenJvmZipFixture_whenExtracting_thenFilesAreDecoded() {
        assertZipFixtureExtracts(JVM_ZIP_FIXTURE.hexToByteArray())
    }

    private fun assertZipFixtureExtracts(fixture: ByteArray) {
        val fileSystem = FakeKaliumFileSystem()
        val outputPath = fileSystem.rootCachePath / "zip-compatibility"
        fileSystem.createDirectories(outputPath)

        val result = extractCompressedFile(
            inputSource = Buffer().write(fixture),
            outputRootPath = outputPath,
            param = ExtractFilesParam.All,
            fileSystem = fileSystem,
        )

        assertIs<Either.Right<Long>>(result)
        assertEquals("hello from zip fixture", fileSystem.readUtf8(outputPath / "payload-a.txt"))
        assertEquals("""{"ok":true}""", fileSystem.readUtf8(outputPath / "payload-b.json"))
    }

    private fun FakeKaliumFileSystem.readUtf8(path: Path): String =
        source(path).buffer().use { it.readUtf8() }

    private fun String.hexToByteArray(): ByteArray =
        chunked(HEX_CHARS_PER_BYTE)
            .map { it.toInt(HEX_RADIX).toByte() }
            .toByteArray()

    private companion object {
        private const val HEX_CHARS_PER_BYTE = 2
        private const val HEX_RADIX = 16

        // ZIP fixture generated with the Apple writer shape: raw DEFLATE entries with sizes and CRCs in local headers.
        private const val APPLE_ZIP_FIXTURE =
            "504b030414000008080000000000001e5f1a18000000160000000d0000007061796c6f61642d612e747874" +
                    "cb48cdc9c957482bcacf55a8ca2c5048cbac28292d4a0500504b030414000008080000000000905fd4a7" +
                    "0d0000000b0000000e0000007061796c6f61642d622e6a736f6eab56cacf56b22a292a4dad0500504b" +
                    "0102140314000008080000000000001e5f1a18000000160000000d000000000000000000000000000000" +
                    "00007061796c6f61642d612e747874504b0102140314000008080000000000905fd4a70d0000000b00" +
                    "00000e00000000000000000000000000430000007061796c6f61642d622e6a736f6e504b0506000000" +
                    "0002000200770000007c0000000000"

        // ZIP fixture generated with java.util.zip.ZipOutputStream on JDK 21.
        private const val JVM_ZIP_FIXTURE =
            "504b030414000808080063bfa85c0000000000000000000000000d0000007061796c6f61642d612e747874" +
                    "cb48cdc9c957482bcacf55a8ca2c5048cbac28292d4a0500504b0708001e5f1a1800000016000000" +
                    "504b030414000808080063bfa85c0000000000000000000000000e0000007061796c6f61642d622e6a" +
                    "736f6eab56cacf56b22a292a4dad0500504b0708905fd4a70d0000000b000000504b010214001400" +
                    "0808080063bfa85c001e5f1a18000000160000000d0000000000000000000000000000000000706179" +
                    "6c6f61642d612e747874504b0102140014000808080063bfa85c905fd4a70d0000000b0000000e00" +
                    "000000000000000000000000530000007061796c6f61642d622e6a736f6e504b0506000000000200" +
                    "0200770000009c0000000000"
    }
}
