package com.wire.kalium.logic.data.user

import com.wire.kalium.logic.data.user.type.DomainUserTypeMapper
import com.wire.kalium.logic.data.user.type.DomainUserTypeMapperImpl
import com.wire.kalium.logic.data.user.type.UserType
import kotlin.test.Test
import kotlin.test.assertEquals

class DomainUserTypeMapperTest {

    private val userTypeMapper : DomainUserTypeMapper = DomainUserTypeMapperImpl()

    @Test
    fun givenDomainAndTeamAreEqual_whenMappingToConversationDetails_ThenConversationDetailsUserTypeIsInternal() {
        //when
        val result = userTypeMapper.fromTeamDomainAndPermission(
            "someDomain",
            "someTeamId",
            "someTeamId",
            "someDomain"
        )
        //then
        assertEquals(UserType.INTERNAL, result)
    }

    @Test
    fun givenCommonNotWireDomainAndDifferentTeam_whenMappingToConversationDetails_ThenConversationDetailsUserTypeIsFederated() {
        //given
        val result = userTypeMapper.fromTeamDomainAndPermission(
            "domainB",
            "teamA",
            "teamB",
            "domainA"
        )
        //then
        assertEquals(UserType.FEDERATED, result)
    }

    @Test
    fun givenUsingSameDomainAndDifferentTeam_whenMappingToConversationDetails_ThenConversationDetailsUserTypeIsGuest() {
        //when
        val result = userTypeMapper.fromTeamDomainAndPermission(
            "domain.wire.com",
            "teamA",
            "teamB",
            "domain.wire.com",
            )
        //then
        assertEquals(UserType.GUEST, result)
    }
}

