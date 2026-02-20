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
import kotlin.test.Test
import kotlin.test.assertEquals

class ProteusMessageFailureHandlerTest {

    @Test
    fun givenDuplicateCode_whenHandlingProteusFailure_thenIgnore() {
        val failure = ProteusFailure(
            ProteusException(
                message = null,
                code = ProteusException.Code.DUPLICATE_MESSAGE,
                intCode = 7
            )
        )

        val result = ProteusMessageFailureHandler.handleFailure(failure)

        assertEquals(ProteusMessageFailureResolution.Ignore, result)
    }

    @Test
    fun givenUnknownCodeWithDuplicateMessageText_whenHandlingProteusFailure_thenInformUser() {
        val failure = ProteusFailure(
            ProteusException(
                message = "exception=com.wire.crypto.ProteusException\$DuplicateMessage: ",
                code = ProteusException.Code.UNKNOWN_ERROR,
                intCode = null
            )
        )

        val result = ProteusMessageFailureHandler.handleFailure(failure)

        assertEquals(ProteusMessageFailureResolution.InformUser, result)
    }

    @Test
    fun givenTooDistantFuture_whenHandlingProteusFailure_thenIgnore() {
        val failure = ProteusFailure(
            ProteusException(
                message = null,
                code = ProteusException.Code.TOO_DISTANT_FUTURE,
                intCode = 8
            )
        )

        val result = ProteusMessageFailureHandler.handleFailure(failure)

        assertEquals(ProteusMessageFailureResolution.Ignore, result)
    }

    @Test
    fun givenSessionNotFound_whenHandlingProteusFailure_thenRecoverSession() {
        val failure = ProteusFailure(
            ProteusException(
                message = null,
                code = ProteusException.Code.SESSION_NOT_FOUND,
                intCode = 2
            )
        )

        val result = ProteusMessageFailureHandler.handleFailure(failure)

        assertEquals(ProteusMessageFailureResolution.RecoverSession, result)
    }

    @Test
    fun givenNonDuplicateProteusFailure_whenHandling_thenInformUser() {
        val failure = ProteusFailure(
            ProteusException(
                message = null,
                code = ProteusException.Code.INVALID_SIGNATURE,
                intCode = 5
            )
        )

        val result = ProteusMessageFailureHandler.handleFailure(failure)

        assertEquals(ProteusMessageFailureResolution.InformUser, result)
    }

    @Test
    fun givenNonProteusFailure_whenHandling_thenInformUser() {
        val result = ProteusMessageFailureHandler.handleFailure(CoreFailure.InvalidEventSenderID)

        assertEquals(ProteusMessageFailureResolution.InformUser, result)
    }
}
