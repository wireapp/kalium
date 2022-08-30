package com.wire.kalium.logic.feature.conversation.keyingmaterials

import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.data.conversation.MLSConversationRepository
import com.wire.kalium.logic.data.conversation.UpdateKeyingMaterialThresholdProvider
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.functional.fold
import com.wire.kalium.logic.functional.map
import com.wire.kalium.logic.functional.onFailure

sealed class UpdateKeyingMaterialsResult {

    object Success : UpdateKeyingMaterialsResult()
    class Failure(val failure: CoreFailure) : UpdateKeyingMaterialsResult()

}

interface UpdateKeyingMaterialsUseCase {
    suspend operator fun invoke(): UpdateKeyingMaterialsResult
}

class UpdateKeyingMaterialsUseCaseImpl(
    val mlsConversationRepository: MLSConversationRepository,
    private val updateKeyingMaterialThresholdProvider: UpdateKeyingMaterialThresholdProvider
) : UpdateKeyingMaterialsUseCase {
    override suspend fun invoke(): UpdateKeyingMaterialsResult =
        mlsConversationRepository
            .getMLSGroupsRequiringKeyingMaterialUpdate(updateKeyingMaterialThresholdProvider.keyingMaterialUpdateThreshold).map { groups ->
                var updatesSucceeded = true
                groups.onEach { groupId ->
                    mlsConversationRepository.updateKeyingMaterial(groupId).onFailure {
                        updatesSucceeded = false
                    }
                }
                if (updatesSucceeded)
                    Either.Right(Unit)
                else Either.Left(Unit)
            }.fold(
                { UpdateKeyingMaterialsResult.Failure(it) },
                { UpdateKeyingMaterialsResult.Success }
            )

}
