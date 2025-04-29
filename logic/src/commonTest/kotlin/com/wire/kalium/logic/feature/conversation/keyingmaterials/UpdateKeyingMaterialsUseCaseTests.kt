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

package com.wire.kalium.logic.feature.conversation.keyingmaterials

import com.wire.kalium.common.error.CoreFailure
import com.wire.kalium.common.error.NetworkFailure
import com.wire.kalium.common.error.StorageFailure
import com.wire.kalium.logic.data.conversation.MLSConversationRepository
import com.wire.kalium.logic.data.id.GroupID
import com.wire.kalium.common.functional.Either
import io.ktor.utils.io.errors.IOException
import io.mockative.Mock
import io.mockative.any
import io.mockative.coEvery
import io.mockative.coVerify
import io.mockative.eq
import io.mockative.matches
import io.mockative.mock
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertIs

class UpdateKeyingMaterialsUseCaseTests {

    @Test
    fun givenOutdatedListGroups_ThenRequestToUpdateThemPerformed() = runTest {
        val (arrangement, updateKeyingMaterialsUseCase) = Arrangement()
            .withOutdatedGroupsReturns(Either.Right(Arrangement.OUTDATED_KEYING_MATERIALS_GROUPS))
            .withUpdateKeyingMaterialsSuccessful()
            .arrange()

        val actual = updateKeyingMaterialsUseCase()

        coVerify {
            arrangement.mlsConversationRepository.updateKeyingMaterial(any())
        }.wasInvoked(Arrangement.OUTDATED_KEYING_MATERIALS_GROUPS.size)

        assertIs<UpdateKeyingMaterialsResult.Success>(actual)
    }

    @Test
    fun givenOutdatedListGroups_ThenRequestToUpdateSucceededPartially_ThenReturnSuccess() = runTest {
        val (arrangement, updateKeyingMaterialsUseCase) = Arrangement()
            .withOutdatedGroupsReturns(Either.Right(Arrangement.OUTDATED_KEYING_MATERIALS_GROUPS))
            .withUpdateKeyingMaterialsFailsFor(Arrangement.OUTDATED_KEYING_MATERIALS_GROUPS[0], StorageFailure.DataNotFound)
            .arrange()

        val actual = updateKeyingMaterialsUseCase()

        coVerify {
            arrangement.mlsConversationRepository.updateKeyingMaterial(any())
        }.wasInvoked(Arrangement.OUTDATED_KEYING_MATERIALS_GROUPS.size)

        assertIs<UpdateKeyingMaterialsResult.Success>(actual)
    }

    @Test
    fun givenOutdatedListGroups_ThenRequestToUpdateFailedWithNoInternet_ThenReturnError() = runTest {
        val (arrangement, updateKeyingMaterialsUseCase) = Arrangement()
            .withOutdatedGroupsReturns(Either.Right(Arrangement.OUTDATED_KEYING_MATERIALS_GROUPS))
            .withUpdateKeyingMaterialsFailsFor(Arrangement.OUTDATED_KEYING_MATERIALS_GROUPS[0], NetworkFailure.NoNetworkConnection(IOException("No network")))
            .arrange()

        val actual = updateKeyingMaterialsUseCase()

        coVerify {
            arrangement.mlsConversationRepository.updateKeyingMaterial(any())
        }.wasInvoked(1)
        assertIs<UpdateKeyingMaterialsResult.Failure>(actual)
    }

    @Test
    fun givenEmptyListOfOutdatedGroups_ThenUpdateShouldNotCalled() = runTest {
        val (arrangement, updateKeyingMaterialsUseCase) = Arrangement()
            .withOutdatedGroupsReturns(Either.Right(listOf()))
            .withUpdateKeyingMaterialsSuccessful()
            .arrange()

        val actual = updateKeyingMaterialsUseCase()

        coVerify {
            arrangement.mlsConversationRepository.updateKeyingMaterial(any())
        }.wasNotInvoked()

        assertIs<UpdateKeyingMaterialsResult.Success>(actual)
    }

    @Test
    fun givenListOfOutdatedGroups_WhenUpdateFails_ThenShouldReturnFailure() = runTest {
        val (arrangement, updateKeyingMaterialsUseCase) = Arrangement()
            .withOutdatedGroupsReturns(Either.Left(StorageFailure.DataNotFound))
            .withUpdateKeyingMaterialsSuccessful()
            .arrange()

        val actual = updateKeyingMaterialsUseCase()

        coVerify {
            arrangement.mlsConversationRepository.updateKeyingMaterial(any())
        }.wasNotInvoked()

        assertIs<UpdateKeyingMaterialsResult.Failure>(actual)
    }

    private class Arrangement {
        @Mock
        val mlsConversationRepository = mock(MLSConversationRepository::class)

        private var updateKeyingMaterialsUseCase = UpdateKeyingMaterialsUseCaseImpl(
            mlsConversationRepository,
        )

        suspend fun withOutdatedGroupsReturns(either: Either<CoreFailure, List<GroupID>>) = apply {
            coEvery {
                mlsConversationRepository.getMLSGroupsRequiringKeyingMaterialUpdate(any())
            }.returns(either)
        }

        suspend fun withUpdateKeyingMaterialsSuccessful() = apply {
            coEvery {
                mlsConversationRepository.updateKeyingMaterial(any())
            }.returns(Either.Right(Unit))
        }

        suspend fun withUpdateKeyingMaterialsFailsFor(failedGroupId: GroupID, error: CoreFailure) = apply {
            coEvery {
                mlsConversationRepository.updateKeyingMaterial(eq(failedGroupId))
            }.returns(Either.Left(error))
            coEvery {
                mlsConversationRepository.updateKeyingMaterial(matches { it != failedGroupId })
            }.returns(Either.Right(Unit))
        }

        fun arrange() = this to updateKeyingMaterialsUseCase

        companion object {
            val OUTDATED_KEYING_MATERIALS_GROUPS = listOf(GroupID("group1"), GroupID("group2"),  GroupID("group3"))
        }
    }
}
