/*
 * Wire
 * Copyright (C) 2026 Wire Swiss GmbH
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
package com.wire.kalium.persistence.config

import com.wire.kalium.persistence.dao.UserIDEntity

/**
 * Factory for creating [UserConfigStorage] instances.
 * This is used during migration from SharedPreferences to database storage.
 * Creating the storage only when needed avoids creating SharedPreferences
 * when migration is not required.
 */
@Deprecated(
    "Scheduled for removal in future versions, User KMM Settings are now replaced by database implementation." +
            "Just kept for migration purposes.",
    ReplaceWith("No replacement available"),
)
expect class UserConfigStorageFactory() {
    fun create(
        userId: UserIDEntity,
        shouldEncryptData: Boolean,
        platformParam: Any
    ): UserConfigStorage
}
