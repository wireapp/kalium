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
package com.wire.kalium.logic.feature.featureConfig.handler

import com.wire.kalium.common.error.StorageFailure
import com.wire.kalium.common.functional.Either
import com.wire.kalium.common.functional.isLeft
import com.wire.kalium.common.functional.isRight
import com.wire.kalium.logic.configuration.UserConfigRepository
import com.wire.kalium.logic.data.featureConfig.ConferenceCallingModel
import com.wire.kalium.logic.data.featureConfig.Status
import io.mockative.any
import io.mockative.coEvery
import io.mockative.coVerify
import io.mockative.mock
import io.mockative.once
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertTrue

class ConferenceCallingConfigHandlerTest {

    @Test
    fun givenUserConfigRepositoryFailureForConferenceCallingEnabled_whenHandlingTheEvent_ThenReturnFailure() = runTest {
        val conferenceCallingModel = ConferenceCallingModel(Status.ENABLED, false)
        val (arrangement, conferenceCallingConfigHandler) = Arrangement()
            .withSetConferenceCallingEnabledFailure()
            .arrange()

        val result = conferenceCallingConfigHandler.handle(conferenceCallingModel)

        coVerify {
            arrangement.userConfigRepository.setConferenceCallingEnabled(conferenceCallingModel.status.toBoolean())
        }.wasInvoked(exactly = once)

        coVerify {
            arrangement.userConfigRepository.setUseSFTForOneOnOneCalls(any())
        }.wasNotInvoked()

        assertTrue { result.isLeft() }
    }

    @Test
    fun givenUserConfigRepositoryFailureForUseSFTForOneOnOneCalls_whenHandlingTheEvent_ThenReturnFailure() = runTest {
        val conferenceCallingModel = ConferenceCallingModel(Status.ENABLED, false)
        val (arrangement, conferenceCallingConfigHandler) = Arrangement()
            .withSetConferenceCallingEnabledSuccess()
            .withSetUseSFTForOneOnOneCallsFailure()
            .arrange()

        val result = conferenceCallingConfigHandler.handle(conferenceCallingModel)

        coVerify {
            arrangement.userConfigRepository.setConferenceCallingEnabled(conferenceCallingModel.status.toBoolean())
        }.wasInvoked(exactly = once)

        coVerify {
            arrangement.userConfigRepository.setUseSFTForOneOnOneCalls(any())
        }.wasInvoked(exactly = once)

        assertTrue { result.isLeft() }
    }

    @Test
    fun givenUserConfigRepositorySuccess_whenHandlingTheEvent_ThenReturnUnit() = runTest {
        val conferenceCallingModel = ConferenceCallingModel(Status.ENABLED, false)
        val (arrangement, conferenceCallingConfigHandler) = Arrangement()
            .withSetConferenceCallingEnabledSuccess()
            .withSetUseSFTForOneOnOneCallsSuccess()
            .arrange()

        val result = conferenceCallingConfigHandler.handle(conferenceCallingModel)

        coVerify {
            arrangement.userConfigRepository.setConferenceCallingEnabled(conferenceCallingModel.status.toBoolean())
        }.wasInvoked(exactly = once)

        coVerify {
            arrangement.userConfigRepository.setUseSFTForOneOnOneCalls(any())
        }.wasInvoked(exactly = once)

        assertTrue { result.isRight() }
    }

    private class Arrangement {

        val userConfigRepository: UserConfigRepository = mock(UserConfigRepository::class)

        fun arrange() = run {
            this@Arrangement to ConferenceCallingConfigHandler(
                userConfigRepository = userConfigRepository
            )
        }

        init {
            runBlocking {
                coEvery {
                    userConfigRepository.setAppLockStatus(any(), any(), any())
                }.returns(Either.Right(Unit))
            }

        }

        suspend fun withSetConferenceCallingEnabledFailure() = apply {
            coEvery {
                userConfigRepository.setConferenceCallingEnabled(any())
            }.returns(Either.Left(StorageFailure.DataNotFound))
        }

        suspend fun withSetConferenceCallingEnabledSuccess() = apply {
            coEvery {
                userConfigRepository.setConferenceCallingEnabled(any())
            }.returns(Either.Right(Unit))
        }

        suspend fun withSetUseSFTForOneOnOneCallsFailure() = apply {
            coEvery {
                userConfigRepository.setUseSFTForOneOnOneCalls(any())
            }.returns(Either.Left(StorageFailure.DataNotFound))
        }

        suspend fun withSetUseSFTForOneOnOneCallsSuccess() = apply {
            coEvery {
                userConfigRepository.setUseSFTForOneOnOneCalls(any())
            }.returns(Either.Right(Unit))
        }
    }
}
