package com.wire.kalium.logic.data.user

import com.wire.kalium.logic.data.user.type.UserEntityTypeMapper
import com.wire.kalium.logic.data.user.type.UserEntityTypeMapperImpl
import com.wire.kalium.persistence.dao.UserTypeEntity
import kotlin.test.Test
import kotlin.test.assertEquals

class UserEntityTypeMapperTest {

    private val userTypeMapper : UserEntityTypeMapper = UserEntityTypeMapperImpl()

    @Test
    fun givenDomainAndTeamAreEqual_whenMappingToConversationDetails_ThenConversationDetailsUserTypeIsInternal() {
        //when
        val result = userTypeMapper.fromOtherUserTeamAndDomain(
            "someDomain",
            "someTeamId",
            "someTeamId",
            "someDomain"
            )
        //then
        assertEquals(UserTypeEntity.INTERNAL, result)
    }

    @Test
    fun givenCommonNotTheSameDomainAndDifferentTeam_whenMappingToConversationDetails_ThenConversationDetailsUserTypeIsFederated() {
        //given
        val result = userTypeMapper.fromOtherUserTeamAndDomain(
            "domainB",
            "teamA",
            "teamB",
            "domainA"
            )
        //then
        assertEquals(UserTypeEntity.FEDERATED, result)
    }

    @Test
    fun givenUsingSameDomainAndDifferentTeam_whenMappingToConversationDetails_ThenConversationDetailsUserTypeIsGuest() {
        //when
        val result = userTypeMapper.fromOtherUserTeamAndDomain(
            "testDomain",
            "teamA",
            "teamB",
            "testDomain"
            )
        //then
        assertEquals(UserTypeEntity.GUEST, result)
    }
}

