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
package com.wire.kalium.logic.feature.conversation

import com.wire.kalium.logic.StorageFailure
import com.wire.kalium.logic.data.conversation.Conversation
import com.wire.kalium.logic.data.conversation.LegalHoldStatusMapperImpl
import com.wire.kalium.logic.data.message.MessageContent
import com.wire.kalium.logic.data.user.LegalHoldStatus
import com.wire.kalium.logic.framework.TestMessage
import com.wire.kalium.logic.framework.TestMessage.signalingMessage
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.network.api.base.model.LegalHoldStatusDTO
import kotlin.test.Test
import kotlin.test.assertEquals

class LegalHoldStatusMapperTest {

    @Test
    fun givenDTOLegalHoldStatus_whenMappingToDomain_thenMapCorrectly() {
        val legalHoldStatusMapper = Arrangement().legalHoldStatusMapper

        val resultDisabled = legalHoldStatusMapper.fromApiModel(LegalHoldStatusDTO.DISABLED)
        assertEquals(LegalHoldStatus.DISABLED, resultDisabled)

        val resultEnabled = legalHoldStatusMapper.fromApiModel(LegalHoldStatusDTO.ENABLED)
        assertEquals(LegalHoldStatus.ENABLED, resultEnabled)

        val resultNonConsent = legalHoldStatusMapper.fromApiModel(LegalHoldStatusDTO.NO_CONSENT)
        assertEquals(LegalHoldStatus.NO_CONSENT, resultNonConsent)

        val resultPending = legalHoldStatusMapper.fromApiModel(LegalHoldStatusDTO.PENDING)
        assertEquals(LegalHoldStatus.PENDING, resultPending)
    }

    @Test
    fun givenStorageFailure_whenMappingLegalHoldStatus_thenReturnUnknown() {
        val legalHoldStatusMapper = Arrangement().legalHoldStatusMapper

        val result = legalHoldStatusMapper.mapLegalHoldConversationStatus(
            Either.Left(StorageFailure.DataNotFound),
            TestMessage.TEXT_MESSAGE
        )
        assertEquals(Conversation.LegalHoldStatus.UNKNOWN, result)
    }

    @Test
    fun givenRegularMessage_whenMappingLegalHoldStatus_thenReturnLegalHoldStatusOfTheMessage() {
        val legalHoldStatusMapper = Arrangement().legalHoldStatusMapper

        val result1 = legalHoldStatusMapper.mapLegalHoldConversationStatus(
            Either.Right(Conversation.LegalHoldStatus.ENABLED),
            TestMessage.TEXT_MESSAGE
        )
        assertEquals(Conversation.LegalHoldStatus.ENABLED, result1)

        val result2 = legalHoldStatusMapper.mapLegalHoldConversationStatus(
            Either.Right(Conversation.LegalHoldStatus.DISABLED),
            TestMessage.TEXT_MESSAGE
        )
        assertEquals(Conversation.LegalHoldStatus.DISABLED, result2)
    }

    @Test
    fun givenNonRegularMessage_whenMappingLegalHoldStatus_thenReturnDisabledStatus() {
        val legalHoldStatusMapper = Arrangement().legalHoldStatusMapper

        val result = legalHoldStatusMapper.mapLegalHoldConversationStatus(
            Either.Right(Conversation.LegalHoldStatus.ENABLED),
            signalingMessage(
                MessageContent.TextEdited(
                    editMessageId = "ORIGINAL_MESSAGE_ID",
                    newContent = "some new content",
                    newMentions = listOf()
                )
            )
        )

        assertEquals(Conversation.LegalHoldStatus.UNKNOWN, result)
    }

    private class Arrangement {
        val legalHoldStatusMapper = LegalHoldStatusMapperImpl
    }
}
