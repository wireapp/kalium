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

package com.wire.kalium.logic.sync.periodic

import com.wire.kalium.logic.feature.server.UpdateApiVersionsUseCase
import com.wire.kalium.logic.sync.Result
import io.mockative.Mock
import io.mockative.classOf
import io.mockative.coEvery
import io.mockative.mock
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
class UpdateApiVersionsWorkerTest {

    @Mock
    private val updateApiVersionsUseCase = mock(classOf<UpdateApiVersionsUseCase>())

    private lateinit var updateApiVersionsWorker: UpdateApiVersionsWorker

    @BeforeTest
    fun setup() {
        updateApiVersionsWorker = UpdateApiVersionsWorker(updateApiVersionsUseCase)
    }

    @Test
    fun givenUpdateCompletes_whenExecutingAWorker_thenReturnSuccess() = runTest {
        coEvery {
            updateApiVersionsUseCase.invoke()
        }.returns(Unit)

        val result = updateApiVersionsWorker.doWork()

        assertEquals(result, Result.Success)
    }
}
