/* Wire, Copyright (C) 2026 Wire Swiss GmbH, SPDX-License-Identifier: GPL-3.0-or-later */
package com.wire.kalium.logic.sync.receiving

import com.wire.kalium.cryptography.CommitBundle
import com.wire.kalium.cryptography.GroupInfoBundle
import com.wire.kalium.cryptography.GroupInfoEncryptionType
import com.wire.kalium.cryptography.MLSGroupId
import com.wire.kalium.cryptography.RatchetTreeType
import com.wire.kalium.network.api.authenticated.message.SendMLSMessageResponse
import com.wire.kalium.network.api.authenticated.notification.EventContentDTO
import com.wire.kalium.network.api.base.authenticated.message.MLSMessageApi
import com.wire.kalium.network.tools.KtxSerializer
import com.wire.kalium.network.utils.NetworkResponse
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
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

    private class FakeNative(var onJoin: suspend (ByteArray) -> String) : UnexpectedMlsContext() {
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
