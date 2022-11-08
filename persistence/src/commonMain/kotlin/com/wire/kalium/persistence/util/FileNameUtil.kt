package com.wire.kalium.persistence.util

import com.wire.kalium.persistence.dao.UserIDEntity

object FileNameUtil {
    fun appPrefFile() = SHARED_PREFERENCE_FILE_NAME
    fun globalDBName() = GLOBAL_DB_NAME

    fun userPrefFile(userId: UserIDEntity) = "$USER_PREFERENCE_FILE_PREFIX-${userId.value}-${userId.domain}".filterFileName()

    fun userDBName(userId: UserIDEntity) = "$USER_DB_PREFIX-${userId.value}-${userId.domain}".filterFileName()

    private const val GLOBAL_DB_NAME = "global-db"
    private const val USER_PREFERENCE_FILE_PREFIX = "user-pref"
    private const val USER_DB_PREFIX = "user-db"
    private const val SHARED_PREFERENCE_FILE_NAME = "app-preference"
}

private fun String.filterFileName(): String = this.filter { it.isLetterOrDigit() or (it == '-') }
