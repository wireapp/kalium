/* Wire, Copyright (C) 2026 Wire Swiss GmbH, SPDX-License-Identifier: GPL-3.0-or-later */
package com.wire.kalium.logic.sync.receiving

import com.wire.kalium.logic.data.event.Event
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.id.toApi
import com.wire.kalium.network.api.authenticated.conversation.AddConversationMembersRequest
import com.wire.kalium.network.api.authenticated.conversation.AddServiceRequest
import com.wire.kalium.network.api.authenticated.conversation.ConvProtocol
import com.wire.kalium.network.api.authenticated.conversation.ConversationMemberAddedResponse
import com.wire.kalium.network.api.authenticated.conversation.ConversationMemberDTO
import com.wire.kalium.network.api.authenticated.conversation.ConversationMemberRemovedResponse
import com.wire.kalium.network.api.authenticated.conversation.ConversationMembersResponse
import com.wire.kalium.network.api.authenticated.conversation.ConversationPagingResponse
import com.wire.kalium.network.api.authenticated.conversation.ConversationRenameResponse
import com.wire.kalium.network.api.authenticated.conversation.ConversationResponse
import com.wire.kalium.network.api.authenticated.conversation.ConversationResponseDTO
import com.wire.kalium.network.api.authenticated.conversation.CreateConversationRequest
import com.wire.kalium.network.api.authenticated.conversation.MemberUpdateDTO
import com.wire.kalium.network.api.authenticated.conversation.ReceiptMode
import com.wire.kalium.network.api.authenticated.conversation.SubconversationDeleteRequest
import com.wire.kalium.network.api.authenticated.conversation.SubconversationResponse
import com.wire.kalium.network.api.authenticated.conversation.TypingIndicatorStatusDTO
import com.wire.kalium.network.api.authenticated.conversation.UpdateChannelAddPermissionResponse
import com.wire.kalium.network.api.authenticated.conversation.UpdateConversationAccessRequest
import com.wire.kalium.network.api.authenticated.conversation.UpdateConversationAccessResponse
import com.wire.kalium.network.api.authenticated.conversation.UpdateConversationProtocolResponse
import com.wire.kalium.network.api.authenticated.conversation.UpdateConversationReceiptModeResponse
import com.wire.kalium.network.api.authenticated.conversation.channel.ChannelAddPermissionDTO
import com.wire.kalium.network.api.authenticated.conversation.guestroomlink.ConversationInviteLinkResponse
import com.wire.kalium.network.api.authenticated.conversation.model.ConversationCodeInfo
import com.wire.kalium.network.api.authenticated.conversation.model.ConversationMemberRoleDTO
import com.wire.kalium.network.api.authenticated.conversation.model.ConversationReceiptModeDTO
import com.wire.kalium.network.api.authenticated.notification.EventContentDTO
import com.wire.kalium.network.api.base.authenticated.conversation.ConversationApi
import com.wire.kalium.network.api.model.ConversationId as ApiConversationId
import com.wire.kalium.network.api.model.QualifiedID
import com.wire.kalium.network.api.model.ServiceAddedResponse
import com.wire.kalium.network.api.model.SubconversationId
import com.wire.kalium.network.api.model.UserId
import com.wire.kalium.network.utils.NetworkResponse
import com.wire.kalium.persistence.TestUserDatabase
import com.wire.kalium.persistence.dao.UserIDEntity
import com.wire.kalium.persistence.withTestUserDatabase
import kotlin.io.encoding.Base64
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest

class MLSReceiveRecoveryTest {
    @Test
    fun healthyResumeDoesNotDecodeCiphertextOrReadNativeOrNetworkAndCachesTheIndex() = runTest {
        withFixture { f ->
            repeat(2) { assertNull(f.recovery.resume(f.native, EVENT.copy(content = "not base64!"))) }
            assertEquals(1, f.metadata.rangeReads)
            assertEquals(0, f.native.reads)
            assertEquals(0, f.api.detailReads)
            assertEquals(0, f.api.groupInfoReads)
            assertEquals(0, f.joins)
        }
    }

    @Test
    fun pendingIntentForcesExplicitJoinEvenWhenNativeEpochIsAlreadyCurrent() = runTest {
        withFixture { f ->
            val operation = f.store.begin(EVENT.id, GROUP_ID, CIPHERTEXT)
            f.native.epoch = 12uL
            val result = assertNotNull(f.recovery.resume(f.native, EVENT))
            assertEquals(operation, result.operation)
            assertEquals(13uL, result.epoch)
            assertEquals("mls-recovery:${operation.id}", result.warningId)
            assertEquals(1, f.joins)
            assertEquals(1, f.native.checkpointWrites)
        }
    }

    @Test
    fun committedCompletionResumesEffectsWithoutAnotherExternalJoin() = runTest {
        withFixture { f ->
            val first = assertNotNull(f.recovery.recover(f.native, EVENT, GROUP_ID))
            val replay = assertNotNull(f.recovery.resume(f.native, EVENT))
            assertEquals(first, replay)
            assertEquals(1, f.joins)
            assertEquals(1, f.api.groupInfoReads)
            assertEquals(1, f.native.checkpointWrites)
        }
    }

    @Test
    fun invalidCanonicalMetadataCannotCreateIntentOrJoin() = runTest {
        val invalid = listOf(
            RESPONSE.copy(id = QualifiedID("other", "example.com")),
            RESPONSE.copy(id = QualifiedID("conversation", "other.example")),
            RESPONSE.copy(groupId = "other-group"),
            RESPONSE.copy(groupId = " "),
            RESPONSE.copy(protocol = ConvProtocol.PROTEUS),
            RESPONSE.copy(members = RESPONSE.members.copy(self = null)),
            RESPONSE.copy(members = RESPONSE.members.copy(self = ConversationMemberDTO.Self(UserId("other", "example.com"), "wire_member"))),
            RESPONSE.copy(epoch = null),
        )
        for (response in invalid) withFixture { f ->
            f.api.response = response
            assertFailsWith<IllegalStateException> { f.recovery.recover(f.native, EVENT, GROUP_ID) }
            assertTrue(f.metadata.values.isEmpty())
            assertEquals(0, f.joins)
            assertEquals(0, f.api.groupInfoReads)
        }
    }

    @Test
    fun pendingRecoveryRejectsChangedGroupWithoutJoinOrRetirement() = runTest {
        withFixture { f ->
            val operation = f.store.begin(EVENT.id, GROUP_ID, CIPHERTEXT)
            f.api.response = RESPONSE.copy(groupId = "replacement-group")
            assertFailsWith<IllegalStateException> { f.recovery.resume(f.native, EVENT) }
            assertEquals(operation, f.store.find(EVENT.id) { CIPHERTEXT })
            assertEquals(0, f.joins)
        }
    }

    @Test
    fun completedRecoveryStillRequiresCanonicalSelfMembership() = runTest {
        withFixture { f ->
            val first = assertNotNull(f.recovery.recover(f.native, EVENT, GROUP_ID))
            f.api.response = RESPONSE.copy(members = RESPONSE.members.copy(self = null))
            assertFailsWith<IllegalStateException> { f.recovery.resume(f.native, EVENT) }
            assertEquals(first.operation, f.store.find(EVENT.id) { CIPHERTEXT })
            assertEquals(1, f.joins)
        }
    }

    @Test
    fun pendingRecoveryCannotJoinAGroupDifferentFromTheCallersExpectedGroup() = runTest {
        withFixture { f ->
            f.store.begin(EVENT.id, GROUP_ID, CIPHERTEXT)
            assertFailsWith<IllegalStateException> { f.recovery.recover(f.native, EVENT, "different-group") }
            assertEquals(0, f.api.detailReads)
            assertEquals(0, f.joins)
        }
    }

    @Test
    fun nativeEpochControlsNewRecoveryAndMissingNativeGroupCanRecover() = runTest {
        withFixture { f ->
            f.native.epoch = 12uL
            assertNull(f.recovery.recover(f.native, EVENT, GROUP_ID))
            assertTrue(f.metadata.values.isEmpty())
            f.native.exists = false
            assertNotNull(f.recovery.recover(f.native, EVENT, GROUP_ID))
            assertEquals(1, f.joins)
        }
    }

    @Test
    fun unsupportedCheckpointFailsBeforeJoinButRetainsIntent() = runTest {
        withFixture { f ->
            f.native.checkpoint = "unsupported-format".encodeToByteArray()
            assertFailsWith<Exception> { f.recovery.recover(f.native, EVENT, GROUP_ID) }
            assertNotNull(f.store.find(EVENT.id) { CIPHERTEXT })
            assertEquals(0, f.api.groupInfoReads)
            assertEquals(0, f.joins)
        }
    }

    @Test
    fun failedJoinRetainsIntentAndCancellationPropagates() = runTest {
        withFixture { f ->
            f.failure = CancellationException("cancelled")
            assertFailsWith<CancellationException> { f.recovery.recover(f.native, EVENT, GROUP_ID) }
            assertNotNull(f.store.find(EVENT.id) { CIPHERTEXT })
            assertEquals(0, f.native.checkpointWrites)
            f.failure = null
            assertNotNull(f.recovery.resume(f.native, EVENT))
            assertEquals(2, f.joins)
        }
    }

    @Test
    fun emptyGroupInfoCannotBecomeCompleted() = runTest {
        withFixture { f ->
            f.api.groupInfo = byteArrayOf()
            assertFailsWith<IllegalStateException> { f.recovery.recover(f.native, EVENT, GROUP_ID) }
            assertNotNull(f.store.find(EVENT.id) { CIPHERTEXT })
            assertEquals(0, f.joins)
            assertEquals(0, f.native.checkpointWrites)
        }
    }

    @Test
    fun incorrectJoinEpochCannotWriteCompletion() = runTest {
        withFixture { f ->
            val recovery = MLSReceiveRecovery(f.store, f.api, SELF) { _, _, _ -> 999uL }
            assertFailsWith<IllegalStateException> { recovery.recover(f.native, EVENT, GROUP_ID) }
            assertNotNull(f.store.find(EVENT.id) { CIPHERTEXT })
            assertEquals(0, f.native.checkpointWrites)
        }
    }

    private suspend fun TestScope.withFixture(block: suspend (Fixture) -> Unit) {
        withTestUserDatabase(UserIDEntity("recovery-test", "example.com")) { block(Fixture(it)) }
    }

    private class Fixture(database: TestUserDatabase) {
        val metadata = FakeMetadata()
        val store = MLSReceiveRecoveryStore(metadata, database.builder.notificationEventStore, "client")
        val native = FakeMlsContext()
        val api = FakeApi()
        var joins = 0
        var failure: Exception? = null
        val recovery = MLSReceiveRecovery(store, api, SELF) { context, groupId, groupInfo ->
            assertEquals(native, context)
            assertEquals(GROUP_ID, groupId)
            assertContentEquals(byteArrayOf(1, 2), groupInfo)
            assertNotNull(store.find(EVENT.id) { CIPHERTEXT })
            joins++
            failure?.let { throw it }
            native.exists = true
            native.epoch++
            native.epoch
        }
    }

    private class FakeApi : ConversationApi {
        var response = RESPONSE
        var detailReads = 0
        var groupInfoReads = 0
        var groupInfo = byteArrayOf(1, 2)
        override suspend fun fetchConversationsIds(
            pagingState: String?
        ): NetworkResponse<ConversationPagingResponse> = error("Unexpected API call")
        override suspend fun fetchConversationsListDetails(
            conversationsIds: List<ApiConversationId>
        ): NetworkResponse<ConversationResponseDTO> = error("Unexpected API call")
        override suspend fun fetchConversationDetails(
            conversationId: ApiConversationId
        ): NetworkResponse<ConversationResponse> { detailReads++; return NetworkResponse.Success(response, emptyMap(), 200) }
        override suspend fun createNewConversation(
            createConversationRequest: CreateConversationRequest
        ): NetworkResponse<ConversationResponse> = error("Unexpected API call")
        override suspend fun addMember(
            addParticipantRequest: AddConversationMembersRequest,
            conversationId: ApiConversationId
        ): NetworkResponse<ConversationMemberAddedResponse> = error("Unexpected API call")
        override suspend fun addService(
            addServiceRequest: AddServiceRequest,
            conversationId: ApiConversationId
        ): NetworkResponse<ServiceAddedResponse> = error("Unexpected API call")
        override suspend fun removeMember(
            userId: UserId,
            conversationId: ApiConversationId
        ): NetworkResponse<ConversationMemberRemovedResponse> = error("Unexpected API call")
        override suspend fun updateConversationMemberState(
            memberUpdateRequest: MemberUpdateDTO,
            conversationId: ApiConversationId
        ): NetworkResponse<Unit> = error("Unexpected API call")
        override suspend fun updateAccess(
            conversationId: ApiConversationId,
            updateConversationAccessRequest: UpdateConversationAccessRequest
        ): NetworkResponse<UpdateConversationAccessResponse> = error("Unexpected API call")
        override suspend fun updateConversationMemberRole(
            conversationId: ApiConversationId,
            userId: UserId,
            conversationMemberRoleDTO: ConversationMemberRoleDTO
        ): NetworkResponse<Unit> = error("Unexpected API call")
        override suspend fun updateConversationName(
            conversationId: QualifiedID,
            conversationName: String
        ): NetworkResponse<ConversationRenameResponse> = error("Unexpected API call")
        override suspend fun fetchGroupInfo(
            conversationId: QualifiedID
        ): NetworkResponse<ByteArray> { groupInfoReads++; return NetworkResponse.Success(groupInfo, emptyMap(), 200) }
        override suspend fun joinConversation(
            code: String,
            key: String,
            uri: String?,
            password: String?
        ): NetworkResponse<ConversationMemberAddedResponse> = error("Unexpected API call")
        override suspend fun fetchLimitedInformationViaCode(
            code: String,
            key: String
        ): NetworkResponse<ConversationCodeInfo> = error("Unexpected API call")
        override suspend fun fetchMlsOneToOneConversation(
            userId: UserId
        ): NetworkResponse<ConversationResponse> = error("Unexpected API call")
        override suspend fun fetchSubconversationDetails(
            conversationId: ApiConversationId,
            subconversationId: SubconversationId
        ): NetworkResponse<SubconversationResponse> = error("Unexpected API call")
        override suspend fun fetchSubconversationGroupInfo(
            conversationId: ApiConversationId,
            subconversationId: SubconversationId
        ): NetworkResponse<ByteArray> = error("Unexpected API call")
        override suspend fun deleteSubconversation(
            conversationId: ApiConversationId,
            subconversationId: SubconversationId,
            deleteRequest: SubconversationDeleteRequest
        ): NetworkResponse<Unit> = error("Unexpected API call")
        override suspend fun leaveSubconversation(
            conversationId: ApiConversationId,
            subconversationId: SubconversationId
        ): NetworkResponse<Unit> = error("Unexpected API call")
        override suspend fun updateReceiptMode(
            conversationId: ApiConversationId,
            receiptMode: ConversationReceiptModeDTO
        ): NetworkResponse<UpdateConversationReceiptModeResponse> = error("Unexpected API call")
        override suspend fun generateGuestRoomLink(
            conversationId: ApiConversationId,
            password: String?
        ): NetworkResponse<EventContentDTO.Conversation.CodeUpdated> = error("Unexpected API call")
        override suspend fun revokeGuestRoomLink(conversationId: ApiConversationId): NetworkResponse<Unit> = error("Unexpected API call")
        override suspend fun updateMessageTimer(
            conversationId: ApiConversationId,
            messageTimer: Long?
        ): NetworkResponse<EventContentDTO.Conversation.MessageTimerUpdate> = error("Unexpected API call")
        override suspend fun sendTypingIndicatorNotification(
            conversationId: ApiConversationId,
            typingIndicatorMode: TypingIndicatorStatusDTO
        ): NetworkResponse<Unit> = error("Unexpected API call")
        override suspend fun updateProtocol(
            conversationId: ApiConversationId,
            protocol: ConvProtocol
        ): NetworkResponse<UpdateConversationProtocolResponse> = error("Unexpected API call")
        override suspend fun updateChannelAddPermission(
            conversationId: ApiConversationId,
            channelAddPermission: ChannelAddPermissionDTO
        ): NetworkResponse<UpdateChannelAddPermissionResponse> = error("Unexpected API call")
        override suspend fun guestLinkInfo(
            conversationId: ApiConversationId
        ): NetworkResponse<ConversationInviteLinkResponse> = error("Unexpected API call")
        override suspend fun resetMlsConversation(
            groupId: String,
            epoch: ULong,
        ): NetworkResponse<Unit> = error("Unexpected API call")
    }

    private companion object {
        const val GROUP_ID = "group"
        val SELF = com.wire.kalium.logic.data.user.UserId("self", "example.com")
        val CIPHERTEXT = byteArrayOf(4, 5, 6)
        val EVENT = Event.Conversation.NewMLSMessage(
            "event", ConversationId("conversation", "example.com"), null, SELF,
            Instant.parse("2026-09-01T00:00:00Z"), Base64.encode(CIPHERTEXT),
        )
        val RESPONSE = ConversationResponse(
            creator = null,
            members = ConversationMembersResponse(ConversationMemberDTO.Self(SELF.toApi(), "wire_member"), emptyList()),
            name = "Conversation", id = EVENT.conversationId.toApi(), groupId = GROUP_ID, epoch = 10uL,
            type = ConversationResponse.Type.GROUP, messageTimer = null, teamId = null,
            protocol = ConvProtocol.MLS, lastEventTime = "2026-09-01T00:00:00Z", mlsCipherSuiteTag = 1,
            access = emptySet(), accessRole = emptySet(), receiptMode = ReceiptMode.DISABLED,
        )
    }
}
