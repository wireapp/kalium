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
package com.wire.kalium.logic.sync.receiver.conversation.message

import com.wire.kalium.common.error.CoreFailure
import com.wire.kalium.common.error.ProteusFailure
import com.wire.kalium.logic.data.message.InvalidMessageContent

sealed class ProteusMessageFailureResolution {
    data object Ignore : ProteusMessageFailureResolution()
    data object RecoverSession : ProteusMessageFailureResolution()
    data object InformUser : ProteusMessageFailureResolution()
}

object ProteusMessageFailureHandler {
    fun handleFailure(failure: CoreFailure): ProteusMessageFailureResolution {
        if (failure !is ProteusFailure) {
            return if (failure is InvalidMessageContent) ProteusMessageFailureResolution.Ignore
            else ProteusMessageFailureResolution.InformUser
        }

        return when (failure.code) {
            ProteusFailure.Code.DUPLICATE_MESSAGE -> ProteusMessageFailureResolution.Ignore

            ProteusFailure.Code.SESSION_NOT_FOUND,
            ProteusFailure.Code.TOO_DISTANT_FUTURE,
            ProteusFailure.Code.STORAGE_ERROR,
            ProteusFailure.Code.PREKEY_NOT_FOUND,
            ProteusFailure.Code.PANIC,
            ProteusFailure.Code.OUTDATED_MESSAGE,
            ProteusFailure.Code.LOCAL_FILES_NOT_FOUND -> ProteusMessageFailureResolution.RecoverSession

            ProteusFailure.Code.REMOTE_IDENTITY_CHANGED,
            ProteusFailure.Code.INVALID_SIGNATURE,
            ProteusFailure.Code.INVALID_MESSAGE,
            ProteusFailure.Code.DECODE_ERROR,
            ProteusFailure.Code.IDENTITY_ERROR,
            ProteusFailure.Code.UNKNOWN_ERROR -> ProteusMessageFailureResolution.InformUser
        }
    }
}
