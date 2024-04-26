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
package com.wire.kalium.logic.feature.asset

import com.wire.kalium.logic.feature.asset.GetAssetSizeLimitUseCaseImpl.Companion.ASSET_SIZE_DEFAULT_LIMIT_BYTES
import com.wire.kalium.logic.feature.asset.GetAssetSizeLimitUseCaseImpl.Companion.ASSET_SIZE_TEAM_USER_LIMIT_BYTES
import com.wire.kalium.logic.feature.asset.GetAssetSizeLimitUseCaseImpl.Companion.IMAGE_SIZE_LIMIT_BYTES
import com.wire.kalium.logic.feature.user.IsSelfATeamMemberUseCase
import com.wire.kalium.logic.test_util.TestKaliumDispatcher
import io.mockative.Mock
import io.mockative.coEvery
import io.mockative.coVerify
import io.mockative.mock
import io.mockative.once
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
class GetAssetSizeLimitUseCaseTest {

    @BeforeTest
    fun before() {
        Dispatchers.setMain(dispatcher.default)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun givenAnImageAsset_whenGettingTheSizeLimit_thenTheSizeLimitIsCorrect() = runTest(dispatcher.default) {
        val isImage = true
        val hasUserTeam = true
        val (arrangement, getAssetSizeLimit) = Arrangement()
            .withIsSelfATeamMember(hasUserTeam)
            .arrange()

        val assetLimit = getAssetSizeLimit(isImage)
        advanceUntilIdle()

        assertEquals(assetLimit, IMAGE_SIZE_LIMIT_BYTES)
        coVerify {
            arrangement.isSelfATeamMember.invoke()
        }.wasInvoked(exactly = once)
    }

    @Test
    fun givenAGenericAssetFromANonTeamUser_whenGettingTheSizeLimit_thenTheSizeLimitIsCorrect() = runTest(dispatcher.default) {
        val isImage = false
        val hasUserTeam = false
        val (arrangement, getAssetSizeLimit) = Arrangement()
            .withIsSelfATeamMember(hasUserTeam)
            .arrange()

        val assetLimit = getAssetSizeLimit(isImage)
        advanceUntilIdle()

        assertEquals(assetLimit, ASSET_SIZE_DEFAULT_LIMIT_BYTES)
        coVerify {
            arrangement.isSelfATeamMember.invoke()
        }.wasInvoked(exactly = once)
    }

    @Test
    fun givenAGenericAssetFromATeamUser_whenGettingTheSizeLimit_thenTheSizeLimitIsCorrect() = runTest(dispatcher.default) {
        val isImage = false
        val hasUserTeam = true
        val (arrangement, getAssetSizeLimit) = Arrangement()
            .withIsSelfATeamMember(hasUserTeam)
            .arrange()

        val assetLimit = getAssetSizeLimit(isImage)
        advanceUntilIdle()

        assertEquals(assetLimit, ASSET_SIZE_TEAM_USER_LIMIT_BYTES)
        coVerify {
            arrangement.isSelfATeamMember.invoke()
        }.wasInvoked(exactly = once)
    }

    private class Arrangement {

        @Mock
        val isSelfATeamMember = mock(IsSelfATeamMemberUseCase::class)

        suspend fun withIsSelfATeamMember(hasUserTeam: Boolean) = apply {
            coEvery {
                isSelfATeamMember.invoke()
            }.returns(hasUserTeam)
        }

        fun arrange() = this to GetAssetSizeLimitUseCaseImpl(isSelfATeamMember, dispatcher)
    }

    companion object {
        private val dispatcher = TestKaliumDispatcher
    }
}
