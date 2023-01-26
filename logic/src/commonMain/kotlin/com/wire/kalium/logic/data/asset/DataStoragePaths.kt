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

package com.wire.kalium.logic.data.asset

import kotlin.jvm.JvmInline

@JvmInline
value class CacheFolder(val value: String)

@JvmInline
value class AssetsStorageFolder(val value: String)

@JvmInline
value class DBFolder(val value: String)

data class DataStoragePaths(val assetStoragePath: AssetsStorageFolder, val cachePath: CacheFolder, val dbPath: DBFolder)
