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

package com.wire.kalium.logic.data.conversation

import com.wire.kalium.logic.StorageFailure
import com.wire.kalium.logic.data.message.Message
import com.wire.kalium.logic.data.user.LegalHoldStatus
import com.wire.kalium.common.functional.Either
import com.wire.kalium.network.api.model.LegalHoldStatusDTO

interface LegalHoldStatusMapper {
    fun fromApiModel(legalHoldStatusDTO: LegalHoldStatusDTO): LegalHoldStatus
    fun mapLegalHoldConversationStatus(
        legalHoldStatus: Either<StorageFailure, Conversation.LegalHoldStatus>,
        message: Message.Sendable
    ): Conversation.LegalHoldStatus
}

internal object LegalHoldStatusMapperImpl : LegalHoldStatusMapper {
    override fun fromApiModel(legalHoldStatusDTO: LegalHoldStatusDTO): LegalHoldStatus =
        when (legalHoldStatusDTO) {
            LegalHoldStatusDTO.ENABLED -> LegalHoldStatus.ENABLED
            LegalHoldStatusDTO.PENDING -> LegalHoldStatus.PENDING
            LegalHoldStatusDTO.DISABLED -> LegalHoldStatus.DISABLED
            LegalHoldStatusDTO.NO_CONSENT -> LegalHoldStatus.NO_CONSENT
        }

    override fun mapLegalHoldConversationStatus(
        legalHoldStatus: Either<StorageFailure, Conversation.LegalHoldStatus>,
        message: Message.Sendable
    ): Conversation.LegalHoldStatus = when (legalHoldStatus) {
        is Either.Left -> Conversation.LegalHoldStatus.UNKNOWN
        is Either.Right -> {
            when (message) {
                is Message.Regular -> legalHoldStatus.value
                else -> Conversation.LegalHoldStatus.UNKNOWN
            }
        }
    }
}
