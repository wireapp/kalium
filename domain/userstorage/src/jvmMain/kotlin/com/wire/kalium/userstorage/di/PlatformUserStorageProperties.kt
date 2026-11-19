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

package com.wire.kalium.userstorage.di

import com.wire.kalium.persistence.db.UserDBSecret
import java.io.File

public actual class PlatformUserStorageProperties(
    public val rootPath: String,
    internal val databaseInfo: DatabaseStorageType,
    internal val secret: UserDBSecret? = null
) {
    public val protectedAccountDirectory: String?
        get() = if (secret != null) (databaseInfo as DatabaseStorageType.FiledBacked).filePath.path else null
}

public sealed interface DatabaseStorageType {
    public data class FiledBacked(val filePath: File) : DatabaseStorageType

    public data object InMemory : DatabaseStorageType
}
