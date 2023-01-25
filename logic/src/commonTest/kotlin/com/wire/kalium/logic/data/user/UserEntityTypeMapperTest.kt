/*
 * Wire
 * Copyright (C) 2023 Wire Swiss GmbH
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see http://www.gnu.org/licenses/.
 */

package com.wire.kalium.logic.data.user

import com.wire.kalium.logic.data.team.TeamRole
import com.wire.kalium.logic.data.user.type.UserEntityTypeMapper
import com.wire.kalium.logic.data.user.type.UserEntityTypeMapperImpl
import com.wire.kalium.persistence.dao.UserTypeEntity
import kotlin.test.Test
import kotlin.test.assertEquals

class UserEntityTypeMapperTest {

    private val userTypeMapper: UserEntityTypeMapper = UserEntityTypeMapperImpl()

    @Test
    fun givenDomainAndTeamAreEqualAndPermissionCodeIsNull_whenMappingToConversationDetails_ThenConversationDetailsUserTypeIsInternal() {
        // when
        val result = userTypeMapper.fromTeamAndDomain(
            "someDomain",
            "someTeamId",
            "someTeamId",
            "someDomain",
            false
        )
        // then
        assertEquals(UserTypeEntity.STANDARD, result)
    }

    @Test
    fun givenTeamMemberWithAdminPermissions_whenMappingToConversationDetails_ThenConversationDetailsUserTypeIsAdmin() {
        // when
        val result = userTypeMapper.teamRoleCodeToUserType(TeamRole.Admin.value)
        // then
        assertEquals(UserTypeEntity.ADMIN, result)
    }

    @Test
    fun givenTeamMemberWithOwnerPermissions_whenMappingToConversationDetails_ThenConversationDetailsUserTypeIsOwner() {
        // when
        val result = userTypeMapper.teamRoleCodeToUserType(TeamRole.Owner.value)
        // then
        assertEquals(UserTypeEntity.OWNER, result)
    }

    @Test
    fun givenTeamMemberWithExternalPartnerPermissions_whenMappingToConversationDetails_ThenConversationDetailsUserTypeIsExternal() {
        // when
        val result = userTypeMapper.teamRoleCodeToUserType(TeamRole.ExternalPartner.value)
        // then
        assertEquals(UserTypeEntity.EXTERNAL, result)
    }

    @Test
    fun givenTeamMemberWithMemberPermissions_whenMappingToConversationDetails_ThenConversationDetailsUserTypeIsInternal() {
        // when
        val result = userTypeMapper.teamRoleCodeToUserType(TeamRole.Member.value)
        // then
        assertEquals(UserTypeEntity.STANDARD, result)
    }

    @Test
    fun givenCommonNotTheSameDomainAndDifferentTeam_whenMappingToConversationDetails_ThenConversationDetailsUserTypeIsFederated() {
        // given
        val result = userTypeMapper.fromTeamAndDomain(
            "domainB",
            "teamA",
            "teamB",
            "domainA",
            false
        )
        // then
        assertEquals(UserTypeEntity.FEDERATED, result)
    }

    @Test
    fun givenUsingSameDomainAndDifferentTeam_whenMappingToConversationDetails_ThenConversationDetailsUserTypeIsGuest() {
        // when
        val result = userTypeMapper.fromTeamAndDomain(
            "testDomain",
            "teamA",
            "teamB",
            "testDomain",
            false
        )
        // then
        assertEquals(UserTypeEntity.GUEST, result)
    }

    @Test
    fun givenServiceBot_whenMappingToConversationDetails_ThenConversationDetailsUserTypeIsService() {
        // when
        val result = userTypeMapper.fromTeamAndDomain(
            "domain.wire.com",
            "teamA",
            "teamB",
            "domain.wire.com",
            true
        )
        // then
        assertEquals(UserTypeEntity.SERVICE, result)
    }
}
