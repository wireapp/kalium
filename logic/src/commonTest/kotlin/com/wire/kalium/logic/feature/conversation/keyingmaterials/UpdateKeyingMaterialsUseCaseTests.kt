package com.wire.kalium.logic.feature.conversation.keyingmaterials

import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.StorageFailure
import com.wire.kalium.logic.data.conversation.MLSConversationRepository
import com.wire.kalium.logic.data.conversation.UpdateKeyingMaterialThresholdProvider
import com.wire.kalium.logic.data.id.GroupID

import com.wire.kalium.logic.functional.Either
import io.mockative.Mock
import io.mockative.Times
import io.mockative.any
import io.mockative.anything
import io.mockative.classOf
import io.mockative.given
import io.mockative.mock
import io.mockative.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertIs
import kotlin.time.Duration.Companion.days

@OptIn(ExperimentalCoroutinesApi::class)
class UpdateKeyingMaterialsUseCaseTests {

    @Test
    fun givenOutdatedListGroups_ThenRequestToUpdateThemPerformed() = runTest {
        val (arrangement, updateKeyingMaterialsUseCase) = Arrangement()
            .withOutdatedGroupsReturns(Either.Right(Arrangement.OUTDATED_KEYING_MATERIALS_GROUPS))
            .withUpdateKeyingMaterials()
            .withKeyingMaterialThreshold()
            .arrange()

        val actual = updateKeyingMaterialsUseCase()

        verify(arrangement.mlsConversationRepository)
            .suspendFunction(arrangement.mlsConversationRepository::updateKeyingMaterial)
            .with(any())
            .wasInvoked(Times(Arrangement.OUTDATED_KEYING_MATERIALS_GROUPS.size))

        assertIs<UpdateKeyingMaterialsResult.Success>(actual)
    }

    @Test
    fun givenEmptyListOfOutdatedGroups_ThenUpdateShouldNotCalled() = runTest {
        val (arrangement, updateKeyingMaterialsUseCase) = Arrangement()
            .withOutdatedGroupsReturns(Either.Right(listOf()))
            .withUpdateKeyingMaterials()
            .withKeyingMaterialThreshold()
            .arrange()

        val actual = updateKeyingMaterialsUseCase()

        verify(arrangement.mlsConversationRepository)
            .suspendFunction(arrangement.mlsConversationRepository::updateKeyingMaterial)
            .with(any())
            .wasNotInvoked()

        assertIs<UpdateKeyingMaterialsResult.Success>(actual)
    }

    @Test
    fun givenListOfOutdatedGroups_WhenUpdateFails_ThenShouldReturnFailure() = runTest {
        val (arrangement, updateKeyingMaterialsUseCase) = Arrangement()
            .withOutdatedGroupsReturns(Either.Left(StorageFailure.DataNotFound))
            .withUpdateKeyingMaterials()
            .withKeyingMaterialThreshold()
            .arrange()

        val actual = updateKeyingMaterialsUseCase()

        verify(arrangement.mlsConversationRepository)
            .suspendFunction(arrangement.mlsConversationRepository::updateKeyingMaterial)
            .with(any())
            .wasNotInvoked()

        assertIs<UpdateKeyingMaterialsResult.Failure>(actual)
    }

    private class Arrangement {
        @Mock
        val mlsConversationRepository = mock(classOf<MLSConversationRepository>())

        @Mock
        val updateKeyingMaterialThresholdProvider = mock(classOf<UpdateKeyingMaterialThresholdProvider>())

        private var updateKeyingMaterialsUseCase = UpdateKeyingMaterialsUseCaseImpl(
            mlsConversationRepository,
            updateKeyingMaterialThresholdProvider
        )

        fun withOutdatedGroupsReturns(either: Either<CoreFailure, List<GroupID>>) = apply {
            given(mlsConversationRepository).suspendFunction(mlsConversationRepository::getMLSGroupsRequiringKeyingMaterialUpdate)
                .whenInvokedWith(anything())
                .thenReturn(either)
        }

        fun withKeyingMaterialThreshold() = apply {
            given(updateKeyingMaterialThresholdProvider).invocation { keyingMaterialUpdateThreshold }.thenReturn(1.days)
        }

        fun withUpdateKeyingMaterials() = apply {
            given(mlsConversationRepository).suspendFunction(mlsConversationRepository::updateKeyingMaterial)
                .whenInvokedWith(anything())
                .then { Either.Right(Unit) }
        }

        fun arrange() = this to updateKeyingMaterialsUseCase

        companion object {
            val OUTDATED_KEYING_MATERIALS_GROUPS = listOf(GroupID("group1"), GroupID("group2"))
        }
    }
}
