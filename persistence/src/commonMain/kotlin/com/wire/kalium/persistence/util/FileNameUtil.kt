package com.wire.kalium.persistence.util

object FileNameUtil {
    fun appPrefFile() = SHARED_PREFERENCE_FILE_NAME

    fun userPrefFile(userId: String) =
        "${USER_PREFERENCE_FILE_PREFIX}-${userId.let { id -> id.filter { (it.isLetterOrDigit() or (it == '-')) } }}"

    fun userDBName(userId: String) = "${USER_DB_PREFIX}-${userId.let { id -> id.filter { (it.isLetterOrDigit() or (it == '-')) } }}"

    private const val USER_PREFERENCE_FILE_PREFIX = "user-pref"
    private const val USER_DB_PREFIX = "user-db"
    private const val SHARED_PREFERENCE_FILE_NAME = "app-preference"
}
