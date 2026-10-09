/*
 * Wire
 * Copyright (C) 2026 Wire Swiss GmbH
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.wire.kalium.cryptography

import com.wire.kalium.util.AccountResourceAccess
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import platform.Foundation.NSFileManager
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSUUID
import kotlin.io.encoding.Base64
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class ReopenableCoreCryptoCentralTest {
    @Test
    fun failedOpenWithUncertainCleanupCannotBeRetriedOrHandedOff() = runTest {
        val access = AccountResourceAccess()
        var opens = 0
        val central = ReopenableCoreCryptoCentral(access) {
            opens++
            throw CoreCryptoOpenCleanupException(IllegalStateException("native cleanup failed"))
        }
        assertFailsWith<CoreCryptoOpenCleanupException> { central.proteusClient() }
        assertFailsWith<IllegalStateException> { central.proteusClient() }
        assertFailsWith<IllegalStateException> { access.pause { central.suspendNativeResources() } }
        assertEquals(1, opens)
    }

    @Test
    fun retainedProteusClientResumesAfterAnotherOwnerUpdatesTheStore() = runTest {
        val root = NSTemporaryDirectory() + "wire-retained-proteus-" + NSUUID().UUIDString
        val key = ByteArray(32) { 7 }
        val access = AccountResourceAccess()
        val central = ReopenableCoreCryptoCentral(access) { coreCryptoCentral(root, key) }
        try {
            val client = central.proteusClient()
            val first = client.newPreKeysAuto(2)
            val fingerprint = client.transaction("fingerprint") { it.getLocalFingerprint() }
            access.pause { central.suspendNativeResources() }
            val queued = async { client.newPreKeysAuto(2) }
            runCurrent()
            assertFalse(queued.isCompleted)
            val extension = coreCryptoCentral(root, key)
            val second = try {
                extension.proteusClient().newPreKeysAuto(2)
            } finally {
                extension.close()
            }
            access.resume {}
            val third = queued.await()
            assertEquals(6, (first + second + third).map { it.id }.toSet().size)
            assertEquals(fingerprint, client.transaction("same-identity") { it.getLocalFingerprint() })
        } finally {
            access.resume {}
            central.close()
            NSFileManager.defaultManager.removeItemAtPath(root, null)
        }
    }

    @Test
    fun retainedMlsClientObservesGroupsWrittenByAnotherOwner() = runTest {
        val root = NSTemporaryDirectory() + "wire-retained-mls-" + NSUUID().UUIDString
        val key = ByteArray(32) { 7 }
        val access = AccountResourceAccess()
        val central = ReopenableCoreCryptoCentral(access) { coreCryptoCentral(root, key) }
        val identity = CryptoQualifiedClientId("abc123", CryptoUserID("fcbe2b38-c66a-4fbe-9e54-6fab39c017a4", "wire.test"))
        val transport = object : MLSTransporter {
            override suspend fun sendCommitBundle(commitBundle: CommitBundle) = Unit
        }
        val observer = object : MLSEpochObserver {
            override suspend fun onEpochChange(groupId: MLSGroupId, epoch: ULong) = Unit
        }
        try {
            val client = central.mlsClient(identity, MLSCiphersuite.DEFAULT, transport, observer, backgroundScope)
            client.initializeBasicCredential()
            val originalKey = client.getPublicKey().first
            val group = Base64.encode("created-by-extension".encodeToByteArray())
            access.pause { central.suspendNativeResources() }
            val extension = coreCryptoCentral(root, key)
            try {
                extension.mlsClient(identity, MLSCiphersuite.DEFAULT, transport, observer, backgroundScope).transaction("new-group") {
                    it.createConversation(group, Base64.decode("GX9rI+FshTLGq8g4+s1ep4m+DHaykgM0A5v6iz02jWE="))
                }
            } finally {
                extension.close()
            }
            access.resume {}
            assertTrue(client.transaction("updated-groups") { it.conversationExists(group) })
            assertContentEquals(originalKey, client.getPublicKey().first)
        } finally {
            access.resume {}
            central.close()
            NSFileManager.defaultManager.removeItemAtPath(root, null)
        }
    }
}
