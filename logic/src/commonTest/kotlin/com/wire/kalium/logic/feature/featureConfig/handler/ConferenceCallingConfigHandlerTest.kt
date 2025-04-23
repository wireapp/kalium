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
import com.wire.kalium.logic.configuration.UserConfigRepository
import com.wire.kalium.logic.data.featureConfig.ConferenceCallingModel
import com.wire.kalium.logic.data.featureConfig.Status
import com.wire.kalium.common.functional.Either
import com.wire.kalium.common.functional.isLeft
import com.wire.kalium.common.functional.isRight
import io.mockative.any
import io.mockative.every
import io.mockative.mock
import io.mockative.once
import io.mockative.verify
import kotlin.test.Test
import kotlin.test.assertTrue

class ConferenceCallingConfigHandlerTest {

    @Test
    fun givenUserConfigRepositoryFailureForConferenceCallingEnabled_whenHandlingTheEvent_ThenReturnFailure() {
        val conferenceCallingModel = ConferenceCallingModel(Status.ENABLED, false)
        val (arrangement, conferenceCallingConfigHandler) = Arrangement()
            .withSetConferenceCallingEnabledFailure()
            .arrange()

        val result = conferenceCallingConfigHandler.handle(conferenceCallingModel)

        verify {
            arrangement.userConfigRepository.setConferenceCallingEnabled(conferenceCallingModel.status.toBoolean())
        }.wasInvoked(exactly = once)

        verify {
            arrangement.userConfigRepository.setUseSFTForOneOnOneCalls(any())
        }.wasNotInvoked()

        assertTrue { result.isLeft() }
    }

    @Test
    fun givenUserConfigRepositoryFailureForUseSFTForOneOnOneCalls_whenHandlingTheEvent_ThenReturnFailure() {
        val conferenceCallingModel = ConferenceCallingModel(Status.ENABLED, false)
        val (arrangement, conferenceCallingConfigHandler) = Arrangement()
            .withSetConferenceCallingEnabledSuccess()
            .withSetUseSFTForOneOnOneCallsFailure()
            .arrange()

        val result = conferenceCallingConfigHandler.handle(conferenceCallingModel)

        verify {
            arrangement.userConfigRepository.setConferenceCallingEnabled(conferenceCallingModel.status.toBoolean())
        }.wasInvoked(exactly = once)

        verify {
            arrangement.userConfigRepository.setUseSFTForOneOnOneCalls(any())
        }.wasInvoked(exactly = once)

        assertTrue { result.isLeft() }
    }

    @Test
    fun givenUserConfigRepositorySuccess_whenHandlingTheEvent_ThenReturnUnit() {
        val conferenceCallingModel = ConferenceCallingModel(Status.ENABLED, false)
        val (arrangement, conferenceCallingConfigHandler) = Arrangement()
            .withSetConferenceCallingEnabledSuccess()
            .withSetUseSFTForOneOnOneCallsSuccess()
            .arrange()

        val result = conferenceCallingConfigHandler.handle(conferenceCallingModel)

        verify {
            arrangement.userConfigRepository.setConferenceCallingEnabled(conferenceCallingModel.status.toBoolean())
        }.wasInvoked(exactly = once)

        verify {
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
            every {
                userConfigRepository.setAppLockStatus(any(), any(), any())
            }.returns(Either.Right(Unit))
        }

        fun withSetConferenceCallingEnabledFailure() = apply {
            every {
                userConfigRepository.setConferenceCallingEnabled(any())
            }.returns(Either.Left(StorageFailure.DataNotFound))
        }

        fun withSetConferenceCallingEnabledSuccess() = apply {
            every {
                userConfigRepository.setConferenceCallingEnabled(any())
            }.returns(Either.Right(Unit))
        }

        fun withSetUseSFTForOneOnOneCallsFailure() = apply {
            every {
                userConfigRepository.setUseSFTForOneOnOneCalls(any())
            }.returns(Either.Left(StorageFailure.DataNotFound))
        }

        fun withSetUseSFTForOneOnOneCallsSuccess() = apply {
            every {
                userConfigRepository.setUseSFTForOneOnOneCalls(any())
            }.returns(Either.Right(Unit))
        }
    }
}
