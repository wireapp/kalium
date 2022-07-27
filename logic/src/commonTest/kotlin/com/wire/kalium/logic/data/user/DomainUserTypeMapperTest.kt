package com.wire.kalium.logic.data.user

import com.wire.kalium.logic.data.team.TeamRole
import com.wire.kalium.logic.data.user.type.DomainUserTypeMapper
import com.wire.kalium.logic.data.user.type.DomainUserTypeMapperImpl
import com.wire.kalium.logic.data.user.type.UserType
import kotlin.test.Test
import kotlin.test.assertEquals

class DomainUserTypeMapperTest {

    private val userTypeMapper: DomainUserTypeMapper = DomainUserTypeMapperImpl()

    @Test
    fun givenDomainAndTeamAreEqualAndPermissionCodeIsNull_whenMappingToConversationDetails_ThenConversationDetailsUserTypeIsInternal() {
        // when
        val result = userTypeMapper.fromTeamDomainAndPermission(
            "someDomain",
            "someTeamId",
            "someTeamId",
            "someDomain",
            null,
            false
        )
        // then
        assertEquals(UserType.INTERNAL, result)
    }

    @Test
    fun givenTeamMemberWithAdminPermissions_whenMappingToConversationDetails_ThenConversationDetailsUserTypeIsAdmin() {
        // when
        val result = userTypeMapper.fromTeamDomainAndPermission(
            "someDomain",
            "someTeamId",
            "someTeamId",
            "someDomain",
            TeamRole.Admin.value,
            false
        )
        // then
        assertEquals(UserType.ADMIN, result)
    }

    @Test
    fun givenTeamMemberWithOwnerPermissions_whenMappingToConversationDetails_ThenConversationDetailsUserTypeIsOwner() {
        // when
        val result = userTypeMapper.fromTeamDomainAndPermission(
            "someDomain",
            "someTeamId",
            "someTeamId",
            "someDomain",
            TeamRole.Owner.value,
            false
        )
        // then
        assertEquals(UserType.OWNER, result)
    }

    @Test
    fun givenTeamMemberWithExternalPartnerPermissions_whenMappingToConversationDetails_ThenConversationDetailsUserTypeIsExternal() {
        // when
        val result = userTypeMapper.fromTeamDomainAndPermission(
            "someDomain",
            "someTeamId",
            "someTeamId",
            "someDomain",
            TeamRole.ExternalPartner.value,
            false
        )
        // then
        assertEquals(UserType.EXTERNAL, result)
    }

    @Test
    fun givenTeamMemberWithMemberPermissions_whenMappingToConversationDetails_ThenConversationDetailsUserTypeIsInternal() {
        // when
        val result = userTypeMapper.fromTeamDomainAndPermission(
            "someDomain",
            "someTeamId",
            "someTeamId",
            "someDomain",
            TeamRole.Member.value,
            false
        )
        // then
        assertEquals(UserType.INTERNAL, result)
    }

    @Test
    fun givenCommonNotWireDomainAndDifferentTeam_whenMappingToConversationDetails_ThenConversationDetailsUserTypeIsFederated() {
        // given
        val result = userTypeMapper.fromTeamDomainAndPermission(
            "domainB",
            "teamA",
            "teamB",
            "domainA",
            null,
            false
        )
        // then
        assertEquals(UserType.FEDERATED, result)
    }

    @Test
    fun givenUsingSameDomainAndDifferentTeam_whenMappingToConversationDetails_ThenConversationDetailsUserTypeIsGuest() {
        // when
        val result = userTypeMapper.fromTeamDomainAndPermission(
            "domain.wire.com",
            "teamA",
            "teamB",
            "domain.wire.com",
            null,
            false
        )
        // then
        assertEquals(UserType.GUEST, result)
    }

    @Test
    fun givenServiceBot_whenMappingToConversationDetails_ThenConversationDetailsUserTypeIsService() {
        // when
        val result = userTypeMapper.fromTeamDomainAndPermission(
            "domain.wire.com",
            "teamA",
            "teamB",
            "domain.wire.com",
            null,
            true
        )
        // then
        assertEquals(UserType.SERVICE, result)
    }
}
