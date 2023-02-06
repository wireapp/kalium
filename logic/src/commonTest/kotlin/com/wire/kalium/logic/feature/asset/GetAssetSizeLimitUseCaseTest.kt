package com.wire.kalium.logic.feature.asset;

import com.wire.kalium.logic.feature.asset.GetAssetSizeLimitUseCaseImpl.Companion.ASSET_SIZE_DEFAULT_LIMIT_BYTES
import com.wire.kalium.logic.feature.asset.GetAssetSizeLimitUseCaseImpl.Companion.ASSET_SIZE_TEAM_USER_LIMIT_BYTES
import com.wire.kalium.logic.feature.asset.GetAssetSizeLimitUseCaseImpl.Companion.IMAGE_SIZE_LIMIT_BYTES
import com.wire.kalium.logic.feature.user.IsSelfATeamMemberUseCase
import com.wire.kalium.logic.test_util.TestKaliumDispatcher
import io.mockative.Mock
import io.mockative.classOf
import io.mockative.given
import io.mockative.mock
import io.mockative.once
import io.mockative.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
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
        verify(arrangement.isSelfATeamMember)
            .suspendFunction(arrangement.isSelfATeamMember::invoke)
            .wasInvoked(exactly = once)
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
        verify(arrangement.isSelfATeamMember)
            .suspendFunction(arrangement.isSelfATeamMember::invoke)
            .wasInvoked(exactly = once)
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
        verify(arrangement.isSelfATeamMember)
            .suspendFunction(arrangement.isSelfATeamMember::invoke)
            .wasInvoked(exactly = once)
    }

    private class Arrangement {

        @Mock
        val isSelfATeamMember = mock(classOf<IsSelfATeamMemberUseCase>())

        fun withIsSelfATeamMember(hasUserTeam: Boolean) = apply {
            given(isSelfATeamMember)
                .suspendFunction(isSelfATeamMember::invoke)
                .whenInvoked()
                .thenReturn(hasUserTeam)
        }

        fun arrange() = this to GetAssetSizeLimitUseCaseImpl(isSelfATeamMember, dispatcher)
    }

    companion object {
        private val dispatcher = TestKaliumDispatcher
    }
}
