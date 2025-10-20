/*
 * Wire
 * Copyright (C) 2024 Wire Swiss GmbH
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
import com.wire.kalium.logic.data.user.type.DomainUserTypeMapper
import com.wire.kalium.logic.data.user.type.DomainUserTypeMapperImpl
import com.wire.kalium.logic.data.user.type.UserType
import com.wire.kalium.network.api.model.UserTypeDTO
import kotlin.test.Test
import kotlin.test.assertEquals

class DomainUserTypeDTOMapperTest {

    private val userTypeMapper: DomainUserTypeMapper = DomainUserTypeMapperImpl()

    @Test
    fun givenTeamMemberWithAdminPermissions_whenMappingToConversationDetails_ThenConversationDetailsUserTypeIsAdmin() {
        // when
        val result = userTypeMapper.teamRoleCodeToUserType(TeamRole.Admin.value)
        // then
        assertEquals(UserType.ADMIN, result)
    }

    @Test
    fun givenTeamMemberWithOwnerPermissions_whenMappingToConversationDetails_ThenConversationDetailsUserTypeIsOwner() {
        // when
        val result = userTypeMapper.teamRoleCodeToUserType(TeamRole.Owner.value)
        // then
        assertEquals(UserType.OWNER, result)
    }

    @Test
    fun givenTeamMemberWithExternalPartnerPermissions_whenMappingToConversationDetails_ThenConversationDetailsUserTypeIsExternal() {
        // when
        val result = userTypeMapper.teamRoleCodeToUserType(TeamRole.ExternalPartner.value)
        // then
        assertEquals(UserType.EXTERNAL, result)
    }

    @Test
    fun givenTeamMemberWithMemberPermissions_whenMappingToConversationDetails_ThenConversationDetailsUserTypeIsInternal() {
        // when
        val result = userTypeMapper.teamRoleCodeToUserType(TeamRole.Member.value)
        // then
        assertEquals(UserType.INTERNAL, result)
    }

    @Test
    fun givenServiceTeamMember_whenMappingToConversationDetails_ThenConversationDetailsUserTypeIsService() {
        // when
        val result = userTypeMapper.teamRoleCodeToUserType(TeamRole.Member.value, true)
        // then
        assertEquals(UserType.SERVICE, result)
    }

    @Test
    fun givenApiTypeIsApp_whenMappingFromApiTypeAndTeamAndDomain_ThenUserTypeIsApp() {
        // when
        val result = userTypeMapper.fromApiTypeAndTeamAndDomain(
            apiUserTypeDTO = UserTypeDTO.APP,
            otherUserDomain = "domain.wire.com",
            selfUserTeamId = "teamA",
            otherUserTeamId = "teamB",
            selfUserDomain = "domain.wire.com",
            isLegacyBot = false
        )
        // then
        assertEquals(UserType.APP, result)
    }

    @Test
    fun givenApiTypeIsBot_whenMappingFromApiTypeAndTeamAndDomain_ThenUserTypeIsService() {
        // when
        val result = userTypeMapper.fromApiTypeAndTeamAndDomain(
            apiUserTypeDTO = UserTypeDTO.BOT,
            otherUserDomain = "domain.wire.com",
            selfUserTeamId = "teamA",
            otherUserTeamId = "teamB",
            selfUserDomain = "domain.wire.com",
            isLegacyBot = false
        )
        // then
        assertEquals(UserType.SERVICE, result)
    }

    @Test
    fun givenApiTypeIsRegularAndSameTeamAndDomain_whenMappingFromApiTypeAndTeamAndDomain_ThenUserTypeIsInternal() {
        // when
        val result = userTypeMapper.fromApiTypeAndTeamAndDomain(
            apiUserTypeDTO = UserTypeDTO.REGULAR,
            otherUserDomain = "domain.wire.com",
            selfUserTeamId = "teamA",
            otherUserTeamId = "teamA",
            selfUserDomain = "domain.wire.com",
            isLegacyBot = false
        )
        // then
        assertEquals(UserType.INTERNAL, result)
    }

    @Test
    fun givenApiTypeIsRegularAndDifferentDomains_whenMappingFromApiTypeAndTeamAndDomain_ThenUserTypeIsFederated() {
        // when
        val result = userTypeMapper.fromApiTypeAndTeamAndDomain(
            apiUserTypeDTO = UserTypeDTO.REGULAR,
            otherUserDomain = "domainB.wire.com",
            selfUserTeamId = "teamA",
            otherUserTeamId = "teamB",
            selfUserDomain = "domainA.wire.com",
            isLegacyBot = false
        )
        // then
        assertEquals(UserType.FEDERATED, result)
    }

    @Test
    fun givenApiTypeIsRegularAndSameDomainButDifferentTeam_whenMappingFromApiTypeAndTeamAndDomain_ThenUserTypeIsGuest() {
        // when
        val result = userTypeMapper.fromApiTypeAndTeamAndDomain(
            apiUserTypeDTO = UserTypeDTO.REGULAR,
            otherUserDomain = "domain.wire.com",
            selfUserTeamId = "teamA",
            otherUserTeamId = "teamB",
            selfUserDomain = "domain.wire.com",
            isLegacyBot = false
        )
        // then
        assertEquals(UserType.GUEST, result)
    }

    @Test
    fun givenApiTypeIsNullAndSameTeamAndDomain_whenMappingFromApiTypeAndTeamAndDomain_ThenUserTypeIsInternal() {
        // when
        val result = userTypeMapper.fromApiTypeAndTeamAndDomain(
            apiUserTypeDTO = null,
            otherUserDomain = "domain.wire.com",
            selfUserTeamId = "teamA",
            otherUserTeamId = "teamA",
            selfUserDomain = "domain.wire.com",
            isLegacyBot = false
        )
        // then
        assertEquals(UserType.INTERNAL, result)
    }

    @Test
    fun givenApiTypeIsNullAndDifferentDomains_whenMappingFromApiTypeAndTeamAndDomain_ThenUserTypeIsFederated() {
        // when
        val result = userTypeMapper.fromApiTypeAndTeamAndDomain(
            apiUserTypeDTO = null,
            otherUserDomain = "domainB.wire.com",
            selfUserTeamId = "teamA",
            otherUserTeamId = "teamB",
            selfUserDomain = "domainA.wire.com",
            isLegacyBot = false
        )
        // then
        assertEquals(UserType.FEDERATED, result)
    }

    @Test
    fun givenApiTypeIsRegularWithLegacyBot_whenMappingFromApiTypeAndTeamAndDomain_ThenUserTypeIsService() {
        // when
        val result = userTypeMapper.fromApiTypeAndTeamAndDomain(
            apiUserTypeDTO = UserTypeDTO.REGULAR,
            otherUserDomain = "domain.wire.com",
            selfUserTeamId = "teamA",
            otherUserTeamId = "teamB",
            selfUserDomain = "domain.wire.com",
            isLegacyBot = true
        )
        // then
        assertEquals(UserType.SERVICE, result)
    }

    @Test
    fun givenApiTypeIsAppWithLegacyBotFlag_whenMappingFromApiTypeAndTeamAndDomain_ThenUserTypeIsApp() {
        // The API type takes precedence over legacy bot flag
        // when
        val result = userTypeMapper.fromApiTypeAndTeamAndDomain(
            apiUserTypeDTO = UserTypeDTO.APP,
            otherUserDomain = "domain.wire.com",
            selfUserTeamId = "teamA",
            otherUserTeamId = "teamB",
            selfUserDomain = "domain.wire.com",
            isLegacyBot = true
        )
        // then
        assertEquals(UserType.APP, result)
    }

    @Test
    fun givenApiTypeIsNullAndSameDomainDifferentTeam_whenMappingFromApiTypeAndTeamAndDomain_ThenUserTypeIsGuest() {
        // Test null API type with guest scenario for backward compatibility
        // when
        val result = userTypeMapper.fromApiTypeAndTeamAndDomain(
            apiUserTypeDTO = null,
            otherUserDomain = "domain.wire.com",
            selfUserTeamId = "teamA",
            otherUserTeamId = "teamB",
            selfUserDomain = "domain.wire.com",
            isLegacyBot = false
        )
        // then
        assertEquals(UserType.GUEST, result)
    }
}

