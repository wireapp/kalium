/*
 * Wire
 * Copyright (C) 2026 Wire Swiss GmbH
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
import com.wire.kalium.common.functional.Either
import com.wire.kalium.common.functional.flatMap
import com.wire.kalium.common.functional.fold
import com.wire.kalium.common.functional.left
import com.wire.kalium.common.functional.map
import com.wire.kalium.common.functional.onFailure
import com.wire.kalium.common.functional.right
import com.wire.kalium.logic.data.client.CryptoTransactionProvider
import com.wire.kalium.logic.data.conversation.MLSConversationRepository
import com.wire.kalium.logic.data.id.GroupID
import com.wire.kalium.logic.sync.SyncManager
import kotlin.time.Duration

/**
 * Renews the self client's keying material in every established MLS group, however recently it was renewed.
 *
 * Meant for after the client state was restored from an older copy. The restored state can hand out message keys that
 * the other members already used and deleted, so they couldn't read what the client sends next. An update commit starts
 * a new epoch with fresh keys for every member. A group that moved on since the copy rejects the commit; it's joined
 * again once the stale epoch is detected.
 *
 * Waits until the sync is live, so the groups are at their current epoch.
 */
public interface RenewMLSKeyingMaterialsUseCase {
    public suspend operator fun invoke(): RenewMLSKeyingMaterialsResult
}

public sealed interface RenewMLSKeyingMaterialsResult {

    /** [renewedGroups] got a new epoch. [failedGroups] didn't, for example because they moved on since the copy. */
    public data class Success(public val renewedGroups: Int, public val failedGroups: Int) : RenewMLSKeyingMaterialsResult

    /** The sync didn't become live, the groups couldn't be read, or the network went away. Try again later. */
    public data class Failure(public val failure: CoreFailure) : RenewMLSKeyingMaterialsResult
}

internal class RenewMLSKeyingMaterialsUseCaseImpl(
    private val syncManager: SyncManager,
    private val mlsConversationRepository: MLSConversationRepository,
    private val transactionProvider: CryptoTransactionProvider
) : RenewMLSKeyingMaterialsUseCase {

    override suspend fun invoke(): RenewMLSKeyingMaterialsResult =
        syncManager.waitUntilLiveOrFailure()
            .flatMap { mlsConversationRepository.getMLSGroupsRequiringKeyingMaterialUpdate(Duration.ZERO) }
            .flatMap { groups -> renew(groups) }
            .fold({ RenewMLSKeyingMaterialsResult.Failure(it) }, { it })

    private suspend fun renew(groups: List<GroupID>): Either<CoreFailure, RenewMLSKeyingMaterialsResult.Success> {
        var failedGroups = 0
        return transactionProvider.mlsTransaction("RenewKeyingMaterials") { mlsContext ->
            groups.forEach { group ->
                mlsConversationRepository.updateKeyingMaterial(mlsContext, group).onFailure {
                    if (it is NetworkFailure.NoNetworkConnection) return@mlsTransaction it.left()
                    failedGroups++
                }
            }
            Unit.right()
        }.map {
            RenewMLSKeyingMaterialsResult.Success(renewedGroups = groups.size - failedGroups, failedGroups = failedGroups)
        }
    }
}
