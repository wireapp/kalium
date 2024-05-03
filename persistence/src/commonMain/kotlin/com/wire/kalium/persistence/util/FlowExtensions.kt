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

import app.cash.sqldelight.Query
import app.cash.sqldelight.coroutines.mapToList
import app.cash.sqldelight.coroutines.mapToOne
import app.cash.sqldelight.coroutines.mapToOneNotNull
import app.cash.sqldelight.coroutines.mapToOneOrDefault
import app.cash.sqldelight.coroutines.mapToOneOrNull
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow

// TODO(refactor): Remove these and force DAOs to use another Dispatcher
//                 This is added to keep compatibility in

fun <T : Any> Flow<Query<T>>.mapToOne(): Flow<T> = mapToOne(Dispatchers.Default)

fun <T : Any> Flow<Query<T>>.mapToOneOrDefault(
    defaultValue: T,
): Flow<T> = mapToOneOrDefault(defaultValue, Dispatchers.Default)

fun <T : Any> Flow<Query<T>>.mapToOneOrNull(): Flow<T?> = mapToOneOrNull(Dispatchers.Default)

fun <T : Any> Flow<Query<T>>.mapToOneNotNull(): Flow<T> = mapToOneNotNull(Dispatchers.Default)

fun <T : Any> Flow<Query<T>>.mapToList(): Flow<List<T>> = mapToList(Dispatchers.Default)
