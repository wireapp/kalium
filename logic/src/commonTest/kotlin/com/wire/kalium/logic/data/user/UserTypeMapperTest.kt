package com.wire.kalium.logic.data.user

import com.wire.kalium.logic.data.conversation.UserType
import com.wire.kalium.logic.data.id.QualifiedID
import com.wire.kalium.logic.data.publicuser.model.OtherUser
import kotlin.test.Test
import kotlin.test.assertEquals

class UserTypeMapperTest {

    private val userTypeMapper : UserTypeMapper = UserTypeMapperImpl()

    @Test
    fun givenDomainAndTeamAreEqual_whenMappingToConversationDetails_ThenConversationDetailsUserTypeIsInternal() {
        //given
        val commonDomain = "commonDomain"
        val commonTeam = "commonTeam"
        val selfUser = generateTestSelfUser(commonDomain, commonTeam)
        val otherUser = generateTestOtherUser(commonDomain, commonTeam)

        //when
        val result = userTypeMapper.fromOtherUserAndSelfUser(otherUser, selfUser)
        //then
        assertEquals(UserType.INTERNAL, result)
    }

    @Test
    fun givenDomainAreDifferentButTeamsAreEqual_whenMappingToConversationDetails_ThenConversationDetailsUserTypeIsInternal() {
        //given
        val commonTeam = "commonTeam"

        val selfUser = generateTestSelfUser("domainA", commonTeam)
        val otherUser = generateTestOtherUser("domainB", commonTeam)
        //when
        val result = userTypeMapper.fromOtherUserAndSelfUser(otherUser, selfUser)
        //then
        assertEquals(UserType.INTERNAL, result)
    }

    @Test
    fun givenCommonNotWireDomainAndDifferentTeam_whenMappingToConversationDetails_ThenConversationDetailsUserTypeIsFederated() {
        //given
        val commonDomain = "commonDomain"

        val selfUser = generateTestSelfUser(commonDomain, "teamA")
        val otherUser = generateTestOtherUser(commonDomain, "teamB")
        //when
        val result = userTypeMapper.fromOtherUserAndSelfUser(otherUser, selfUser)
        //then
        assertEquals(UserType.FEDERATED, result)
    }

    @Test
    fun givenDifferentNotWireDomainAndDifferentTeam_whenMappingToConversationDetails_ThenConversationDetailsUserTypeIsFederated() {
        //given
        val selfUser = generateTestSelfUser("domainA", "teamA")
        val otherUser = generateTestOtherUser("domainB", "teamB")
        //when
        val result = userTypeMapper.fromOtherUserAndSelfUser(otherUser, selfUser)
        //then
        assertEquals(UserType.FEDERATED, result)
    }

    @Test
    fun givenUsingWireDomainAndDifferentTeam_whenMappingToConversationDetails_ThenConversationDetailsUserTypeIsGuest() {
        //given
        val selfUser = generateTestSelfUser("domainA", "teamA")
        val otherUser = generateTestOtherUser("testDomain.wire.com", "teamB")
        //when
        val result = userTypeMapper.fromOtherUserAndSelfUser(otherUser, selfUser)
        //then
        assertEquals(UserType.GUEST, result)
    }

    private fun generateTestSelfUser(domain: String, team: String?): SelfUser {
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
            completePicture = "testCompletePicture",
            availabilityStatus = UserAvailabilityStatus.NONE
        )
    }

    private fun generateTestOtherUser(domain: String, team: String?): OtherUser {
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
            completePicture = "testCompletePicture",
            availabilityStatus = UserAvailabilityStatus.NONE,
        )
    }
}
