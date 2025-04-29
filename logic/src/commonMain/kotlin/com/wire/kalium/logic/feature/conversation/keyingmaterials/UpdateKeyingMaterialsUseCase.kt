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
import com.wire.kalium.logic.data.conversation.MLSConversationRepository
import com.wire.kalium.common.functional.fold
import com.wire.kalium.common.functional.onFailure
import com.wire.kalium.common.logger.kaliumLogger
import com.wire.kalium.common.logger.logStructuredJson
import com.wire.kalium.logger.KaliumLogLevel
import com.wire.kalium.logic.data.id.GroupID
import kotlin.time.Duration.Companion.days

sealed class UpdateKeyingMaterialsResult {

    data object Success : UpdateKeyingMaterialsResult()
    data class Failure(val failure: CoreFailure) : UpdateKeyingMaterialsResult()

}

/**
 * This use case will check if the number of keying materials is below the minimum threshold and will
 * upload new keying materials if needed for the mls conversations of the user.
 */
interface UpdateKeyingMaterialsUseCase {
    suspend operator fun invoke(): UpdateKeyingMaterialsResult
}

internal class UpdateKeyingMaterialsUseCaseImpl(
    val mlsConversationRepository: MLSConversationRepository,
) : UpdateKeyingMaterialsUseCase {
    override suspend fun invoke(): UpdateKeyingMaterialsResult = mlsConversationRepository
        .getMLSGroupsRequiringKeyingMaterialUpdate(KEYING_MATERIAL_UPDATE_THRESHOLD)
        .fold(
            {
                UpdateKeyingMaterialsResult.Failure(it)
            },
            { groups ->
                val failedGroup: MutableList<GroupID> = mutableListOf()
                // TODO: this should run in parallel
                for (group in groups) {
                    mlsConversationRepository.updateKeyingMaterial(group).onFailure {
                        if (it is NetworkFailure.NoNetworkConnection) {
                            return@fold UpdateKeyingMaterialsResult.Failure(it)

                        }
                    }
                    failedGroup.add(group)
                }
                kaliumLogger.logStructuredJson(
                    KaliumLogLevel.DEBUG,
                    "Keying materials updated successfully returning success",
                    mapOf(
                        "totalGroups" to groups.size,
                        "failedGroupCount" to failedGroup.size,
                    )
                )
                UpdateKeyingMaterialsResult.Success
            }
        )

    private companion object {
        // TODO: there are some edge cases and optimisations points to consider for M5-> please see: https://wearezeta.atlassian.net/browse/AR-1633
        val KEYING_MATERIAL_UPDATE_THRESHOLD = 90.days
    }
}
