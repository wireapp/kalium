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

package com.wire.kalium.logic.feature.server

import com.wire.kalium.logic.configuration.server.ServerConfigRepository
import com.wire.kalium.logic.util.stubs.newServerConfig
import dev.mokkery.answering.returns
import dev.mokkery.every
import dev.mokkery.mock
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class ObserveApiVersionChangeUseCaseTest {
    @Test
    fun detectsInitialMismatchAndSubsequentVersionChanges() = runTest {
        val config = newServerConfig(1)
        val version = config.metaData.commonApiVersion.version
        val repository = mock<ServerConfigRepository>()
        every { repository.observeCommonApiVersion(config.links) } returns
            flowOf(version + 1, version, null, version + 2)

        val result = ObserveApiVersionChangeUseCaseImpl(repository, config)().toList()

        assertEquals(listOf(true, false, false, true), result)
    }
}
