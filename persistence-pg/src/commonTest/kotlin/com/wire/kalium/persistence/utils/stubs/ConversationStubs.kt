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

package com.wire.kalium.persistence.utils.stubs

import com.wire.kalium.persistence.dao.QualifiedIDEntity
import com.wire.kalium.persistence.dao.conversation.ConversationEntity
import kotlinx.datetime.Instant
import kotlinx.datetime.toInstant

fun newConversationEntity(id: String = "test") = ConversationEntity(
    id = QualifiedIDEntity(id, "wire.com"),
    name = "conversation1",
    type = ConversationEntity.Type.ONE_ON_ONE,
    teamId = "teamID",
    protocolInfo = ConversationEntity.ProtocolInfo.Proteus,
    creatorId = "someValue",
    lastNotificationDate = null,
    lastModifiedDate = "2022-03-30T15:36:00.000Z".toInstant(),
    lastReadDate = "2000-01-01T12:00:00.000Z".toInstant(),
    access = listOf(ConversationEntity.Access.LINK, ConversationEntity.Access.INVITE),
    accessRole = listOf(ConversationEntity.AccessRole.NON_TEAM_MEMBER, ConversationEntity.AccessRole.TEAM_MEMBER),
    receiptMode = ConversationEntity.ReceiptMode.DISABLED,
    messageTimer = null,
    userMessageTimer = null,
    archived = false,
    archivedInstant = null,
    mlsVerificationStatus = ConversationEntity.VerificationStatus.NOT_VERIFIED,
    proteusVerificationStatus = ConversationEntity.VerificationStatus.NOT_VERIFIED,
    legalHoldStatus = ConversationEntity.LegalHoldStatus.DISABLED
)

fun newConversationEntity(
    id: QualifiedIDEntity,
    lastReadDate: Instant = Instant.parse("1970-01-01T00:00:00.000Z"),
    lastModified: Instant = Instant.parse("2022-03-30T15:36:00.000Z")
) = ConversationEntity(
    id = id,
    name = "conversation1",
    type = ConversationEntity.Type.ONE_ON_ONE,
    teamId = "teamID",
    protocolInfo = ConversationEntity.ProtocolInfo.Proteus,
    creatorId = "someValue",
    lastNotificationDate = null,
    lastReadDate = lastReadDate,
    lastModifiedDate = lastModified,
    access = listOf(ConversationEntity.Access.LINK, ConversationEntity.Access.INVITE),
    accessRole = listOf(ConversationEntity.AccessRole.NON_TEAM_MEMBER, ConversationEntity.AccessRole.TEAM_MEMBER),
    receiptMode = ConversationEntity.ReceiptMode.DISABLED,
    messageTimer = null,
    userMessageTimer = null,
    archived = false,
    archivedInstant = null,
    mlsVerificationStatus = ConversationEntity.VerificationStatus.NOT_VERIFIED,
    proteusVerificationStatus = ConversationEntity.VerificationStatus.NOT_VERIFIED,
    legalHoldStatus = ConversationEntity.LegalHoldStatus.DISABLED
)
