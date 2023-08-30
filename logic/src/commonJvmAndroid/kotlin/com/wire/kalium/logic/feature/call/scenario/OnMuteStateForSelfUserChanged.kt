/*
 * Wire
 * Copyright (C) 2023 Wire Swiss GmbH
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
import com.wire.kalium.calling.callbacks.SelfUserMuteHandler
import com.wire.kalium.logic.callingLogger
import com.wire.kalium.logic.data.call.CallRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

internal class OnMuteStateForSelfUserChanged(
    private val scope: CoroutineScope,
    private val callRepository: CallRepository
) : SelfUserMuteHandler {

    override fun onMuteStateChanged(isMuted: Int, arg: Pointer?) {
        println("onMuteStateChanged start")

        scope.launch {
            println("onMuteStateChanged called")
            callingLogger.i("[OnMuteStateForSelfUserChanged] - STARTED")
            callRepository.establishedCallsFlow().first().takeIf {
                println("call is not empty: ${it}")
                it.isNotEmpty()
            }?.first()?.conversationId?.let {
                callingLogger.i("[OnMuteStateForSelfUserChanged] - conversationId: $it muted: $isMuted")
                callRepository.updateIsMutedById(it, isMuted == 1)
            }
        }
    }
}
