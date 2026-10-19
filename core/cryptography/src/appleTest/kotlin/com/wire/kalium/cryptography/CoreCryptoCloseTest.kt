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

@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.wire.kalium.cryptography

import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.allocArray
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.toKString
import platform.posix.F_GETPATH
import platform.posix.PATH_MAX
import platform.posix.fcntl
import platform.posix.getdtablesize
import kotlin.io.encoding.Base64
import com.wire.crypto.CoreCryptoContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.test.runTest
import platform.Foundation.NSFileManager
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSUUID

class CoreCryptoCloseTest {
    @Test
    fun transactionCheckpointCommitsRollsBackAndSurvivesReopen() = runTest {
        val root = "${NSTemporaryDirectory()}/kalium-checkpoint-${NSUUID().UUIDString}"
        val committed = "committed-recovery".encodeToByteArray()
        var central = coreCryptoCentral(root, ByteArray(32) { 7 }) as CoreCryptoCentralImpl
        try {
            central.transaction("initial-checkpoint") {
                kotlin.test.assertNull(it.getData())
                it.setData(committed)
                kotlin.test.assertContentEquals(committed, it.getData())
            }
            assertFailsWith<IllegalArgumentException> {
                central.transaction("aborted-checkpoint") {
                    it.setData("aborted-recovery".encodeToByteArray())
                    throw IllegalArgumentException("Abort checkpoint and crypto transaction")
                }
            }
            central.transaction("checkpoint-after-rollback") {
                kotlin.test.assertContentEquals(committed, it.getData())
            }
            central.close()
            central = coreCryptoCentral(root, ByteArray(32) { 7 }) as CoreCryptoCentralImpl
            central.transaction("checkpoint-after-reopen") {
                kotlin.test.assertContentEquals(committed, it.getData())
            }
        } finally {
            central.close()
            NSFileManager.defaultManager.removeItemAtPath(root, null)
        }
    }


    @Test
    fun givenStoredPrekeys_whenReopeningProteus_thenAutomaticIdsDoNotRepeat() = runTest {
        val root = "${NSTemporaryDirectory()}/kalium-prekeys-${NSUUID().UUIDString}"
        val key = ByteArray(32) { 7 }
        var central = coreCryptoCentral(root, key)
        try {
            val client = central.proteusClient()
            val firstBatch = client.newPreKeysAuto(10)
            val secondBatch = client.newPreKeysAuto(10)
            central.close()
            central = coreCryptoCentral(root, key)
            val reopenedBatch = central.proteusClient().newPreKeysAuto(10)

            assertEquals(10, firstBatch.size)
            assertEquals(10, secondBatch.size)
            assertEquals(10, reopenedBatch.size)
            assertEquals(30, (firstBatch + secondBatch + reopenedBatch).map { it.id }.toSet().size)
        } finally {
            central.close()
            NSFileManager.defaultManager.removeItemAtPath(root, null)
        }
    }

    @Test
    fun reinitializingAndClosingMLSReleasesNativeFiles() = runTest {
        val root = "${NSTemporaryDirectory()}/kalium-mls-close-${NSUUID().UUIDString}"
        val central = coreCryptoCentral(root, ByteArray(32) { 7 })
        try {
            suspend fun openClient() = central.mlsClient(
                CryptoQualifiedClientId("abc123", CryptoUserID("fcbe2b38-c66a-4fbe-9e54-6fab39c017a4", "wire.test")),
                MLSCiphersuite.DEFAULT,
                object : MLSTransporter {
                    override suspend fun sendCommitBundle(commitBundle: CommitBundle) = Unit
                },
                object : MLSEpochObserver {
                    override suspend fun onEpochChange(groupId: MLSGroupId, epoch: ULong) = Unit
                },
                backgroundScope
            )
            val client = openClient()
            client.initializeBasicCredential()
            client.getPublicKey()
            client.transaction("create-group") {
                it.createConversation(
                    Base64.encode("close-test-group".encodeToByteArray()),
                    Base64.decode("GX9rI+FshTLGq8g4+s1ep4m+DHaykgM0A5v6iz02jWE=")
                )
            }
            val replacement = openClient()
            assertTrue(replacement.transaction("read-persisted-group") {
                it.conversationExists(Base64.encode("close-test-group".encodeToByteArray()))
            })
            assertTrue(openStoreFiles(root).isNotEmpty(), "Open store must be visible to descriptor inspection: $root")
        } finally {
            central.close()
            assertEquals(emptyList(), openStoreFiles(root))
            NSFileManager.defaultManager.removeItemAtPath(root, null)
        }
    }

    @Test
    fun transactionClosesNativeContextAfterSuccessAndFailure() = runTest {
        val root = "${NSTemporaryDirectory()}/kalium-context-close-${NSUUID().UUIDString}"
        val central = coreCryptoCentral(root, ByteArray(32) { 7 }) as CoreCryptoCentralImpl
        try {
            lateinit var completed: CoreCryptoContext
            central.transaction("completed") {
                completed = it
                it.proteusInit()
            }
            assertFailsWith<IllegalStateException> { completed.proteusFingerprint() }

            lateinit var aborted: CoreCryptoContext
            assertFailsWith<IllegalArgumentException> {
                central.transaction("aborted") {
                    aborted = it
                    throw IllegalArgumentException("Abort the transaction")
                }
            }
            assertFailsWith<IllegalStateException> { aborted.proteusFingerprint() }
            // Closing the callback handle must leave the owning client usable.
            central.transaction("after-abort") { assertTrue(it.proteusFingerprint().isNotEmpty()) }
        } finally {
            central.close()
            NSFileManager.defaultManager.removeItemAtPath(root, null)
        }
    }

    @Test
    fun cancelledCallerClosesRealProteusStoreAndReopenDecryptsNextCiphertext() = runTest {
        val root = "${NSTemporaryDirectory()}/kalium-close-${NSUUID().UUIDString}"
        val key = ByteArray(32) { 7 }
        val sender = coreCryptoCentral("$root/sender", key)
        var receiver = coreCryptoCentral("$root/receiver", key)
        try {
            val senderClient = sender.proteusClient()
            val receiverClient = receiver.proteusClient()
            val senderSession = CryptoSessionId(CryptoUserID("sender", "wire.test"), CryptoClientId("1"))
            val receiverSession = CryptoSessionId(CryptoUserID("receiver", "wire.test"), CryptoClientId("2"))
            val prekey = receiverClient.newPreKeysAuto(1).single()
            val first = senderClient.transaction("first") {
                it.encryptWithPreKey("first".encodeToByteArray(), prekey, receiverSession)
            }
            assertEquals("first", receiverClient.transaction("receiveFirst") {
                it.decryptMessage(senderSession, first) { text -> text.decodeToString() }
            })
            val fingerprint = receiverClient.transaction("fingerprint") { it.getLocalFingerprint() }

            // Cleanup must run even if the owner's job was cancelled before calling close.
            var closeFinished = false
            val close = async(start = CoroutineStart.UNDISPATCHED) {
                currentCoroutineContext().cancel()
                receiver.close()
                closeFinished = true
            }
            close.join()
            assertTrue(close.isCancelled)
            assertTrue(closeFinished)
            assertFails {
                receiverClient.transaction("closedClient") { it.getLocalFingerprint() }
            }
            receiver.close()
            receiver = coreCryptoCentral("$root/receiver", key)
            val reopened = receiver.proteusClient()
            assertEquals(fingerprint, reopened.transaction("fingerprintAfterReopen") { it.getLocalFingerprint() })
            val next = senderClient.transaction("next") {
                it.encrypt("next".encodeToByteArray(), receiverSession)
            }
            assertEquals("next", reopened.transaction("receiveNext") {
                it.decryptMessage(senderSession, next) { text -> text.decodeToString() }
            })
        } finally {
            receiver.close()
            sender.close()
            assertEquals(emptyList(), openStoreFiles(root))
            NSFileManager.defaultManager.removeItemAtPath(root, null)
        }
    }
}

private fun openStoreFiles(root: String): List<String> = memScoped {
    val path = allocArray<ByteVar>(PATH_MAX)
    (0 until getdtablesize()).mapNotNull { descriptor ->
        if (fcntl(descriptor, F_GETPATH, path) == 0) {
            path.toKString().takeIf { it.startsWith(root.replace("//", "/")) }
        } else null
    }
}
