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
import com.wire.kalium.logic.callingLogger
import com.wire.kalium.logic.data.call.CallRepository
import com.wire.kalium.logic.data.id.QualifiedIdMapper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

class OnRequestNewEpoch(
    private val scope: CoroutineScope,
    private val callRepository: CallRepository,
    private val qualifiedIdMapper: QualifiedIdMapper,
) : RequestNewEpochHandler {
    override fun onRequestNewEpoch(inst: Handle, conversationId: String, arg: Pointer?) {
        callingLogger.i("[OnRequestNewEpoch] - STARTED")
        scope.launch {
            callRepository.advanceEpoch(qualifiedIdMapper.fromStringToQualifiedID(conversationId))
        }
    }
}
