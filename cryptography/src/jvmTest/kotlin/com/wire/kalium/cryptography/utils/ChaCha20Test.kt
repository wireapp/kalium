package com.wire.kalium.cryptography.utils

import okio.Path.Companion.toPath
import okio.fakefilesystem.FakeFileSystem
import org.junit.Test
import java.security.SecureRandom
import kotlin.test.assertTrue

internal class ChaCha20Test {

    @Test
    fun `test chacha20`() {
        val fakeData = "Some file data to be encrypted".toByteArray()
        val arrangement = Arrangement()
            .arrange()

        with(arrangement) {
            val inputPath = "$rootPath/test-data.txt".toPath()
            val outputPath = "$rootPath/test-data.cc20".toPath()
            fakeFileSystem.write(inputPath) {
                write(fakeData)
            }
            val inputDataSource = fakeFileSystem.source(inputPath)
            val outputSink = fakeFileSystem.sink(outputPath)

            val key = ChaCha20().generateRandomChaCha20Key()

            val salt = ByteArray(12)
            SecureRandom().nextBytes(salt)
            val hashedUserId = PlainData(calcMd5("some-user-id".toByteArray()).toByteArray())
            val outputSize = ChaCha20().encryptFile(inputDataSource, outputSink, key, PlainData(salt), hashedUserId)
            assertTrue(outputSize > 1)
        }
    }

//     @Test
//     fun `test chacha20 with counter`() {
//         val key = ChaCha20Key("0000000000000000000000000000000000000000000000000000000000000000".hexToBytes())
//         val nonce = "0000000000000000".hexToBytes()
//         val counter = 1
//         val input = "0000000000000000000000000000000000000000000000000000000000000000".hexToBytes()
//         val expected = "aeb1e4d5f2f0d9c3c3e9c80d6b3f0b38".hexToBytes()
//         val output = ChaCha20.encrypt(input, key, nonce, counter)
//         assertArrayEquals(expected, output)
//     }
//
//     @Test
//     fun `test chacha20 with counter and nonce`() {
//         val key = ChaCha20Key("0000000000000000000000000000000000000000000000000000000000000000".hexToBytes())
//         val nonce = "0000000000000001".hexToBytes()
//         val counter = 1
//         val input = "0000000000000000000000000000000000000000000000000000000000000000".hexToBytes()
//         val expected = "aeb1e4d5f2f0d9c3c3e9c80d6b3f0b38".hexToBytes()
//         val output = ChaCha20.encrypt(input, key, nonce, counter)
//         assertArrayEquals(expected, output)
//     }

    class Arrangement {
        val fakeFileSystem = FakeFileSystem()
        val rootPath = "/Users/me".toPath()

        init {
            fakeFileSystem.createDirectories(rootPath)
        }

        fun arrange(): Arrangement {
            return this
        }
    }
}
