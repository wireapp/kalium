package com.wire.kalium.logic.data.conversation

import com.wire.kalium.logic.data.id.IdMapper
import com.wire.kalium.logic.data.id.QualifiedID
import com.wire.kalium.logic.data.id.TeamId
import com.wire.kalium.logic.data.publicuser.model.OtherUser
import com.wire.kalium.logic.data.user.ConnectionState
import com.wire.kalium.logic.data.user.SelfUser
import com.wire.kalium.network.api.ConversationId
import com.wire.kalium.network.api.UserId
import com.wire.kalium.network.api.conversation.ConvProtocol
import com.wire.kalium.network.api.conversation.ConversationMembersResponse
import com.wire.kalium.network.api.conversation.ConversationOtherMembersResponse
import com.wire.kalium.network.api.conversation.ConversationResponse
import com.wire.kalium.network.api.conversation.ConversationSelfMemberResponse
import com.wire.kalium.network.api.conversation.MutedStatus
import com.wire.kalium.persistence.dao.ConversationEntity
import com.wire.kalium.persistence.dao.QualifiedIDEntity
import io.mockative.Mock
import io.mockative.any
import io.mockative.classOf
import io.mockative.given
import io.mockative.mock
import io.mockative.once
import io.mockative.verify
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

class ConversationMapperTest {

    @Mock
    val idMapper = mock(classOf<IdMapper>())

    @Mock
    val conversationStatusMapper = mock(classOf<ConversationStatusMapper>())

    private lateinit var conversationMapper: ConversationMapper

    @BeforeTest
    fun setup() {
        conversationMapper = ConversationMapperImpl(idMapper, conversationStatusMapper)
    }

    @Test
    fun givenAConversationResponse_whenMappingFromConversationResponse_thenTheNameShouldBeCorrect() {
        val response = CONVERSATION_RESPONSE
        val transformedConversationId = QualifiedIDEntity("transformed", "tDomain")

        given(idMapper)
            .function(idMapper::fromApiToDao)
            .whenInvokedWith(any())
            .then { transformedConversationId }

        given(conversationStatusMapper)
            .function(conversationStatusMapper::fromDaoModel)
            .whenInvokedWith(any())
            .then { MutedConversationStatus.AllAllowed }

        given(conversationStatusMapper)
            .function(conversationStatusMapper::fromApiToDaoModel)
            .whenInvokedWith(any())
            .then { ConversationEntity.MutedStatus.ALL_ALLOWED }

        val mappedResponse = conversationMapper.fromApiModelToDaoModel(response, mlsGroupState = null, SELF_USER_TEAM_ID)

        assertEquals(mappedResponse.name, response.name)
    }

    @Test
    fun givenAConversationResponse_whenMappingFromConversationResponseToDaoModel_thenShouldCallIdMapperToMapConversationId() {
        val response = CONVERSATION_RESPONSE
        val originalConversationId = ORIGINAL_CONVERSATION_ID
        val transformedConversationId = QualifiedIDEntity("transformed", "tDomain")

        given(idMapper)
            .function(idMapper::fromApiToDao)
            .whenInvokedWith(any())
            .then { transformedConversationId }

        given(conversationStatusMapper)
            .function(conversationStatusMapper::fromDaoModel)
            .whenInvokedWith(any())
            .then { MutedConversationStatus.AllAllowed }

        given(conversationStatusMapper)
            .function(conversationStatusMapper::fromApiToDaoModel)
            .whenInvokedWith(any())
            .then { ConversationEntity.MutedStatus.ALL_ALLOWED }

        conversationMapper.fromApiModelToDaoModel(response, mlsGroupState = null, SELF_USER_TEAM_ID)

        verify(idMapper)
            .invocation { idMapper.fromApiToDao(originalConversationId) }
            .wasInvoked(exactly = once)
    }

    @Test
    fun givenAFakeTeamOneOnOneConversationResponse_whenMappingFromConversationResponseToDaoModel_thenShouldMapToOneOnOneConversation() {
        val response = CONVERSATION_RESPONSE.copy(
            // Looks like a Group
            type = ConversationResponse.Type.GROUP,
            // No Name
            name = null,
            // Only one other participant
            members = CONVERSATION_RESPONSE.members.copy(otherMembers = listOf(OTHER_MEMBERS.first())),
            // Same team as user
            teamId = SELF_USER_TEAM_ID.value
        )

        given(idMapper)
            .function(idMapper::fromApiToDao)
            .whenInvokedWith(any())
            .then { QualifiedIDEntity("transformed", "tDomain") }

        given(conversationStatusMapper)
            .function(conversationStatusMapper::fromDaoModel)
            .whenInvokedWith(any())
            .then { MutedConversationStatus.AllAllowed }

        given(conversationStatusMapper)
            .function(conversationStatusMapper::fromApiToDaoModel)
            .whenInvokedWith(any())
            .then { ConversationEntity.MutedStatus.ALL_ALLOWED }

        val result = conversationMapper.fromApiModelToDaoModel(response, mlsGroupState = null, SELF_USER_TEAM_ID)

        assertEquals(ConversationEntity.Type.ONE_ON_ONE, result.type)
    }

    @Test
    fun givenAGroupConversationResponseWithoutName_whenMappingFromConversationResponseToDaoModel_thenShouldMapToGroupType() {
        val response = CONVERSATION_RESPONSE.copy(type = ConversationResponse.Type.GROUP, name = null)

        given(idMapper)
            .function(idMapper::fromApiToDao)
            .whenInvokedWith(any())
            .then { QualifiedIDEntity("transformed", "tDomain") }

        given(conversationStatusMapper)
            .function(conversationStatusMapper::fromDaoModel)
            .whenInvokedWith(any())
            .then { MutedConversationStatus.AllAllowed }

        given(conversationStatusMapper)
            .function(conversationStatusMapper::fromApiToDaoModel)
            .whenInvokedWith(any())
            .then { ConversationEntity.MutedStatus.ALL_ALLOWED }

        val result = conversationMapper.fromApiModelToDaoModel(response, mlsGroupState = null, SELF_USER_TEAM_ID)

        assertEquals(ConversationEntity.Type.GROUP, result.type)
    }

    @Test
    fun givenDomainAndTeamAreEqual_whenMappingToConversationDetails_ThenConversationDetailsUserTypeIsInternal() {
        //given
        val commonDomain = "commonDomain"
        val commonTeam = "commonTeam"
        val selfUser = generateTestSelfUser(commonDomain, commonTeam)
        val otherUser = generateTestOtherUser(commonDomain, commonTeam)
        //when
        val result = conversationMapper.toConversationDetailsOneToOne(TEST_CONVERSATION, otherUser, selfUser)
        //then
        assertEquals(UserType.INTERNAL, result.userType)
    }

    @Test
    fun givenDomainAreDifferentButTeamsAreEqual_whenMappingToConversationDetails_ThenConversationDetailsUserTypeIsInternal() {
        //given
        val commonTeam = "commonTeam"

        val selfUser = generateTestSelfUser("domainA", commonTeam)
        val otherUser = generateTestOtherUser("domainB", commonTeam)
        //when
        val result = conversationMapper.toConversationDetailsOneToOne(TEST_CONVERSATION, otherUser, selfUser)
        //then
        assertEquals(UserType.INTERNAL, result.userType)
    }

    @Test
    fun givenCommonNotWireDomainAndDifferentTeam_whenMappingToConversationDetails_ThenConversationDetailsUserTypeIsFederated() {
        //given
        val commonDomain = "commonDomain"

        val selfUser = generateTestSelfUser(commonDomain, "teamA")
        val otherUser = generateTestOtherUser(commonDomain, "teamB")
        //when
        val result = conversationMapper.toConversationDetailsOneToOne(TEST_CONVERSATION, otherUser, selfUser)
        //then
        assertEquals(UserType.FEDERATED, result.userType)
    }

    @Test
    fun givenDifferentNotWireDomainAndDifferentTeam_whenMappingToConversationDetails_ThenConversationDetailsUserTypeIsFederated() {
        //given
        val selfUser = generateTestSelfUser("domainA", "teamA")
        val otherUser = generateTestOtherUser("domainB", "teamB")
        //when
        val result = conversationMapper.toConversationDetailsOneToOne(TEST_CONVERSATION, otherUser, selfUser)
        //then
        assertEquals(UserType.FEDERATED, result.userType)
    }

    @Test
    fun givenUsingWireDomainAndDifferentTeam_whenMappingToConversationDetails_ThenConversationDetailsUserTypeIsGuest() {
        //given
        val selfUser = generateTestSelfUser("domainA", "teamA")
        val otherUser = generateTestOtherUser("testDomain.wire.com", "teamB")
        //when
        val result = conversationMapper.toConversationDetailsOneToOne(TEST_CONVERSATION, otherUser, selfUser)
        //then
        assertEquals(UserType.GUEST, result.userType)
    }

    private companion object {
        val ORIGINAL_CONVERSATION_ID = ConversationId("original", "oDomain")
        val SELF_USER_TEAM_ID = TeamId("teamID")
        val SELF_MEMBER_RESPONSE =
            ConversationSelfMemberResponse(
                UserId("selfId", "selfDomain"), "2022-04-11T20:24:57.237Z", MutedStatus.ALL_ALLOWED
            )
        val OTHER_MEMBERS = listOf(ConversationOtherMembersResponse(null, UserId("other1", "domain1")))
        val MEMBERS_RESPONSE = ConversationMembersResponse(SELF_MEMBER_RESPONSE, OTHER_MEMBERS)
        val CONVERSATION_RESPONSE = ConversationResponse(
            "creator",
            MEMBERS_RESPONSE,
            "name",
            ORIGINAL_CONVERSATION_ID,
            null,
            ConversationResponse.Type.GROUP,
            null,
            null,
            ConvProtocol.PROTEUS,
            lastEventTime = "2022-03-30T15:36:00.000Z"
        )

        val TEST_CONVERSATION = Conversation(
            id = QualifiedID(
                value = "testValue",
                domain = "testDomain",
            ),
            name = "testName",
            type = ConversationEntity.Type.ONE_ON_ONE,
            teamId = TeamId("test"),
            mutedStatus = MutedConversationStatus.AllAllowed,
            lastNotificationDate = "testNotificationDate",
            lastModifiedDate = "testLastModifiedDate"
        )

        fun generateTestSelfUser(domain: String, team: String?): SelfUser {
            return SelfUser(
                id = QualifiedID(
                    value = "testValue",
                    domain = domain,
                ),
                name = "testName",
                handle = "testHandle",
                email = "testEmail",
                phone = "testPhone",
                accentId = 0,
                team = team,
                connectionStatus = ConnectionState.ACCEPTED,
                previewPicture = "testPreviewPicture",
                completePicture = "testCompletePicture"
            )
        }

        fun generateTestOtherUser(domain: String, team: String?): OtherUser {
            return OtherUser(
                id = QualifiedID(
                    value = "testValue",
                    domain = domain,
                ),
                name = "testName",
                handle = "testHandle",
                email = "testEmail",
                phone = "testPhone",
                accentId = 0,
                team = team,
                connectionStatus = ConnectionState.ACCEPTED,
                previewPicture = "testPreviewPicture",
                completePicture = "testCompletePicture"
            )
        }
    }
}
