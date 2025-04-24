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
import com.wire.kalium.common.error.MLSFailure
import com.wire.kalium.common.error.StorageFailure
import com.wire.kalium.logic.data.conversation.MLSConversationRepository
import com.wire.kalium.logic.data.conversation.UpdateKeyingMaterialThresholdProvider
import com.wire.kalium.logic.data.id.GroupID
import com.wire.kalium.common.functional.Either
import com.wire.kalium.logic.data.conversation.ConversationRepository
import com.wire.kalium.logic.data.id.ConversationIdWithGroup
import com.wire.kalium.logic.framework.TestConversation
import io.mockative.Mock
import io.mockative.any
import io.mockative.coEvery
import io.mockative.coVerify
import io.mockative.eq
import io.mockative.every
import io.mockative.matches
import io.mockative.mock
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertIs
import kotlin.time.Duration.Companion.days

class UpdateKeyingMaterialsUseCaseTests {

    @Test
    fun givenOutdatedListGroups_ThenRequestToUpdateThemPerformed() = runTest {
        val (arrangement, updateKeyingMaterialsUseCase) = Arrangement()
            .withOutdatedGroupsReturns(Either.Right(Arrangement.OUTDATED_KEYING_MATERIALS_GROUPS))
            .withUpdateKeyingMaterialsSuccessful()
            .withKeyingMaterialThreshold()
            .arrange()

        val actual = updateKeyingMaterialsUseCase()

        coVerify {
            arrangement.mlsConversationRepository.updateKeyingMaterial(any())
        }.wasInvoked(Arrangement.OUTDATED_KEYING_MATERIALS_GROUPS.size)

        assertIs<UpdateKeyingMaterialsResult.Success>(actual)
    }

    @Test
    fun givenOutdatedListGroups_ThenRequestToUpdateSucceededPartially_ThenReturnFailed() = runTest {
        val (arrangement, updateKeyingMaterialsUseCase) = Arrangement()
            .withOutdatedGroupsReturns(Either.Right(Arrangement.OUTDATED_KEYING_MATERIALS_GROUPS))
            .withUpdateKeyingMaterialsFailsFor(Arrangement.OUTDATED_KEYING_MATERIALS_GROUPS[0].groupId)
            .withKeyingMaterialThreshold()
            .arrange()

        val actual = updateKeyingMaterialsUseCase()

        coVerify {
            arrangement.mlsConversationRepository.updateKeyingMaterial(any())
        }.wasInvoked(Arrangement.OUTDATED_KEYING_MATERIALS_GROUPS.size)

        assertIs<UpdateKeyingMaterialsResult.Failure>(actual)
    }

    @Test
    fun givenOutdatedListGroups_ThenRequestToUpdateFailsPartiallyWithPendingCommit_ThenSuccessfullyRecovered() = runTest {
        val oneRetry = 1
        val (arrangement, updateKeyingMaterialsUseCase) = Arrangement()
            .withOutdatedGroupsReturns(Either.Right(Arrangement.OUTDATED_KEYING_MATERIALS_GROUPS))
            .withUpdateKeyingMaterialsFailing(MLSFailure.PendingCommitExist, oneRetry)
            .withFetchingGroupInfoSuccessful()
            .withKeyingMaterialThreshold()
            .withLeaveGroupSuccessful()
            .withJoinGroupByExternalCommitSuccessful()
            .arrange()

        val actual = updateKeyingMaterialsUseCase()

        coVerify {
            arrangement.mlsConversationRepository.updateKeyingMaterial(any())
        }.wasInvoked(Arrangement.OUTDATED_KEYING_MATERIALS_GROUPS.size + oneRetry)

        coVerify {
            arrangement.conversationRepository.getGroupInfo(any())
        }.wasInvoked(1)

        coVerify {
            arrangement.mlsConversationRepository.leaveGroup(any())
        }.wasInvoked(1)

        coVerify {
            arrangement.mlsConversationRepository.joinGroupByExternalCommit(any(), any())
        }.wasInvoked(1)

        assertIs<UpdateKeyingMaterialsResult.Success>(actual)
    }

    @Test
    fun givenEmptyListOfOutdatedGroups_ThenUpdateShouldNotCalled() = runTest {
        val (arrangement, updateKeyingMaterialsUseCase) = Arrangement()
            .withOutdatedGroupsReturns(Either.Right(listOf()))
            .withUpdateKeyingMaterialsSuccessful()
            .withKeyingMaterialThreshold()
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
            .withKeyingMaterialThreshold()
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

        @Mock
        val conversationRepository = mock(ConversationRepository::class)

        @Mock
        val updateKeyingMaterialThresholdProvider = mock(UpdateKeyingMaterialThresholdProvider::class)

        private var updateKeyingMaterialsUseCase = UpdateKeyingMaterialsUseCaseImpl(
            mlsConversationRepository,
            conversationRepository,
            updateKeyingMaterialThresholdProvider
        )

        suspend fun withOutdatedGroupsReturns(either: Either<CoreFailure, List<ConversationIdWithGroup>>) = apply {
            coEvery {
                mlsConversationRepository.getMLSGroupsRequiringKeyingMaterialUpdate(any())
            }.returns(either)
        }

        suspend fun withFetchingGroupInfoSuccessful() = apply {
            coEvery {
                conversationRepository.getGroupInfo(any())
            }.returns(Either.Right(PUBLIC_GROUP_STATE))
        }

        fun withKeyingMaterialThreshold() = apply {
            every { updateKeyingMaterialThresholdProvider.keyingMaterialUpdateThreshold }
                .returns(1.days)
        }

        suspend fun withUpdateKeyingMaterialsSuccessful() = apply {
            coEvery {
                mlsConversationRepository.updateKeyingMaterial(any())
            }.returns(Either.Right(Unit))
        }

        suspend fun withUpdateKeyingMaterialsFailsFor(failedGroupId: GroupID) = apply {
            coEvery {
                mlsConversationRepository.updateKeyingMaterial(eq(failedGroupId))
            }.returns(Either.Left(StorageFailure.DataNotFound))
            coEvery {
                mlsConversationRepository.updateKeyingMaterial(matches { it != failedGroupId })
            }.returns(Either.Right(Unit))
        }

        suspend fun withLeaveGroupSuccessful() = apply {
            coEvery {
                mlsConversationRepository.leaveGroup(any())
            }.returns(Either.Right(Unit))
        }

        suspend fun withJoinGroupByExternalCommitSuccessful() = apply {
            coEvery {
                mlsConversationRepository.joinGroupByExternalCommit(any(), any())
            }.returns(Either.Right(Unit))
        }

        suspend fun withUpdateKeyingMaterialsFailing(failure: CoreFailure, times: Int = Int.MAX_VALUE) = apply {
            var invocationCounter = 0
            coEvery { mlsConversationRepository.updateKeyingMaterial(any()) }
                .invokes { _ ->
                    if (invocationCounter < times) {
                        invocationCounter++
                        Either.Left(failure)
                    } else {
                        Either.Right(Unit)
                    }
                }
        }

        fun arrange() = this to updateKeyingMaterialsUseCase

        companion object {
            val PUBLIC_GROUP_STATE = "public_group_state".encodeToByteArray()
            val OUTDATED_KEYING_MATERIALS_GROUPS = listOf(
                ConversationIdWithGroup(TestConversation.ID, GroupID("group1")),
                ConversationIdWithGroup(TestConversation.ID.copy(value = "valueConvo2"), GroupID("group2"))
            )
        }
    }
}
