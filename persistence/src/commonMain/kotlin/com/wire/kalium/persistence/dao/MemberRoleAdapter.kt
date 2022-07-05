package com.wire.kalium.persistence.dao

import app.cash.sqldelight.ColumnAdapter

class MemberRoleAdapter : ColumnAdapter<Member.Role, String> {
    override fun decode(databaseValue: String): Member.Role = when (databaseValue) {
        ADMIN -> Member.Role.Admin
        MEMBER -> Member.Role.Member
        else -> Member.Role.Unknown(databaseValue)
    }

    override fun encode(value: Member.Role): String = when(value) {
        Member.Role.Admin -> ADMIN
        Member.Role.Member -> MEMBER
        is Member.Role.Unknown -> value.name
    }

    private companion object {
        const val ADMIN = "wire_admin"
        const val MEMBER = "wire_member"
    }
}
