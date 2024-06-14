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

package com.wire.kalium.logic.test_util

import com.wire.kalium.util.KaliumDispatcher
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher

object TestKaliumDispatcher : KaliumDispatcher {

    private val testDispatcher = StandardTestDispatcher()

    override val default: TestDispatcher
        get() = testDispatcher

    override val main: TestDispatcher
        get() = testDispatcher

    override val unconfined: TestDispatcher
        get() = testDispatcher

    override val io: TestDispatcher
        get() = testDispatcher

}

@OptIn(ExperimentalStdlibApi::class)
val CoroutineScope.testKaliumDispatcher: KaliumDispatcher
    get() = object : KaliumDispatcher {
        override val default: CoroutineDispatcher
            get() = coroutineContext[CoroutineDispatcher]!!
        override val main: CoroutineDispatcher
            get() = coroutineContext[CoroutineDispatcher]!!
        override val unconfined: CoroutineDispatcher
            get() = coroutineContext[CoroutineDispatcher]!!
        override val io: CoroutineDispatcher
            get() = coroutineContext[CoroutineDispatcher]!!
    }
