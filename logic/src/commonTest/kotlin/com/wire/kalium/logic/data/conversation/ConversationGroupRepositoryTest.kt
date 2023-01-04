package com.wire.kalium.logic.data.conversation

import com.wire.kalium.logic.StorageFailure
import com.wire.kalium.logic.data.id.GroupID
import com.wire.kalium.logic.data.id.PersistenceQualifiedId
import com.wire.kalium.logic.data.id.TeamId
import com.wire.kalium.logic.data.id.toApi
import com.wire.kalium.logic.data.user.UserRepository
import com.wire.kalium.logic.feature.SelfTeamIdProvider
import com.wire.kalium.logic.framework.TestConversation
import com.wire.kalium.logic.framework.TestConversation.ADD_MEMBER_TO_CONVERSATION_SUCCESSFUL_RESPONSE
import com.wire.kalium.logic.framework.TestUser
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.sync.receiver.conversation.MemberJoinEventHandler
import com.wire.kalium.logic.sync.receiver.conversation.MemberLeaveEventHandler
import com.wire.kalium.logic.util.shouldFail
import com.wire.kalium.logic.util.shouldSucceed
import com.wire.kalium.network.api.base.authenticated.conversation.ConvProtocol
import com.wire.kalium.network.api.base.authenticated.conversation.ConvProtocol.MLS
import com.wire.kalium.network.api.base.authenticated.conversation.ConversationApi
import com.wire.kalium.network.api.base.authenticated.conversation.ConversationMemberAddedResponse
import com.wire.kalium.network.api.base.authenticated.conversation.ConversationMemberDTO
import com.wire.kalium.network.api.base.authenticated.conversation.ConversationMemberRemovedResponse
import com.wire.kalium.network.api.base.authenticated.conversation.ConversationMembersResponse
import com.wire.kalium.network.api.base.authenticated.conversation.ConversationResponse
import com.wire.kalium.network.api.base.authenticated.conversation.ReceiptMode
import com.wire.kalium.network.api.base.model.ConversationAccessDTO
import com.wire.kalium.network.api.base.model.ConversationAccessRoleDTO
import com.wire.kalium.network.api.base.model.ErrorResponse
import com.wire.kalium.network.exceptions.KaliumException
import com.wire.kalium.network.utils.NetworkResponse
import com.wire.kalium.persistence.dao.ConversationDAO
import com.wire.kalium.persistence.dao.ConversationEntity
import com.wire.kalium.persistence.dao.ConversationViewEntity
import com.wire.kalium.persistence.dao.QualifiedIDEntity
import io.ktor.http.HttpStatusCode
import io.mockative.Mock
import io.mockative.any
import io.mockative.anything
import io.mockative.eq
import io.mockative.fun2
import io.mockative.given
import io.mockative.matching
import io.mockative.mock
import io.mockative.once
import io.mockative.thenDoNothing
import io.mockative.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import kotlin.test.Test
import com.wire.kalium.persistence.dao.Member as MemberEntity

@Suppress("LargeClass")
@OptIn(ExperimentalCoroutinesApi::class)
class ConversationGroupRepositoryTest {

    @Test
    fun givenSelfUserBelongsToATeam_whenCallingCreateGroupConversation_thenConversationIsCreatedAtBackendAndPersisted() = runTest {

        val (arrangement, conversationGroupRepository) = Arrangement()
            .withCreateNewConversationAPI(NetworkResponse.Success(CONVERSATION_RESPONSE, emptyMap(), 201))
            .withSelfTeamId(Either.Right(TestUser.SELF.teamId))
            .withInsertConversationSuccess()
            .withConversationDetailsById(TestConversation.GROUP_VIEW_ENTITY(PROTEUS_PROTOCOL_INFO))
            .withInsertMembersWithQualifiedIdSucceeds()
            .arrange()

        val result = conversationGroupRepository.createGroupConversation(
            GROUP_NAME,
            listOf(TestUser.USER_ID),
            ConversationOptions(protocol = ConversationOptions.Protocol.PROTEUS)
        )

        result.shouldSucceed()

        with(arrangement) {
            verify(conversationDAO)
                .suspendFunction(conversationDAO::insertConversation)
                .with(anything())
                .wasInvoked(once)

            verify(conversationDAO)
                .suspendFunction(conversationDAO::insertMembersWithQualifiedId, fun2<List<MemberEntity>, QualifiedIDEntity>())
                .with(anything(), anything())
                .wasInvoked(once)
        }
    }

    @Test
    fun givenSelfUserDoesNotBelongToATeam_whenCallingCreateGroupConversation_thenConversationIsCreatedAtBackendAndPersisted() = runTest {

        val (arrangement, conversationGroupRepository) = Arrangement()
            .withCreateNewConversationAPI(NetworkResponse.Success(CONVERSATION_RESPONSE, emptyMap(), 201))
            .withSelfTeamId(Either.Right(null))
            .withInsertConversationSuccess()
            .withConversationDetailsById(TestConversation.GROUP_VIEW_ENTITY(PROTEUS_PROTOCOL_INFO))
            .withInsertMembersWithQualifiedIdSucceeds()
            .arrange()

        val result = conversationGroupRepository.createGroupConversation(
            GROUP_NAME,
            listOf(TestUser.USER_ID),
            ConversationOptions(protocol = ConversationOptions.Protocol.PROTEUS)
        )

        result.shouldSucceed()

        with(arrangement) {
            verify(conversationDAO)
                .suspendFunction(conversationDAO::insertConversation)
                .with(anything())
                .wasInvoked(once)

            verify(conversationDAO)
                .suspendFunction(conversationDAO::insertMembersWithQualifiedId, fun2<List<MemberEntity>, QualifiedIDEntity>())
                .with(anything(), anything())
                .wasInvoked(once)
        }
    }

    @Test
    fun givenMLSProtocolIsUsed_whenCallingCreateGroupConversation_thenMLSGroupIsEstablished() = runTest {
        val conversationResponse = CONVERSATION_RESPONSE.copy(protocol = MLS)
        val (arrangement, conversationGroupRepository) = Arrangement()
            .withCreateNewConversationAPI(NetworkResponse.Success(conversationResponse, emptyMap(), 201))
            .withSelfTeamId(Either.Right(TestUser.SELF.teamId))
            .withInsertConversationSuccess()
            .withMlsConversationEstablished()
            .withConversationDetailsById(TestConversation.GROUP_VIEW_ENTITY(PROTEUS_PROTOCOL_INFO))
            .arrange()

        val result = conversationGroupRepository.createGroupConversation(
            GROUP_NAME,
            listOf(TestUser.USER_ID),
            ConversationOptions(protocol = ConversationOptions.Protocol.MLS)
        )

        result.shouldSucceed()

        with(arrangement) {
            verify(conversationDAO)
                .suspendFunction(conversationDAO::insertConversation)
                .with(anything())
                .wasInvoked(once)

            verify(conversationDAO)
                .suspendFunction(conversationDAO::insertMembersWithQualifiedId, fun2<List<MemberEntity>, QualifiedIDEntity>())
                .with(matching { it.isNotEmpty() }, anything())
                .wasInvoked(once)

            verify(mlsConversationRepository)
                .suspendFunction(mlsConversationRepository::establishMLSGroup)
                .with(anything(), anything())
                .wasInvoked(once)
        }
    }

    @Test
    fun givenAConversationAndAPISucceedsWithChange_whenAddingMembersToConversation_thenShouldSucceed() = runTest {
        val (arrangement, conversationGroupRepository) = Arrangement()
            .withConversationDetailsById(TestConversation.CONVERSATION)
            .withConversationDetailsById(TestConversation.GROUP_VIEW_ENTITY(PROTEUS_PROTOCOL_INFO))
            .withFetchUsersIfUnknownByIdsSuccessful()
            .withAddMemberAPISucceedChanged()
            .withSuccessfulHandleMemberJoinEvent()
            .arrange()

        conversationGroupRepository.addMembers(listOf(TestConversation.USER_1), TestConversation.ID)
            .shouldSucceed()

        verify(arrangement.memberJoinEventHandler)
            .suspendFunction(arrangement.memberJoinEventHandler::handle)
            .with(anything())
            .wasInvoked(exactly = once)
    }

    @Test
    fun givenAConversationAndAPISucceedsWithoutChange_whenAddingMembersToConversation_thenShouldSucceed() = runTest {
        val (arrangement, conversationGroupRepository) = Arrangement()
            .withConversationDetailsById(TestConversation.CONVERSATION)
            .withConversationDetailsById(TestConversation.GROUP_VIEW_ENTITY(PROTEUS_PROTOCOL_INFO))
            .withFetchUsersIfUnknownByIdsSuccessful()
            .withAddMemberAPISucceedUnchanged()
            .arrange()

        conversationGroupRepository.addMembers(listOf(TestConversation.USER_1), TestConversation.ID)
            .shouldSucceed()

        verify(arrangement.memberJoinEventHandler)
            .suspendFunction(arrangement.memberJoinEventHandler::handle)
            .with(anything())
            .wasNotInvoked()
    }

    @Test
    fun givenAConversationAndAPIFailed_whenAddingMembersToConversation_thenShouldNotSucceed() = runTest {
        val (arrangement, conversationGroupRepository) = Arrangement()
            .withConversationDetailsById(TestConversation.CONVERSATION)
            .withConversationDetailsById(TestConversation.GROUP_VIEW_ENTITY(PROTEUS_PROTOCOL_INFO))
            .withAddMemberAPIFailed()
            .arrange()

        conversationGroupRepository.addMembers(listOf(TestConversation.USER_1), TestConversation.ID)
            .shouldFail()

        verify(arrangement.memberJoinEventHandler)
            .suspendFunction(arrangement.memberJoinEventHandler::handle)
            .with(anything())
            .wasNotInvoked()
    }

    @Test
    fun givenAnMLSConversationAndAPISucceeds_whenAddMemberFromConversation_thenShouldSucceed() = runTest {
        val (arrangement, conversationGroupRepository) = Arrangement()
            .withConversationDetailsById(TestConversation.MLS_CONVERSATION)
            .withConversationDetailsById(TestConversation.GROUP_VIEW_ENTITY(MLS_PROTOCOL_INFO))
            .withAddMemberAPISucceedChanged()
            .withSuccessfulAddMemberToMLSGroup()
            .arrange()

        conversationGroupRepository.addMembers(listOf(TestConversation.USER_1), TestConversation.ID)
            .shouldSucceed()

        // this is called in the mlsRepo
        verify(arrangement.memberJoinEventHandler)
            .suspendFunction(arrangement.memberJoinEventHandler::handle)
            .with(anything())
            .wasNotInvoked()

        verify(arrangement.mlsConversationRepository)
            .suspendFunction(arrangement.mlsConversationRepository::addMemberToMLSGroup)
            .with(eq(GROUP_ID), eq(listOf(TestConversation.USER_1)))
            .wasInvoked(exactly = once)
    }

    @Test
    fun givenAConversationAndAPISucceedsWithChange_whenRemovingMemberFromConversation_thenShouldSucceed() = runTest {
        val (arrangement, conversationGroupRepository) = Arrangement()
            .withConversationDetailsById(TestConversation.CONVERSATION)
            .withConversationDetailsById(TestConversation.GROUP_VIEW_ENTITY(PROTEUS_PROTOCOL_INFO))
            .withDeleteMemberAPISucceedChanged()
            .withSuccessfulHandleMemberLeaveEvent()
            .arrange()

        conversationGroupRepository.deleteMember(TestConversation.USER_1, TestConversation.ID)
            .shouldSucceed()

        verify(arrangement.memberLeaveEventHandler)
            .suspendFunction(arrangement.memberLeaveEventHandler::handle)
            .with(anything())
            .wasInvoked(exactly = once)
    }

    @Test
    fun givenAConversationAndAPISucceedsWithoutChange_whenRemovingMemberFromConversation_thenShouldSucceed() = runTest {
        val (arrangement, conversationGroupRepository) = Arrangement()
            .withConversationDetailsById(TestConversation.CONVERSATION)
            .withConversationDetailsById(TestConversation.GROUP_VIEW_ENTITY(PROTEUS_PROTOCOL_INFO))
            .withDeleteMemberAPISucceedUnchanged()
            .arrange()

        conversationGroupRepository.deleteMember(TestConversation.USER_1, TestConversation.ID)
            .shouldSucceed()

        verify(arrangement.memberLeaveEventHandler)
            .suspendFunction(arrangement.memberLeaveEventHandler::handle)
            .with(anything())
            .wasNotInvoked()
    }

    @Test
    fun givenAConversationAndAPIFailed_whenRemovingMemberFromConversation_thenShouldFail() = runTest {
        val (arrangement, conversationGroupRepository) = Arrangement()
            .withConversationDetailsById(TestConversation.CONVERSATION)
            .withConversationDetailsById(TestConversation.GROUP_VIEW_ENTITY(PROTEUS_PROTOCOL_INFO))
            .withDeleteMemberAPIFailed()
            .arrange()

        conversationGroupRepository.deleteMember(TestConversation.USER_1, TestConversation.ID)
            .shouldFail()

        verify(arrangement.memberLeaveEventHandler)
            .suspendFunction(arrangement.memberLeaveEventHandler::handle)
            .with(anything())
            .wasNotInvoked()
    }

    @Test
    fun givenAnMLSConversationAndAPISucceeds_whenRemovingLeavingConversation_thenShouldSucceed() = runTest {
        val (arrangement, conversationGroupRepository) = Arrangement()
            .withConversationDetailsById(TestConversation.MLS_CONVERSATION)
            .withConversationDetailsById(TestConversation.GROUP_VIEW_ENTITY(MLS_PROTOCOL_INFO))
            .withDeleteMemberAPISucceedChanged()
            .withSuccessfulMemberDeletion()
            .withSuccessfulLeaveMLSGroup()
            .withSuccessfulHandleMemberLeaveEvent()
            .arrange()

        conversationGroupRepository.deleteMember(TestUser.SELF.id, TestConversation.ID)
            .shouldSucceed()

        verify(arrangement.memberLeaveEventHandler)
            .suspendFunction(arrangement.memberLeaveEventHandler::handle)
            .with(anything())
            .wasInvoked(exactly = once)
        verify(arrangement.mlsConversationRepository)
            .suspendFunction(arrangement.mlsConversationRepository::leaveGroup)
            .with(eq(GROUP_ID))
            .wasInvoked(exactly = once)
        verify(arrangement.mlsConversationRepository)
            .suspendFunction(arrangement.mlsConversationRepository::removeMembersFromMLSGroup)
            .with(any(), any())
            .wasNotInvoked()
    }

    @Test
    fun givenAnMLSConversationAndAPISucceeds_whenRemoveMemberFromConversation_thenShouldSucceed() = runTest {
        val (arrangement, conversationGroupRepository) = Arrangement()
            .withConversationDetailsById(TestConversation.MLS_CONVERSATION)
            .withConversationDetailsById(TestConversation.GROUP_VIEW_ENTITY(MLS_PROTOCOL_INFO))
            .withDeleteMemberAPISucceedChanged()
            .withSuccessfulMemberDeletion()
            .withSuccessfulRemoveMemberFromMLSGroup()
            .arrange()

        conversationGroupRepository.deleteMember(TestConversation.USER_1, TestConversation.ID)
            .shouldSucceed()

        // this function called in the mlsRepo
        verify(arrangement.conversationDAO)
            .suspendFunction(arrangement.conversationDAO::deleteMemberByQualifiedID)
            .with(anything(), anything())
            .wasNotInvoked()
        verify(arrangement.mlsConversationRepository)
            .suspendFunction(arrangement.mlsConversationRepository::removeMembersFromMLSGroup)
            .with(eq(GROUP_ID), eq(listOf(TestConversation.USER_1)))
            .wasInvoked(exactly = once)
        verify(arrangement.mlsConversationRepository)
            .suspendFunction(arrangement.mlsConversationRepository::leaveGroup)
            .with(any())
            .wasNotInvoked()
    }

    @Test
    fun givenCodeAndKey_whenJoiningConversationSuccessWithChanged_thenResponseIsHandled() = runTest {
        val (code, key, uri) = Triple("code", "key", null)

        val (arrangement, conversationGroupRepository) = Arrangement()
            .withConversationDetailsById(TestConversation.CONVERSATION)
            .withConversationDetailsById(TestConversation.GROUP_VIEW_ENTITY(PROTEUS_PROTOCOL_INFO))
            .withJoinConversationAPIResponse(code, key, uri, NetworkResponse.Success(ADD_MEMBER_TO_CONVERSATION_SUCCESSFUL_RESPONSE, emptyMap(), 200))
            .withSuccessfulHandleMemberJoinEvent()
            .arrange()

        conversationGroupRepository.joinViaInviteCode(code, key, uri)
            .shouldSucceed()

        verify(arrangement.conversationApi)
            .suspendFunction(arrangement.conversationApi::joinConversation)
            .with(eq(code), eq(key), eq(uri))
            .wasInvoked(exactly = once)

        verify(arrangement.memberJoinEventHandler)
            .suspendFunction(arrangement.memberJoinEventHandler::handle)
            .with(any())
            .wasInvoked(exactly = once)

    }

    @Test
    fun givenCodeAndKey_whenJoiningConversationSuccessWithUnchanged_thenMemberJoinEventHandlerIsNotInvoked() = runTest {
        val (code, key, uri) = Triple("code", "key", null)

        val (arrangement, conversationGroupRepository) = Arrangement()
            .withConversationDetailsById(TestConversation.CONVERSATION)
            .withConversationDetailsById(TestConversation.GROUP_VIEW_ENTITY(PROTEUS_PROTOCOL_INFO))
            .withJoinConversationAPIResponse(code, key, uri, NetworkResponse.Success(ConversationMemberAddedResponse.Unchanged, emptyMap(), 204))
            .withSuccessfulHandleMemberJoinEvent()
            .arrange()

        conversationGroupRepository.joinViaInviteCode(code, key, uri)
            .shouldSucceed()

        verify(arrangement.conversationApi)
            .suspendFunction(arrangement.conversationApi::joinConversation)
            .with(eq(code), eq(key), eq(uri))
            .wasInvoked(exactly = once)

        verify(arrangement.memberJoinEventHandler)
            .suspendFunction(arrangement.memberJoinEventHandler::handle)
            .with(any())
            .wasNotInvoked()
    }

    private class Arrangement {

        @Mock
        val memberJoinEventHandler = mock(MemberJoinEventHandler::class)

        @Mock
        val memberLeaveEventHandler = mock(MemberLeaveEventHandler::class)

        @Mock
        val userRepository: UserRepository = mock(UserRepository::class)

        @Mock
        val conversationRepository: ConversationRepository = mock(ConversationRepository::class)

        @Mock
        val mlsConversationRepository: MLSConversationRepository = mock(MLSConversationRepository::class)

        @Mock
        val conversationDAO: ConversationDAO = mock(ConversationDAO::class)

        @Mock
        val conversationApi: ConversationApi = mock(ConversationApi::class)

        @Mock
        val selfTeamIdProvider: SelfTeamIdProvider = mock(SelfTeamIdProvider::class)

        val conversationGroupRepository =
            ConversationGroupRepositoryImpl(
                mlsConversationRepository,
                memberJoinEventHandler,
                memberLeaveEventHandler,
                conversationDAO,
                conversationApi,
                TestUser.SELF.id,
                selfTeamIdProvider
            )

        fun withJoinConversationAPIResponse(
            code: String,
            key: String,
            uri: String?,
            result: NetworkResponse<ConversationMemberAddedResponse>
        ): Arrangement = apply {
            given(conversationApi)
                .suspendFunction(conversationApi::joinConversation)
                .whenInvokedWith(eq(code), eq(key), eq(uri))
                .thenReturn(result)
        }

        fun withMlsConversationEstablished(): Arrangement {
            given(mlsConversationRepository)
                .suspendFunction(mlsConversationRepository::establishMLSGroup)
                .whenInvokedWith(anything(), anything())
                .thenReturn(Either.Right(Unit))
            return this
        }

        fun withInsertMembersWithQualifiedIdSucceeds(): Arrangement {
            given(conversationDAO)
                .suspendFunction(conversationDAO::insertMembersWithQualifiedId, fun2<List<MemberEntity>, QualifiedIDEntity>())
                .whenInvokedWith(any(), any())
                .thenDoNothing()
            return this
        }

        fun withCreateNewConversationAPI(result: NetworkResponse<ConversationResponse>): Arrangement = apply {
            given(conversationApi)
                .suspendFunction(conversationApi::createNewConversation)
                .whenInvokedWith(anything())
                .thenReturn(result)
        }

        fun withSelfTeamId(result: Either<StorageFailure, TeamId?>): Arrangement = apply {
            given(selfTeamIdProvider)
                .suspendFunction(selfTeamIdProvider::invoke)
                .whenInvoked()
                .thenReturn(result)
        }

        fun withInsertConversationSuccess(): Arrangement = apply {
            given(conversationDAO)
                .suspendFunction(conversationDAO::insertConversation)
                .whenInvokedWith(anything())
                .thenReturn(Unit)
        }

        fun withConversationDetailsById(conversation: Conversation) = apply {
            given(conversationRepository)
                .suspendFunction(conversationRepository::detailsById)
                .whenInvokedWith(anything())
                .thenReturn(Either.Right(conversation))
        }

        fun withConversationDetailsById(result: ConversationViewEntity?) = apply {
            given(conversationDAO)
                .suspendFunction(conversationDAO::getConversationByQualifiedID)
                .whenInvokedWith(any())
                .thenReturn(result)
        }

        fun withAddMemberAPISucceedChanged() = apply {
            given(conversationApi)
                .suspendFunction(conversationApi::addMember)
                .whenInvokedWith(any(), any())
                .thenReturn(
                    NetworkResponse.Success(
                        TestConversation.ADD_MEMBER_TO_CONVERSATION_SUCCESSFUL_RESPONSE,
                        mapOf(),
                        HttpStatusCode.OK.value
                    )
                )
        }

        fun withAddMemberAPISucceedUnchanged() = apply {
            given(conversationApi)
                .suspendFunction(conversationApi::addMember)
                .whenInvokedWith(any(), any())
                .thenReturn(
                    NetworkResponse.Success(
                        ConversationMemberAddedResponse.Unchanged,
                        mapOf(),
                        HttpStatusCode.OK.value
                    )
                )
        }

        fun withAddMemberAPIFailed() = apply {
            given(conversationApi)
                .suspendFunction(conversationApi::addMember)
                .whenInvokedWith(any(), any())
                .thenReturn(
                    NetworkResponse.Error(
                        KaliumException.ServerError(ErrorResponse(500, "error_message", "error_label"))
                    )
                )

        }

        fun withDeleteMemberAPISucceedChanged() = apply {
            given(conversationApi)
                .suspendFunction(conversationApi::removeMember)
                .whenInvokedWith(any(), any())
                .thenReturn(
                    NetworkResponse.Success(
                        TestConversation.REMOVE_MEMBER_FROM_CONVERSATION_SUCCESSFUL_RESPONSE,
                        mapOf(),
                        HttpStatusCode.OK.value
                    )
                )
        }

        fun withDeleteMemberAPISucceedUnchanged() = apply {
            given(conversationApi)
                .suspendFunction(conversationApi::removeMember)
                .whenInvokedWith(any(), any())
                .thenReturn(
                    NetworkResponse.Success(
                        ConversationMemberRemovedResponse.Unchanged,
                        mapOf(),
                        HttpStatusCode.OK.value
                    )
                )
        }

        fun withDeleteMemberAPIFailed() = apply {
            given(conversationApi)
                .suspendFunction(conversationApi::removeMember)
                .whenInvokedWith(any(), any())
                .thenReturn(
                    NetworkResponse.Error(
                        KaliumException.ServerError(ErrorResponse(500, "error_message", "error_label"))
                    )
                )

        }

        fun withFetchUsersIfUnknownByIdsSuccessful() = apply {
            given(userRepository)
                .suspendFunction(userRepository::fetchUsersIfUnknownByIds)
                .whenInvokedWith(any())
                .thenReturn(Either.Right(Unit))
        }

        fun withSuccessfulMemberDeletion() = apply {
            given(conversationDAO)
                .suspendFunction(conversationDAO::deleteMemberByQualifiedID)
                .whenInvokedWith(any(), any())
                .thenReturn(Unit)
        }

        fun withSuccessfulHandleMemberJoinEvent() = apply {
            given(memberJoinEventHandler)
                .suspendFunction(memberJoinEventHandler::handle)
                .whenInvokedWith(anything())
                .thenReturn(Either.Right(Unit))
        }

        fun withSuccessfulHandleMemberLeaveEvent() = apply {
            given(memberLeaveEventHandler)
                .suspendFunction(memberLeaveEventHandler::handle)
                .whenInvokedWith(anything())
                .thenReturn(Either.Right(Unit))
        }

        fun withSuccessfulLeaveMLSGroup() = apply {
            given(mlsConversationRepository)
                .suspendFunction(mlsConversationRepository::leaveGroup)
                .whenInvokedWith(any())
                .thenReturn(Either.Right(Unit))
        }

        fun withSuccessfulAddMemberToMLSGroup() = apply {
            given(mlsConversationRepository)
                .suspendFunction(mlsConversationRepository::addMemberToMLSGroup)
                .whenInvokedWith(any(), any())
                .thenReturn(Either.Right(Unit))
        }

        fun withSuccessfulRemoveMemberFromMLSGroup() = apply {
            given(mlsConversationRepository)
                .suspendFunction(mlsConversationRepository::removeMembersFromMLSGroup)
                .whenInvokedWith(any(), any())
                .thenReturn(Either.Right(Unit))
        }

        fun arrange() = this to conversationGroupRepository
    }

    companion object {
        private const val RAW_GROUP_ID = "mlsGroupId"
        val GROUP_ID = GroupID(RAW_GROUP_ID)
        val PROTEUS_PROTOCOL_INFO = ConversationEntity.ProtocolInfo.Proteus
        val MLS_PROTOCOL_INFO = ConversationEntity.ProtocolInfo
            .MLS(
                RAW_GROUP_ID,
                groupState = ConversationEntity.GroupState.ESTABLISHED,
                0UL,
                Instant.parse("2021-03-30T15:36:00.000Z"),
                cipherSuite = ConversationEntity.CipherSuite.MLS_128_DHKEMX25519_AES128GCM_SHA256_Ed25519
            )

        const val GROUP_NAME = "Group Name"

        val USER_ID = TestUser.USER_ID
        val CONVERSATION_RESPONSE = ConversationResponse(
            "creator",
            ConversationMembersResponse(
                ConversationMemberDTO.Self(TestUser.SELF.id.toApi(), "wire_member"),
                emptyList()
            ),
            GROUP_NAME,
            TestConversation.NETWORK_ID,
            null,
            0UL,
            ConversationResponse.Type.GROUP,
            0,
            null,
            ConvProtocol.PROTEUS,
            lastEventTime = "2022-03-30T15:36:00.000Z",
            access = setOf(ConversationAccessDTO.INVITE, ConversationAccessDTO.CODE),
            accessRole = setOf(
                ConversationAccessRoleDTO.GUEST,
                ConversationAccessRoleDTO.TEAM_MEMBER,
                ConversationAccessRoleDTO.NON_TEAM_MEMBER
            ),
            mlsCipherSuiteTag = null,
            receiptMode = ReceiptMode.DISABLED
        )
        private val TEST_QUALIFIED_ID_ENTITY = PersistenceQualifiedId("value", "domain")
    }
}
