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
import com.wire.kalium.logic.data.user.type.UserEntityTypeMapper
import com.wire.kalium.logic.data.user.type.UserEntityTypeMapperImpl
import com.wire.kalium.logic.data.user.type.UserType
import com.wire.kalium.logic.data.user.type.UserTypeInfo
import com.wire.kalium.persistence.dao.UserTypeEntity
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class UserEntityTypeMapperTest {

    private val userTypeMapper: UserEntityTypeMapper = UserEntityTypeMapperImpl()

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
    fun givenServiceTeamMember_whenMappingToConversationDetails_ThenConversationDetailsUserTypeIsService() {
        // when
        val result = userTypeMapper.teamRoleCodeToUserType(TeamRole.Member.value, true)
        // then
        assertEquals(UserTypeEntity.SERVICE, result)
    }

    @Test
    fun givenUserTypeInfoIsInternal_whenMappingToUserTypeInfoEntity_thenStandardIsReturned() {
        // when
        val result = userTypeMapper.fromUserTypeInfo(UserTypeInfo.Regular(UserType.INTERNAL))
        // then
        assertEquals(UserTypeEntity.STANDARD, result)
    }

    @Test
    fun givenUserTypeInfoIsAdmin_whenMappingToUserTypeInfoEntity_thenAdminIsReturned() {
        // when
        val result = userTypeMapper.fromUserTypeInfo(UserTypeInfo.Regular(UserType.ADMIN))
        // then
        assertEquals(UserTypeEntity.ADMIN, result)
    }

    @Test
    fun givenUserTypeInfoIsOwner_whenMappingToUserTypeInfoEntity_thenOwnerIsReturned() {
        // when
        val result = userTypeMapper.fromUserTypeInfo(UserTypeInfo.Regular(UserType.OWNER))
        // then
        assertEquals(UserTypeEntity.OWNER, result)
    }

    @Test
    fun givenUserTypeInfoIsExternal_whenMappingToUserTypeInfoEntity_thenExternalIsReturned() {
        // when
        val result = userTypeMapper.fromUserTypeInfo(UserTypeInfo.Regular(UserType.EXTERNAL))
        // then
        assertEquals(UserTypeEntity.EXTERNAL, result)
    }

    @Test
    fun givenUserTypeInfoIsFederated_whenMappingToUserTypeInfoEntity_thenFederatedIsReturned() {
        // when
        val result = userTypeMapper.fromUserTypeInfo(UserTypeInfo.Regular(UserType.FEDERATED))
        // then
        assertEquals(UserTypeEntity.FEDERATED, result)
    }

    @Test
    fun givenUserTypeInfoIsGuest_whenMappingToUserTypeInfoEntity_thenGuestIsReturned() {
        // when
        val result = userTypeMapper.fromUserTypeInfo(UserTypeInfo.Regular(UserType.GUEST))
        // then
        assertEquals(UserTypeEntity.GUEST, result)
    }

    @Test
    fun givenUserTypeInfoIsNone_whenMappingToUserTypeInfoEntity_thenNoneIsReturned() {
        // when
        val result = userTypeMapper.fromUserTypeInfo(UserTypeInfo.Regular(UserType.NONE))
        // then
        assertEquals(UserTypeEntity.NONE, result)
    }

    @Test
    fun givenUserTypeInfoIsService_whenMappingToUserTypeInfoEntity_thenBotIsReturned() {
        // when
        val result = userTypeMapper.fromUserTypeInfo(UserTypeInfo.Bot)
        // then
        assertEquals(UserTypeEntity.SERVICE, result)
    }

    @Test
    fun givenUserTypeInfoIsApp_whenMappingToUserTypeInfoEntity_thenAppIsReturned() {
        // when
        val result = userTypeMapper.fromUserTypeInfo(UserTypeInfo.App)
        // then
        assertEquals(UserTypeEntity.APP, result)
    }

}
