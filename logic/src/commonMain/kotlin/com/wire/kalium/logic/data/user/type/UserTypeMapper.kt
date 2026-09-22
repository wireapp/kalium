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

package com.wire.kalium.logic.data.user.type

import com.wire.kalium.persistence.dao.UserTypeEntity

internal class DomainUserTypeMapperImpl : DomainUserTypeMapper {

    internal val guest: UserType
        get() = UserType.GUEST
    internal val federated: UserType
        get() = UserType.FEDERATED
    internal val external: UserType
        get() = UserType.EXTERNAL
    internal val standard: UserType
        get() = UserType.INTERNAL
    internal val admin: UserType
        get() = UserType.ADMIN
    internal val owner: UserType
        get() = UserType.OWNER
    internal val none: UserType
        get() = UserType.NONE

    override fun fromUserTypeEntity(userTypeEntity: UserTypeEntity?): UserTypeInfo {
        return when (userTypeEntity) {
            UserTypeEntity.STANDARD -> UserTypeInfo.Regular(standard)
            UserTypeEntity.EXTERNAL -> UserTypeInfo.Regular(external)
            UserTypeEntity.FEDERATED -> UserTypeInfo.Regular(federated)
            UserTypeEntity.GUEST -> UserTypeInfo.Regular(guest)
            UserTypeEntity.NONE -> UserTypeInfo.Regular(none)
            UserTypeEntity.OWNER -> UserTypeInfo.Regular(owner)
            UserTypeEntity.ADMIN -> UserTypeInfo.Regular(admin)
            UserTypeEntity.SERVICE -> UserTypeInfo.Bot
            UserTypeEntity.APP -> UserTypeInfo.App
            null -> UserTypeInfo.Regular(none)
        }
    }
}

internal interface DomainUserTypeMapper {
    fun fromUserTypeEntity(userTypeEntity: UserTypeEntity?): UserTypeInfo
}
