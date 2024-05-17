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
