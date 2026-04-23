/*
 * Wire
 * Copyright (C) 2025 Wire Swiss GmbH
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
package com.wire.kalium.logic.feature.featureConfig

import app.cash.turbine.test
import com.wire.kalium.common.error.StorageFailure
import com.wire.kalium.common.functional.Either
import com.wire.kalium.common.functional.left
import com.wire.kalium.common.functional.right
import com.wire.kalium.logic.configuration.UserConfigRepository
import com.wire.kalium.logic.data.id.SelfTeamIdProvider
import com.wire.kalium.logic.data.id.TeamId
import com.wire.kalium.logic.data.user.SupportedProtocol
import com.wire.kalium.logic.framework.TestTeam
import dev.mokkery.answering.returns
import dev.mokkery.everySuspend
import dev.mokkery.mock
import dev.mokkery.verifySuspend
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class ObserveIsAppsAllowedForUsageUseCaseTest {

    @Test
    fun givenAnErrorWhileGettingUserDoesBelongToTeam_whenObservingAppsEnabledConfig_thenReturnFalse() = runTest {
        val (arrangement, observeAppsEnabledConfigUseCase) = Arrangement()
            .withObserveAppsEnabledResult(flowOf(Either.Right(true)))
            .withDefaultProtocol(Either.Right(SupportedProtocol.MLS))
            .withSupportedProtocols(Either.Right(setOf(SupportedProtocol.MLS)))
            .withSelfTeamIdProviderResult(StorageFailure.DataNotFound.left())
            .arrange()

        val result = observeAppsEnabledConfigUseCase()

        result.test {
            val item = awaitItem()
            assertEquals(AppsAllowedResult.Disabled, item)

            cancelAndIgnoreRemainingEvents()

            verifySuspend {
                arrangement.userConfigRepository.observeAppsEnabled()
            }
        }
    }

    @Test
    fun givenUserDoesNotBelongToTeam_whenObservingAppsEnabledConfig_thenReturnFalse() = runTest {
        val (arrangement, observeAppsEnabledConfigUseCase) = Arrangement()
            .withObserveAppsEnabledResult(flowOf(Either.Right(true)))
            .withDefaultProtocol(Either.Right(SupportedProtocol.MLS))
            .withSupportedProtocols(Either.Right(setOf(SupportedProtocol.MLS)))
            .withSelfTeamIdProviderResult(null.right())
            .arrange()

        val result = observeAppsEnabledConfigUseCase()

        result.test {
            val item = awaitItem()
            assertEquals(AppsAllowedResult.Disabled, item)

            cancelAndIgnoreRemainingEvents()

            verifySuspend {
                arrangement.userConfigRepository.observeAppsEnabled()
            }
        }
    }

    @Test
    fun givenUserConfigRepositoryFailureOrNotPresent_whenObservingAppsEnabledConfig_thenReturnFalse() = runTest {
        val (arrangement, observeAppsEnabledConfigUseCase) = Arrangement()
            .withObserveAppsEnabledResult(flowOf(StorageFailure.DataNotFound.left()))
            .withDefaultProtocol(Either.Right(SupportedProtocol.MLS))
            .withSupportedProtocols(Either.Right(setOf(SupportedProtocol.MLS)))
            .withSelfTeamIdProviderResult(TestTeam.TEAM_ID.right())
            .arrange()

        val result = observeAppsEnabledConfigUseCase()

        result.test {
            val item = awaitItem()
            assertEquals(AppsAllowedResult.Disabled, item)

            cancelAndIgnoreRemainingEvents()

            verifySuspend {
                arrangement.userConfigRepository.observeAppsEnabled()
            }
        }
    }

    @Test
    fun givenUserConfigRepositorySuccess_whenObservingAppsEnabledConfig_thenReturnTrue() = runTest {
        val (arrangement, observeAppsEnabledConfigUseCase) = Arrangement()
            .withObserveAppsEnabledResult(flowOf(Either.Right(true)))
            .withDefaultProtocol(Either.Right(SupportedProtocol.MLS))
            .withSupportedProtocols(Either.Right(setOf(SupportedProtocol.MLS)))
            .withSelfTeamIdProviderResult(TestTeam.TEAM_ID.right())
            .arrange()

        val result = observeAppsEnabledConfigUseCase()

        result.test {
            val item = awaitItem()
            assertEquals(AppsAllowedResult.Enabled(AppsAllowedProtocol.MLS), item)

            cancelAndIgnoreRemainingEvents()

            verifySuspend {
                arrangement.userConfigRepository.observeAppsEnabled()
            }
        }
    }

    private val testCases = listOf(
        AppsAllowedResultTestCase(
            description = "Proteus Default Protocol, Proteus Supported Protocol, Apps Disabled",
            appsEnabled = false.right(),
            defaultProtocol = SupportedProtocol.PROTEUS.right(),
            supportedProtocols = setOf(SupportedProtocol.PROTEUS).right(),
            selfTeamId = TestTeam.TEAM_ID.right(),
            expectedResult = AppsAllowedResult.Enabled(AppsAllowedProtocol.PROTEUS)
        ),
        AppsAllowedResultTestCase(
            description = "Proteus Default Protocol, Proteus Supported Protocol, Apps Enabled",
            appsEnabled = true.right(),
            defaultProtocol = SupportedProtocol.PROTEUS.right(),
            supportedProtocols = setOf(SupportedProtocol.PROTEUS).right(),
            selfTeamId = TestTeam.TEAM_ID.right(),
            expectedResult = AppsAllowedResult.Enabled(AppsAllowedProtocol.PROTEUS)
        ),
        AppsAllowedResultTestCase(
            description = "Proteus Default Protocol, Mixed Supported Protocol, Apps Disabled",
            appsEnabled = false.right(),
            defaultProtocol = SupportedProtocol.PROTEUS.right(),
            supportedProtocols = setOf(SupportedProtocol.PROTEUS, SupportedProtocol.MLS).right(),
            selfTeamId = TestTeam.TEAM_ID.right(),
            expectedResult = AppsAllowedResult.Enabled(AppsAllowedProtocol.MIXED(SupportedProtocol.PROTEUS))
        ),
        AppsAllowedResultTestCase(
            description = "Proteus Default Protocol, Mixed Supported Protocol, Apps Enabled",
            appsEnabled = true.right(),
            defaultProtocol = SupportedProtocol.PROTEUS.right(),
            supportedProtocols = setOf(SupportedProtocol.PROTEUS, SupportedProtocol.MLS).right(),
            selfTeamId = TestTeam.TEAM_ID.right(),
            expectedResult = AppsAllowedResult.Enabled(AppsAllowedProtocol.MIXED(SupportedProtocol.MLS))
        ),
        AppsAllowedResultTestCase(
            description = "MLS Default Protocol, MLS Supported Protocol, Apps Disabled",
            appsEnabled = false.right(),
            defaultProtocol = SupportedProtocol.MLS.right(),
            supportedProtocols = setOf(SupportedProtocol.MLS).right(),
            selfTeamId = TestTeam.TEAM_ID.right(),
            expectedResult = AppsAllowedResult.Disabled
        ),
        AppsAllowedResultTestCase(
            description = "MLS Default Protocol, Mixed Supported Protocol, Apps Disabled",
            appsEnabled = false.right(),
            defaultProtocol = SupportedProtocol.MLS.right(),
            supportedProtocols = setOf(SupportedProtocol.PROTEUS, SupportedProtocol.MLS).right(),
            selfTeamId = TestTeam.TEAM_ID.right(),
            expectedResult = AppsAllowedResult.Enabled(AppsAllowedProtocol.MIXED(SupportedProtocol.PROTEUS))
        ),
        AppsAllowedResultTestCase(
            description = "MLS Default Protocol, MLS Supported Protocol, Apps Enabled",
            appsEnabled = true.right(),
            defaultProtocol = SupportedProtocol.MLS.right(),
            supportedProtocols = setOf(SupportedProtocol.MLS).right(),
            selfTeamId = TestTeam.TEAM_ID.right(),
            expectedResult = AppsAllowedResult.Enabled(AppsAllowedProtocol.MLS)
        ),
        AppsAllowedResultTestCase(
            description = "MLS Default Protocol, Mixed Supported Protocol, Apps Enabled",
            appsEnabled = true.right(),
            defaultProtocol = SupportedProtocol.MLS.right(),
            supportedProtocols = setOf(SupportedProtocol.PROTEUS, SupportedProtocol.MLS).right(),
            selfTeamId = TestTeam.TEAM_ID.right(),
            expectedResult = AppsAllowedResult.Enabled(AppsAllowedProtocol.MIXED(SupportedProtocol.MLS))
        ),
    )

    @Test
    fun givenDifferentConfigs_whenObservingAppsEnabled_thenReturnExpectedResult() = runTest {
        testCases.forEach { case ->
            val (_, useCase) = Arrangement()
                .withObserveAppsEnabledResult(flowOf(case.appsEnabled))
                .withDefaultProtocol(case.defaultProtocol)
                .withSupportedProtocols(case.supportedProtocols)
                .withSelfTeamIdProviderResult(case.selfTeamId)
                .arrange()

            useCase().test {
                assertEquals(case.expectedResult, awaitItem(), "Failed for: ${case.description}")
                cancelAndIgnoreRemainingEvents()
            }
        }
    }

    private class Arrangement {
        val userConfigRepository: UserConfigRepository = mock<UserConfigRepository>()
        val selfTeamIdProvider: SelfTeamIdProvider = mock<SelfTeamIdProvider>()

        suspend fun withObserveAppsEnabledResult(result: Flow<Either<StorageFailure, Boolean>>) = apply {
            everySuspend { userConfigRepository.observeAppsEnabled() } returns result
        }

        suspend fun withSelfTeamIdProviderResult(result: Either<StorageFailure, TeamId?>) = apply {
            everySuspend { selfTeamIdProvider() } returns result
        }

        suspend fun withDefaultProtocol(result: Either<StorageFailure, SupportedProtocol>) = apply {
            everySuspend { userConfigRepository.getDefaultProtocol() } returns result
        }

        suspend fun withSupportedProtocols(result: Either<StorageFailure, Set<SupportedProtocol>>) = apply {
            everySuspend { userConfigRepository.getSupportedProtocols() } returns result
        }

        fun arrange(): Pair<Arrangement, ObserveIsAppsAllowedForUsageUseCase> {
            return this to ObserveIsAppsAllowedForUsageUseCaseImpl(userConfigRepository, selfTeamIdProvider)
        }
    }
}

data class AppsAllowedResultTestCase(
    val description: String,
    val appsEnabled: Either<StorageFailure, Boolean>,
    val defaultProtocol: Either<StorageFailure, SupportedProtocol>,
    val supportedProtocols: Either<StorageFailure, Set<SupportedProtocol>>,
    val selfTeamId: Either<StorageFailure, TeamId?>,
    val expectedResult: AppsAllowedResult
)
