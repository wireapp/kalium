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

package com.wire.kalium.persistence.adapter

import app.cash.sqldelight.ColumnAdapter
import com.wire.kalium.persistence.dao.Member

internal class MemberRoleAdapter : ColumnAdapter<Member.Role, String> {
    override fun decode(databaseValue: String): Member.Role = when (databaseValue) {
        ADMIN -> Member.Role.Admin
        MEMBER -> Member.Role.Member
        else -> Member.Role.Unknown(databaseValue)
    }

    override fun encode(value: Member.Role): String = when (value) {
        Member.Role.Admin -> ADMIN
        Member.Role.Member -> MEMBER
        is Member.Role.Unknown -> value.name
    }

    private companion object {
        const val ADMIN = "wire_admin"
        const val MEMBER = "wire_member"
    }
}
