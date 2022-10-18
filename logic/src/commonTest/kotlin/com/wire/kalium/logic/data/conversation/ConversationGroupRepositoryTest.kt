package com.wire.kalium.logic.data.conversation

import com.wire.kalium.logic.data.id.GroupID
import com.wire.kalium.logic.data.id.PersistenceQualifiedId
import com.wire.kalium.logic.data.user.UserRepository
import com.wire.kalium.logic.di.MapperProvider
import com.wire.kalium.logic.framework.TestConversation
import com.wire.kalium.logic.framework.TestUser
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.util.shouldFail
import com.wire.kalium.logic.util.shouldSucceed
import com.wire.kalium.network.api.base.authenticated.conversation.ConvProtocol
import com.wire.kalium.network.api.base.authenticated.conversation.ConvProtocol.MLS
import com.wire.kalium.network.api.base.authenticated.conversation.ConversationApi
import com.wire.kalium.network.api.base.authenticated.conversation.ConversationMemberAddedDTO
import com.wire.kalium.network.api.base.authenticated.conversation.ConversationMemberDTO
import com.wire.kalium.network.api.base.authenticated.conversation.ConversationMemberRemovedDTO
import com.wire.kalium.network.api.base.authenticated.conversation.ConversationMembersResponse
import com.wire.kalium.network.api.base.authenticated.conversation.ConversationResponse
import com.wire.kalium.network.api.base.model.ConversationAccessDTO
import com.wire.kalium.network.api.base.model.ConversationAccessRoleDTO
import com.wire.kalium.network.api.base.model.ErrorResponse
import com.wire.kalium.network.exceptions.KaliumException
import com.wire.kalium.network.utils.NetworkResponse
import com.wire.kalium.persistence.dao.ConversationDAO
import com.wire.kalium.persistence.dao.ConversationEntity
import com.wire.kalium.persistence.dao.QualifiedIDEntity
import io.ktor.http.HttpStatusCode
import io.mockative.Mock
import io.mockative.any
import io.mockative.anything
import io.mockative.classOf
import io.mockative.configure
import io.mockative.eq
import io.mockative.fun2
import io.mockative.given
import io.mockative.mock
import io.mockative.once
import io.mockative.thenDoNothing
import io.mockative.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import kotlin.test.BeforeTest
import kotlin.test.Test
import com.wire.kalium.persistence.dao.Member as MemberEntity

@Suppress("LargeClass")
@OptIn(ExperimentalCoroutinesApi::class)
// TODO: Refactor using Arrangement pattern
class ConversationGroupRepositoryTest {

    @Mock
    private val userRepository = mock(UserRepository::class)

    @Mock
    private val conversationRepository = mock(classOf<ConversationRepository>())

    @Mock
    private val mlsConversationRepository = mock(classOf<MLSConversationRepository>())

    @Mock
    private val conversationDAO = configure(mock(ConversationDAO::class)) { stubsUnitByDefault = true }

    @Mock
    private val conversationApi = mock(ConversationApi::class)

    private lateinit var conversationGroupRepository: ConversationGroupRepository

    @BeforeTest
    fun setup() {
        conversationGroupRepository = ConversationGroupRepositoryImpl(
            userRepository,
            conversationRepository,
            mlsConversationRepository,
            conversationDAO,
            conversationApi,
            TestUser.SELF.id
        )
    }

    @Test
    fun givenSelfUserBelongsToATeam_whenCallingCreateGroupConversation_thenConversationIsCreatedAtBackendAndPersisted() = runTest {

        given(conversationApi)
            .suspendFunction(conversationApi::createNewConversation)
            .whenInvokedWith(anything())
            .thenReturn(NetworkResponse.Success(CONVERSATION_RESPONSE, emptyMap(), 201))

        given(userRepository)
            .coroutine { userRepository.observeSelfUser() }
            .then { flowOf(TestUser.SELF) }

        given(conversationDAO)
            .suspendFunction(conversationDAO::insertConversation)
            .whenInvokedWith(anything())
            .thenDoNothing()

        given(conversationDAO)
            .suspendFunction(conversationDAO::getConversationByQualifiedID)
            .whenInvokedWith(any())
            .thenReturn(TestConversation.GROUP_VIEW_ENTITY(PROTEUS_PROTOCOL_INFO))

        given(conversationDAO)
            .suspendFunction(conversationDAO::insertMembersWithQualifiedId, fun2<List<MemberEntity>, QualifiedIDEntity>())
            .whenInvokedWith(anything(), anything())
            .thenDoNothing()

        val result = conversationGroupRepository.createGroupConversation(
            GROUP_NAME,
            listOf(TestUser.USER_ID),
            ConversationOptions(protocol = ConversationOptions.Protocol.PROTEUS)
        )

        result.shouldSucceed { }

        verify(conversationDAO)
            .suspendFunction(conversationDAO::insertConversation)
            .with(anything())
            .wasInvoked(once)

        verify(conversationDAO)
            .suspendFunction(conversationDAO::insertMembersWithQualifiedId, fun2<List<MemberEntity>, QualifiedIDEntity>())
            .with(anything(), anything())
            .wasInvoked(once)
    }

    @Test
    fun givenSelfUserDoesNotBelongToATeam_whenCallingCreateGroupConversation_thenConversationIsCreatedAtBackendAndPersisted() = runTest {

        val selfUserWithoutTeam = TestUser.SELF.copy(teamId = null)

        given(conversationApi)
            .suspendFunction(conversationApi::createNewConversation)
            .whenInvokedWith(anything())
            .thenReturn(NetworkResponse.Success(CONVERSATION_RESPONSE, emptyMap(), 201))

        given(userRepository)
            .coroutine { userRepository.observeSelfUser() }
            .then { flowOf(selfUserWithoutTeam) }

        given(conversationDAO)
            .suspendFunction(conversationDAO::getConversationByQualifiedID)
            .whenInvokedWith(any())
            .thenReturn(TestConversation.GROUP_VIEW_ENTITY(PROTEUS_PROTOCOL_INFO))

        given(conversationDAO)
            .suspendFunction(conversationDAO::insertConversation)
            .whenInvokedWith(anything())
            .thenDoNothing()

        given(conversationDAO)
            .suspendFunction(conversationDAO::insertMembersWithQualifiedId, fun2<List<MemberEntity>, QualifiedIDEntity>())
            .whenInvokedWith(anything(), anything())
            .thenDoNothing()

        val result = conversationGroupRepository.createGroupConversation(
            GROUP_NAME,
            listOf(TestUser.USER_ID),
            ConversationOptions(protocol = ConversationOptions.Protocol.PROTEUS)
        )

        result.shouldSucceed { }

        verify(conversationDAO)
            .suspendFunction(conversationDAO::insertConversation)
            .with(anything())
            .wasInvoked(once)

        verify(conversationDAO)
            .suspendFunction(conversationDAO::insertMembersWithQualifiedId, fun2<List<MemberEntity>, QualifiedIDEntity>())
            .with(anything(), anything())
            .wasInvoked(once)
    }

    @Test
    fun givenMLSProtocolIsUsed_whenCallingCreateGroupConversation_thenMLSGroupIsEstablished() = runTest {
        val conversationResponse = CONVERSATION_RESPONSE.copy(protocol = MLS)

        given(conversationApi)
            .suspendFunction(conversationApi::createNewConversation)
            .whenInvokedWith(anything())
            .thenReturn(NetworkResponse.Success(conversationResponse, emptyMap(), 201))

        given(userRepository)
            .coroutine { userRepository.observeSelfUser() }
            .then { flowOf(TestUser.SELF) }

        given(conversationDAO)
            .suspendFunction(conversationDAO::insertConversation)
            .whenInvokedWith(anything())
            .thenDoNothing()

        given(conversationDAO)
            .suspendFunction(conversationDAO::getConversationByQualifiedID)
            .whenInvokedWith(any())
            .thenReturn(TestConversation.GROUP_VIEW_ENTITY(PROTEUS_PROTOCOL_INFO))

        given(conversationDAO)
            .suspendFunction(conversationDAO::insertMembersWithQualifiedId, fun2<List<MemberEntity>, QualifiedIDEntity>())
            .whenInvokedWith(any(), any())
            .thenDoNothing()

        given(mlsConversationRepository)
            .suspendFunction(mlsConversationRepository::establishMLSGroup)
            .whenInvokedWith(anything())
            .then { Either.Right(Unit) }

        val result = conversationGroupRepository.createGroupConversation(
            GROUP_NAME,
            listOf(TestUser.USER_ID),
            ConversationOptions(protocol = ConversationOptions.Protocol.MLS)
        )

        result.shouldSucceed { }

        verify(conversationDAO)
            .suspendFunction(conversationDAO::insertConversation)
            .with(anything())
            .wasInvoked(once)

        verify(conversationDAO)
            .suspendFunction(conversationDAO::insertMembersWithQualifiedId, fun2<List<MemberEntity>, QualifiedIDEntity>())
            .with(anything(), anything())
            .wasInvoked(once)

        verify(mlsConversationRepository)
            .suspendFunction(mlsConversationRepository::establishMLSGroup)
            .with(anything())
            .wasInvoked(once)
    }

    @Test
    fun givenAConversationAndAPISucceedsWithChange_whenAddingMembersToConversation_thenShouldSucceed() = runTest {
        val (arrangement, conversationGroupRepository) = Arrangement()
            .withConversationDetailsById(TestConversation.CONVERSATION)
            .withConversationProtocolIs(PROTEUS_PROTOCOL_INFO)
            .withFetchUsersIfUnknownByIdsSuccessful()
            .withAddMemberAPISucceedChanged()
            .withSuccessfulPersistMember()
//             .withSuccessfulMemberInsert()
            .arrange()

        conversationGroupRepository.addMembers(listOf(TestConversation.USER_1), TestConversation.ID)
            .shouldSucceed()

        verify(arrangement.conversationRepository)
            .suspendFunction(conversationRepository::persistMembers)
            .with(anything(), eq(TestConversation.ID))
            .wasInvoked(exactly = once)

//         verify(arrangement.conversationDAO)
//             .suspendFunction(arrangement.conversationDAO::insertMembersWithQualifiedId, fun2<List<MemberEntity>, QualifiedIDEntity>())
//             .with(anything(), anything())
//             .wasInvoked(exactly = once)
    }

    @Test
    fun givenAConversationAndAPISucceedsWithoutChange_whenAddingMembersToConversation_thenShouldSucceed() = runTest {
        val (arrangement, conversationGroupRepository) = Arrangement()
            .withConversationDetailsById(TestConversation.CONVERSATION)
            .withConversationProtocolIs(PROTEUS_PROTOCOL_INFO)
            .withFetchUsersIfUnknownByIdsSuccessful()
            .withAddMemberAPISucceedUnchanged()
            .withSuccessfulPersistMember()
//             .withSuccessfulMemberInsert()
            .arrange()

        conversationGroupRepository.addMembers(listOf(TestConversation.USER_1), TestConversation.ID)
            .shouldSucceed()

        verify(arrangement.conversationRepository)
            .suspendFunction(conversationRepository::persistMembers)
            .with(anything(), eq(TestConversation.ID))
            .wasInvoked(exactly = once)

//         verify(arrangement.conversationDAO)
//             .suspendFunction(arrangement.conversationDAO::insertMembersWithQualifiedId, fun2<List<MemberEntity>, QualifiedIDEntity>())
//             .with(anything(), anything())
//             .wasInvoked(exactly = once)
    }

    @Test
    fun givenAConversationAndAPIFailed_whenAddingMembersToConversation_thenShouldNotSucceed() = runTest {
        val (arrangement, conversationGroupRepository) = Arrangement()
            .withConversationDetailsById(TestConversation.CONVERSATION)
            .withConversationProtocolIs(PROTEUS_PROTOCOL_INFO)
            .withAddMemberAPIFailed()
            .arrange()

        conversationGroupRepository.addMembers(listOf(TestConversation.USER_1), TestConversation.ID)
            .shouldFail()

        verify(arrangement.conversationDAO)
            .suspendFunction(conversationDAO::insertMembersWithQualifiedId, fun2<List<MemberEntity>, QualifiedIDEntity>())
            .with(any(), any())
            .wasNotInvoked()
    }

    @Test
    fun givenAnMLSConversationAndAPISucceeds_whenAddMemberFromConversation_thenShouldSucceed() = runTest {
        val (arrangement, conversationGroupRepository) = Arrangement()
            .withConversationDetailsById(TestConversation.MLS_CONVERSATION)
            .withConversationProtocolIs(MLS_PROTOCOL_INFO)
            .withAddMemberAPISucceedChanged()
            .withSuccessfulPersistMember()
//             .withSuccessfulMemberInsert()
            .withSuccessfulAddMemberToMLSGroup()
            .arrange()

        conversationGroupRepository.addMembers(listOf(TestConversation.USER_1), TestConversation.ID)
            .shouldSucceed { it is MemberChangeResult.Changed }

        // this function called in the mlsRepo
        verify(arrangement.conversationDAO)
            .suspendFunction(arrangement.conversationDAO::deleteMemberByQualifiedID)
            .with(anything(), anything())
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
            .withConversationProtocolIs(PROTEUS_PROTOCOL_INFO)
            .withDeleteMemberAPISucceedChanged()
            .withSuccessfulMemberDeletion()
            .arrange()

        conversationGroupRepository.deleteMember(TestConversation.USER_1, TestConversation.ID)
            .shouldSucceed { it is MemberChangeResult.Changed }

        verify(arrangement.conversationDAO)
            .suspendFunction(arrangement.conversationDAO::deleteMemberByQualifiedID)
            .with(anything(), anything())
            .wasInvoked(exactly = once)
    }

    @Test
    fun givenAConversationAndAPISucceedsWithoutChange_whenRemovingMemberFromConversation_thenShouldSucceed() = runTest {
        val (arrangement, conversationGroupRepository) = Arrangement()
            .withConversationDetailsById(TestConversation.CONVERSATION)
            .withConversationProtocolIs(PROTEUS_PROTOCOL_INFO)
            .withDeleteMemberAPISucceedUnchanged()
            .withSuccessfulMemberDeletion()
            .arrange()

        conversationGroupRepository.deleteMember(TestConversation.USER_1, TestConversation.ID)
            .shouldSucceed { it is MemberChangeResult.Unchanged }

        verify(arrangement.conversationDAO)
            .suspendFunction(arrangement.conversationDAO::deleteMemberByQualifiedID)
            .with(anything(), anything())
            .wasInvoked(exactly = once)
    }

    @Test
    fun givenAConversationAndAPIFailed_whenRemovingMemberFromConversation_thenShouldFail() = runTest {
        val (arrangement, conversationGroupRepository) = Arrangement()
            .withConversationDetailsById(TestConversation.CONVERSATION)
            .withConversationProtocolIs(PROTEUS_PROTOCOL_INFO)
            .withDeleteMemberAPIFailed()
            .withSuccessfulMemberDeletion()
            .arrange()

        conversationGroupRepository.deleteMember(TestConversation.USER_1, TestConversation.ID)
            .shouldFail()

        verify(arrangement.conversationDAO)
            .suspendFunction(arrangement.conversationDAO::deleteMemberByQualifiedID)
            .with(anything(), anything())
            .wasNotInvoked()
    }

    @Test
    fun givenAnMLSConversationAndAPISucceeds_whenRemovingLeavingConversation_thenShouldSucceed() = runTest {
        val (arrangement, conversationGroupRepository) = Arrangement()
            .withConversationDetailsById(TestConversation.MLS_CONVERSATION)
            .withConversationProtocolIs(MLS_PROTOCOL_INFO)
            .withDeleteMemberAPISucceedChanged()
            .withSuccessfulMemberDeletion()
            .withSuccessfulLeaveMLSGroup()
            .arrange()

        conversationGroupRepository.deleteMember(TestUser.SELF.id, TestConversation.ID)
            .shouldSucceed { it is MemberChangeResult.Changed }

        verify(arrangement.conversationDAO)
            .suspendFunction(arrangement.conversationDAO::deleteMemberByQualifiedID)
            .with(anything(), anything())
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
            .withConversationProtocolIs(MLS_PROTOCOL_INFO)
            .withDeleteMemberAPISucceedChanged()
            .withSuccessfulMemberDeletion()
            .withSuccessfulRemoveMemberFromMLSGroup()
            .arrange()

        conversationGroupRepository.deleteMember(TestConversation.USER_1, TestConversation.ID)
            .shouldSucceed { it is MemberChangeResult.Changed }

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

    private class Arrangement {
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

        val conversationGroupRepository =
            ConversationGroupRepositoryImpl(
                userRepository,
                conversationRepository,
                mlsConversationRepository,
                conversationDAO,
                conversationApi,
                TestUser.SELF.id
            )

        fun withConversationDetailsById(conversation: Conversation) = apply {
            given(conversationRepository)
                .suspendFunction(conversationRepository::detailsById)
                .whenInvokedWith(anything())
                .thenReturn(Either.Right(conversation))
        }

        fun withConversationProtocolIs(protocolInfo: ConversationEntity.ProtocolInfo) = apply {
            given(conversationDAO)
                .suspendFunction(conversationDAO::getConversationByQualifiedID)
                .whenInvokedWith(any())
                .thenReturn(TestConversation.GROUP_VIEW_ENTITY(protocolInfo))
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
                        ConversationMemberAddedDTO.Unchanged,
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
                        ConversationMemberRemovedDTO.Unchanged,
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

        fun withSuccessfulPersistMember() = apply {
            given(conversationRepository)
                .suspendFunction(conversationRepository::persistMembers)
                .whenInvokedWith(any(), any())
                .thenReturn(Either.Right(Unit))
        }

//         fun withSuccessfulMemberInsert() = apply {
//             given(conversationDAO)
//                 .suspendFunction(conversationDAO::insertMembersWithQualifiedId, fun2<List<MemberEntity>, QualifiedIDEntity>())
//                 .whenInvokedWith(any(), any())
//                 .thenDoNothing()
//         }

        fun withSuccessfulMemberDeletion() = apply {
            given(conversationDAO)
                .suspendFunction(conversationDAO::deleteMemberByQualifiedID)
                .whenInvokedWith(any(), any())
                .thenReturn(Unit)
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
                ConversationMemberDTO.Self(MapperProvider.idMapper().toApiModel(TestUser.SELF.id), "wire_member"),
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
            mlsCipherSuiteTag = null
        )
        private val TEST_QUALIFIED_ID_ENTITY = PersistenceQualifiedId("value", "domain")
    }
}
