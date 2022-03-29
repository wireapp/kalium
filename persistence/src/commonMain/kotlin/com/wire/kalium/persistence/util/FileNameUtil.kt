package com.wire.kalium.persistence.util

import com.wire.kalium.persistence.dao.UserIDEntity

object FileNameUtil {
    fun appPrefFile() = SHARED_PREFERENCE_FILE_NAME

    fun userPrefFile(userId: UserIDEntity) = "$USER_PREFERENCE_FILE_PREFIX-${userId.value}@${userId.domain}".filter { RESERVED_CHARS.contains(it) }

    fun userDBName(userId: UserIDEntity) = "${USER_DB_PREFIX}-${userId.value}@${userId.domain}".filter { RESERVED_CHARS.contains(it) }

    private const val USER_PREFERENCE_FILE_PREFIX = "user-pref"
    private const val USER_DB_PREFIX = "user-db"
    private const val SHARED_PREFERENCE_FILE_NAME = "app-preference"
    private const val RESERVED_CHARS = """|\?*<":>+[]/'"""
}
