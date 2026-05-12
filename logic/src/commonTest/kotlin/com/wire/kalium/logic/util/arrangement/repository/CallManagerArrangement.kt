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
package com.wire.kalium.logic.util.arrangement.repository

import com.wire.kalium.logic.feature.call.CallManager
import dev.mokkery.matcher.any
import dev.mokkery.everySuspend
import dev.mokkery.MockMode
import dev.mokkery.answering.returns
import dev.mokkery.mock

internal interface CallManagerArrangement {

    val callManager: CallManager

    suspend fun withEndCall()
}

internal class CallManagerArrangementImpl : CallManagerArrangement {

    override val callManager = mock<CallManager>(mode = MockMode.autoUnit)

    override suspend fun withEndCall() {
        everySuspend { callManager.endCall(any()) }.returns(Unit)
    }
}
