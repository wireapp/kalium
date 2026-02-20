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
package com.wire.kalium.logic.feature.call.scenario

import com.sun.jna.Pointer
import com.wire.kalium.calling.callbacks.RequestNewEpochHandler
import com.wire.kalium.calling.types.Handle
import com.wire.kalium.common.logger.callingLogger
import com.wire.kalium.logger.obfuscateId
import com.wire.kalium.logic.data.id.QualifiedIdMapper
import com.wire.kalium.logic.feature.call.usecase.EpochInfoUpdater
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

internal class OnRequestNewEpoch(
    private val epochInfoUpdater: EpochInfoUpdater,
    private val qualifiedIdMapper: QualifiedIdMapper,
    private val callingScope: CoroutineScope,
) : RequestNewEpochHandler {
    override fun onRequestNewEpoch(inst: Handle, conversationId: String, arg: Pointer?) {
        callingScope.launch {
            callingLogger.i("[OnRequestNewEpoch] - ConversationId: ${conversationId.obfuscateId()}")
            val conversationIdWithDomain = qualifiedIdMapper.fromStringToQualifiedID(conversationId)

            // Update AVS with current EpochInfo for MLS calls when new epoch is requested
            epochInfoUpdater(conversationIdWithDomain)
        }
    }
}
