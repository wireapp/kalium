/* Wire, Copyright (C) 2026 Wire Swiss GmbH, SPDX-License-Identifier: GPL-3.0-or-later */
package com.wire.kalium.logic.sync.receiving

import com.wire.kalium.cryptography.ApplicationMessage
import com.wire.kalium.cryptography.CommitBundle
import com.wire.kalium.cryptography.CryptoCredentialRef
import com.wire.kalium.cryptography.CryptoQualifiedClientId
import com.wire.kalium.cryptography.CryptoQualifiedID
import com.wire.kalium.cryptography.E2EIConversationState
import com.wire.kalium.cryptography.ExternalSenderKey
import com.wire.kalium.cryptography.GroupInfoBundle
import com.wire.kalium.cryptography.GroupInfoEncryptionType
import com.wire.kalium.cryptography.MLSCiphersuite
import com.wire.kalium.cryptography.MLSDecryptResult
import com.wire.kalium.cryptography.MLSGroupId
import com.wire.kalium.cryptography.MLSKeyPackage
import com.wire.kalium.cryptography.MlsCoreCryptoContext
import com.wire.kalium.cryptography.PlainMessage
import com.wire.kalium.cryptography.RatchetTreeType
import com.wire.kalium.cryptography.WelcomeMessage
import com.wire.kalium.cryptography.WireIdentity
import com.wire.kalium.network.api.authenticated.message.SendMLSMessageResponse
import com.wire.kalium.network.api.authenticated.notification.EventContentDTO
import com.wire.kalium.network.api.base.authenticated.message.MLSMessageApi
import com.wire.kalium.network.tools.KtxSerializer
import com.wire.kalium.network.utils.NetworkResponse
import com.wire.kalium.persistence.dao.MetadataDAO
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okio.ByteString.Companion.toByteString
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Instant

class ReceivedMLSRejoinTest {
    @Test
    fun onlyExplicitJoinCanSendAndUsesCommitBundleSerialization() = runBlocking {
        val api = FakeApi()
        val metadata = FakeMetadata()
        val rejoin = ReceivedMLSRejoin(api, metadata)
        assertFailsWith<IllegalStateException> { rejoin.sendCommitBundle(BUNDLE) }
        assertEquals(0, api.attempts)

        val native = FakeNative { groupInfo ->
            assertContentEquals(GROUP_INFO, groupInfo)
            rejoin.sendCommitBundle(BUNDLE)
            GROUP_ID
        }
        assertEquals(7uL, rejoin.join(native, GROUP_ID, GROUP_INFO))
        assertEquals(1, native.joins)
        assertEquals(1, api.attempts)
        assertContentEquals(byteArrayOf(1, 2, 3), api.lastBundle?.value)
        assertFailsWith<IllegalStateException> { rejoin.sendCommitBundle(BUNDLE) }
        assertEquals(1, api.attempts)
    }

    @Test
    fun mismatchedNativeGroupIsRejectedAndTransportCloses() = runBlocking {
        val api = FakeApi()
        val rejoin = ReceivedMLSRejoin(api, FakeMetadata())
        val native = FakeNative { "other-group" }

        assertFailsWith<Exception> { rejoin.join(native, GROUP_ID, GROUP_INFO) }
        assertEquals(0, native.epochReads)
        assertFailsWith<IllegalStateException> { rejoin.sendCommitBundle(BUNDLE) }
        assertEquals(0, api.attempts)
    }

    @Test
    fun unexpectedEventsAreRetainedAndBlockNextNativeJoin() = runBlocking {
        val api = FakeApi(events = listOf(
            EventContentDTO.Unknown("unexpected-server-event"),
            EventContentDTO.Federation.FederationDeleteDTO("retained.example"),
        ))
        val metadata = FakeMetadata()
        val diagnostics = ReceivingDiagnostics()
        val rejoin = ReceivedMLSRejoin(api, metadata, diagnostics)
        val native = FakeNative { rejoin.sendCommitBundle(BUNDLE); GROUP_ID }

        assertFailsWith<Exception> { rejoin.join(native, GROUP_ID, GROUP_INFO) }
        assertEquals("UNSUPPORTED_EVENT", diagnostics.errorCode)
        val stored = KtxSerializer.json.parseToJsonElement(assertNotNull(metadata.values[RESPONSE_KEY])).jsonObject
        assertEquals(byteArrayOf(1, 2, 3).toByteString().sha256().hex(), stored.getValue("bundleHash").jsonPrimitive.content)
        assertEquals(2, stored.getValue("events").jsonArray.size)
        assertTrue(stored.toString().contains("unexpected-server-event"))
        assertEquals("retained.example", stored.getValue("events").jsonArray[1].jsonObject.getValue("domain").jsonPrimitive.content)

        val nextNative = FakeNative { error("Native join must not run") }
        assertFailsWith<Exception> { ReceivedMLSRejoin(api, metadata).join(nextNative, GROUP_ID, GROUP_INFO) }
        assertEquals(0, nextNative.joins)
        assertEquals(1, api.attempts)
    }

    @Test
    fun cancellationReleasesJoinGuard() = runBlocking {
        val api = FakeApi()
        val rejoin = ReceivedMLSRejoin(api, FakeMetadata())
        val native = FakeNative { throw CancellationException("cancelled") }
        assertFailsWith<CancellationException> { rejoin.join(native, GROUP_ID, GROUP_INFO) }
        native.onJoin = { GROUP_ID }
        assertEquals(7uL, rejoin.join(native, GROUP_ID, GROUP_INFO))
        assertEquals(2, native.joins)
    }

    @Test
    fun failedRetentionStillBlocksAnotherJoinInThisTransportInstance() = runBlocking {
        val metadata = FakeMetadata(failInsert = true)
        val api = FakeApi(events = listOf(EventContentDTO.Unknown("unexpected")))
        val rejoin = ReceivedMLSRejoin(api, metadata)
        val native = FakeNative { rejoin.sendCommitBundle(BUNDLE); GROUP_ID }
        assertFailsWith<Exception> { rejoin.join(native, GROUP_ID, GROUP_INFO) }
        assertEquals(1, api.attempts)
        assertFailsWith<Exception> { rejoin.join(native, GROUP_ID, GROUP_INFO) }
        assertEquals(1, native.joins)
    }

    private class FakeApi(var events: List<EventContentDTO> = emptyList()) : MLSMessageApi {
        var attempts = 0
        var lastBundle: MLSMessageApi.CommitBundle? = null

        override suspend fun sendMessage(message: ByteArray): NetworkResponse<SendMLSMessageResponse> = error("Unexpected message send")

        override suspend fun sendCommitBundle(bundle: MLSMessageApi.CommitBundle): NetworkResponse<SendMLSMessageResponse> {
            attempts++
            lastBundle = bundle
            return NetworkResponse.Success(SendMLSMessageResponse(Instant.parse("2026-09-01T00:00:00Z"), events), emptyMap(), 201)
        }
    }

    private class FakeMetadata(private val failInsert: Boolean = false) : MetadataDAO {
        val values = mutableMapOf<String, String>()
        override suspend fun insertValue(value: String, key: String) {
            if (failInsert) error("Storage failed")
            values[key] = value
        }
        override suspend fun valueByKey(key: String): String? = values[key]
        override suspend fun valuesByKeyRange(fromInclusive: String, untilExclusive: String): Map<String, String> =
            values.filterKeys { it >= fromInclusive && it < untilExclusive }
        override suspend fun deleteValue(key: String) = error("Unexpected delete")
        override fun valueByKeyFlow(key: String): Flow<String?> = error("Unexpected observe")
        override suspend fun clear(keysToKeep: List<String>?) = error("Unexpected clear")
        override suspend fun <T> putSerializable(key: String, value: T, kSerializer: KSerializer<T>) = error("Unexpected put")
        override suspend fun <T> getSerializable(key: String, kSerializer: KSerializer<T>): T? = error("Unexpected get")
        override fun <T> observeSerializable(key: String, kSerializer: KSerializer<T>): Flow<T?> = error("Unexpected observe")
    }

    private class FakeNative(var onJoin: suspend (ByteArray) -> String) : MlsCoreCryptoContext {
        var joins = 0
        var epochReads = 0
        override suspend fun joinByExternalCommit(publicGroupState: ByteArray): MLSGroupId {
            joins++
            return onJoin(publicGroupState)
        }
        override suspend fun conversationEpoch(groupId: MLSGroupId): ULong {
            epochReads++
            assertEquals(GROUP_ID, groupId)
            return 7uL
        }
        override suspend fun readTransactionCheckpoint(): ByteArray? = error("Unexpected native call")
        override suspend fun writeTransactionCheckpoint(checkpoint: ByteArray) = error("Unexpected native call")
        override fun getDefaultCipherSuite(): MLSCiphersuite = error("Unexpected native call")
        override suspend fun generateKeyPackages(amount: Int, credentialRef: CryptoCredentialRef?): List<ByteArray> = error("Unexpected native call")
        override suspend fun validKeyPackageCount(): ULong = error("Unexpected native call")
        override suspend fun updateKeyingMaterial(groupId: MLSGroupId) = error("Unexpected native call")
        override suspend fun conversationExists(groupId: MLSGroupId): Boolean = error("Unexpected native call")
        override suspend fun createConversation(groupId: MLSGroupId, externalSenders: ByteArray) = error("Unexpected native call")
        override suspend fun getExternalSenders(groupId: MLSGroupId): ExternalSenderKey = error("Unexpected native call")
        override suspend fun wipeConversation(groupId: MLSGroupId) = error("Unexpected native call")
        override suspend fun processWelcomeMessage(message: WelcomeMessage): MLSGroupId = error("Unexpected native call")
        override suspend fun commitPendingProposals(groupId: MLSGroupId) = error("Unexpected native call")
        override suspend fun encryptMessage(groupId: MLSGroupId, message: PlainMessage): ApplicationMessage = error("Unexpected native call")
        override suspend fun decryptMessage(groupId: String, message: ByteArray): MLSDecryptResult = error("Unexpected native call")
        override suspend fun members(groupId: MLSGroupId): List<CryptoQualifiedClientId> = error("Unexpected native call")
        override suspend fun addMember(groupId: MLSGroupId, membersKeyPackages: List<MLSKeyPackage>) = error("Unexpected native call")
        override suspend fun removeMember(groupId: MLSGroupId, members: List<CryptoQualifiedClientId>) = error("Unexpected native call")
        override suspend fun deriveSecret(groupId: MLSGroupId, keyLength: UInt): ByteArray = error("Unexpected native call")
        override suspend fun setConversationCredential(groupId: MLSGroupId, credentialRef: CryptoCredentialRef) = error("Unexpected native call")
        override suspend fun getConversationCredentialRef(groupId: MLSGroupId): CryptoCredentialRef = error("Unexpected native call")
        override suspend fun removeKeyPackages(credentialRef: CryptoCredentialRef) = error("Unexpected native call")
        override suspend fun removeCredential(credentialRef: CryptoCredentialRef) = error("Unexpected native call")
        override suspend fun checkCredentials() = error("Unexpected native call")
        override suspend fun isE2EIEnabled(): Boolean = error("Unexpected native call")
        override suspend fun getGroupState(groupId: MLSGroupId): E2EIConversationState = error("Unexpected native call")
        override suspend fun getDeviceIdentities(groupId: MLSGroupId, clients: List<CryptoQualifiedClientId>): List<WireIdentity> = error("Unexpected native call")
        override suspend fun getUserIdentities(groupId: MLSGroupId, users: List<CryptoQualifiedID>): Map<String, List<WireIdentity>> = error("Unexpected native call")
        override suspend fun removeStaleKeyPackages() = error("Unexpected native call")
    }

    private companion object {
        const val GROUP_ID = "group"
        const val RESPONSE_KEY = "nse_mls_rejoin_unexpected_response_v1"
        val GROUP_INFO = byteArrayOf(4)
        val BUNDLE = CommitBundle(
            byteArrayOf(1), byteArrayOf(3),
            GroupInfoBundle(GroupInfoEncryptionType.PLAINTEXT, RatchetTreeType.FULL, byteArrayOf(2)),
        )
    }
}
