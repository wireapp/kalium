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
