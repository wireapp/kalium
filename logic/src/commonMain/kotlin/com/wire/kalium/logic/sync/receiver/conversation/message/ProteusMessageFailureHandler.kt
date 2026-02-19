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
import com.wire.kalium.cryptography.exceptions.ProteusException

internal sealed class ProteusMessageFailureResolution {
    internal data object Ignore : ProteusMessageFailureResolution()
    internal data object RecoverSession : ProteusMessageFailureResolution()
    internal data object InformUser : ProteusMessageFailureResolution()
}

internal object ProteusMessageFailureHandler {
    fun handleFailure(failure: CoreFailure): ProteusMessageFailureResolution {
        if (failure !is ProteusFailure) return ProteusMessageFailureResolution.InformUser

        return when (failure.proteusException.code) {
            ProteusException.Code.DUPLICATE_MESSAGE,
            ProteusException.Code.TOO_DISTANT_FUTURE,
            ProteusException.Code.OUTDATED_MESSAGE -> ProteusMessageFailureResolution.Ignore

            ProteusException.Code.SESSION_NOT_FOUND,
            ProteusException.Code.STORAGE_ERROR,
            ProteusException.Code.PREKEY_NOT_FOUND,
            ProteusException.Code.PANIC,
            ProteusException.Code.LOCAL_FILES_NOT_FOUND -> ProteusMessageFailureResolution.RecoverSession

            ProteusException.Code.REMOTE_IDENTITY_CHANGED,
            ProteusException.Code.INVALID_SIGNATURE,
            ProteusException.Code.INVALID_MESSAGE,
            ProteusException.Code.DECODE_ERROR,
            ProteusException.Code.IDENTITY_ERROR,
            ProteusException.Code.UNKNOWN_ERROR -> {
                if (failure.proteusException.isDuplicateMessage()) {
                    ProteusMessageFailureResolution.Ignore
                } else {
                    ProteusMessageFailureResolution.InformUser
                }
            }
        }
    }

    private fun ProteusException.isDuplicateMessage(): Boolean =
        code == ProteusException.Code.DUPLICATE_MESSAGE ||
            message.orEmpty().contains("DuplicateMessage", ignoreCase = true) ||
            cause?.toString().orEmpty().contains("DuplicateMessage", ignoreCase = true)
}
