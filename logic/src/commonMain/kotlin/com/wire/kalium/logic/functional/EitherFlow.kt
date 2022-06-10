package com.wire.kalium.logic.functional

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map

fun <L, R> Flow<Either<L, R>>.onlyRight(): Flow<R> = filter { it.isRight() }.map { (it as Either.Right<R>).value }
