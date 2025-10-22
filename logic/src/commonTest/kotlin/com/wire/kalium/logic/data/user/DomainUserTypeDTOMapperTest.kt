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

import com.wire.kalium.logic.data.user.type.DomainUserTypeMapper
import com.wire.kalium.logic.data.user.type.DomainUserTypeMapperImpl
import com.wire.kalium.logic.data.user.type.UserType
import com.wire.kalium.logic.data.user.type.UserTypeInfo
import kotlin.test.Test
import kotlin.test.assertEquals

class DomainUserTypeDTOMapperTest {

    private val domainUserTypeMapper: DomainUserTypeMapper = DomainUserTypeMapperImpl()

    @Test
    fun givenStandardUserTypeEntity_whenMapping_thenReturnsRegularInternal() {
        val result = domainUserTypeMapper.fromUserTypeEntity(com.wire.kalium.persistence.dao.UserTypeEntity.STANDARD)
        assertEquals(UserTypeInfo.Regular(UserType.INTERNAL), result)
    }

    @Test
    fun givenExternalUserTypeEntity_whenMapping_thenReturnsRegularExternal() {
        val result = domainUserTypeMapper.fromUserTypeEntity(com.wire.kalium.persistence.dao.UserTypeEntity.EXTERNAL)
        assertEquals(UserTypeInfo.Regular(UserType.EXTERNAL), result)
    }

    @Test
    fun givenFederatedUserTypeEntity_whenMapping_thenReturnsRegularFederated() {
        val result = domainUserTypeMapper.fromUserTypeEntity(com.wire.kalium.persistence.dao.UserTypeEntity.FEDERATED)
        assertEquals(UserTypeInfo.Regular(UserType.FEDERATED), result)
    }

    @Test
    fun givenGuestUserTypeEntity_whenMapping_thenReturnsRegularGuest() {
        val result = domainUserTypeMapper.fromUserTypeEntity(com.wire.kalium.persistence.dao.UserTypeEntity.GUEST)
        assertEquals(UserTypeInfo.Regular(UserType.GUEST), result)
    }

    @Test
    fun givenNoneUserTypeEntity_whenMapping_thenReturnsRegularNone() {
        val result = domainUserTypeMapper.fromUserTypeEntity(com.wire.kalium.persistence.dao.UserTypeEntity.NONE)
        assertEquals(UserTypeInfo.Regular(UserType.NONE), result)
    }

    @Test
    fun givenOwnerUserTypeEntity_whenMapping_thenReturnsRegularOwner() {
        val result = domainUserTypeMapper.fromUserTypeEntity(com.wire.kalium.persistence.dao.UserTypeEntity.OWNER)
        assertEquals(UserTypeInfo.Regular(UserType.OWNER), result)
    }

    @Test
    fun givenAdminUserTypeEntity_whenMapping_thenReturnsRegularAdmin() {
        val result = domainUserTypeMapper.fromUserTypeEntity(com.wire.kalium.persistence.dao.UserTypeEntity.ADMIN)
        assertEquals(UserTypeInfo.Regular(UserType.ADMIN), result)
    }

    @Test
    fun givenServiceUserTypeEntity_whenMapping_thenReturnsBot() {
        val result = domainUserTypeMapper.fromUserTypeEntity(com.wire.kalium.persistence.dao.UserTypeEntity.SERVICE)
        assertEquals(UserTypeInfo.Bot, result)
    }

    @Test
    fun givenAppUserTypeEntity_whenMapping_thenReturnsApp() {
        val result = domainUserTypeMapper.fromUserTypeEntity(com.wire.kalium.persistence.dao.UserTypeEntity.APP)
        assertEquals(UserTypeInfo.App, result)
    }

    @Test
    fun givenNullUserTypeEntity_whenMapping_thenReturnsRegularNone() {
        val result = domainUserTypeMapper.fromUserTypeEntity(null)
        assertEquals(UserTypeInfo.Regular(UserType.NONE), result)
    }
}

