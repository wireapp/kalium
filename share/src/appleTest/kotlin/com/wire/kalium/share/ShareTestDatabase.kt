/*
 * Wire
 * Copyright (C) 2026 Wire Swiss GmbH
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

package com.wire.kalium.share

import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.persistence.TestUserDatabase
import com.wire.kalium.persistence.dao.ConnectionEntity
import com.wire.kalium.persistence.dao.QualifiedIDEntity
import com.wire.kalium.persistence.dao.SupportedProtocolEntity
import com.wire.kalium.persistence.dao.UserAvailabilityStatusEntity
import com.wire.kalium.persistence.dao.UserEntity
import com.wire.kalium.persistence.dao.UserTypeEntity
import com.wire.kalium.persistence.dao.conversation.ConversationEntity
import com.wire.kalium.persistence.dao.member.MemberEntity
import com.wire.kalium.persistence.db.UserDatabaseBuilder
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlin.time.Instant

// An account database for share tests, with the self user and conversations they write.

internal const val DOMAIN = "wire.com"
internal val SELF = UserId("self", DOMAIN)
internal val OTHER = UserId("other", DOMAIN)
internal val T1 = Instant.parse("2026-10-13T10:00:00Z")
internal val T2 = Instant.parse("2026-10-13T11:00:00Z")
internal val T3 = Instant.parse("2026-10-13T12:00:00Z")

internal fun UserId.toEntity() = QualifiedIDEntity(value, domain)

internal fun user(id: UserId, connection: ConnectionEntity.State, activeOneOnOne: String? = null) = UserEntity(
    id = id.toEntity(),
    name = id.value,
    handle = id.value,
    email = null,
    phone = null,
    accentId = 1,
    team = null,
    connectionStatus = connection,
    previewAssetId = null,
    completeAssetId = null,
    availabilityStatus = UserAvailabilityStatusEntity.NONE,
    userType = UserTypeEntity.STANDARD,
    botService = null,
    deleted = false,
    expiresAt = null,
    defederated = false,
    supportedProtocols = setOf(SupportedProtocolEntity.PROTEUS),
    activeOneOnOneConversationId = activeOneOnOne?.let { QualifiedIDEntity(it, DOMAIN) },
)

internal fun conversationEntity(id: QualifiedIDEntity, type: ConversationEntity.Type, lastModified: Instant) = ConversationEntity(
    id = id,
    name = id.value,
    type = type,
    teamId = null,
    protocolInfo = ConversationEntity.ProtocolInfo.Proteus,
    creatorId = SELF.value,
    lastNotificationDate = null,
    lastModifiedDate = lastModified,
    lastReadDate = T1,
    access = emptyList(),
    accessRole = emptyList(),
    receiptMode = ConversationEntity.ReceiptMode.DISABLED,
    messageTimer = null,
    userMessageTimer = null,
    archived = false,
    archivedInstant = null,
    mlsVerificationStatus = ConversationEntity.VerificationStatus.NOT_VERIFIED,
    proteusVerificationStatus = ConversationEntity.VerificationStatus.NOT_VERIFIED,
    legalHoldStatus = ConversationEntity.LegalHoldStatus.DISABLED,
    channelAccess = null,
    channelAddPermission = null,
    wireCell = null,
    historySharingRetentionSeconds = 0,
)

@Suppress("LongParameterList")
internal suspend fun conversation(
    database: UserDatabaseBuilder,
    id: String,
    type: ConversationEntity.Type,
    lastModified: Instant,
    selfIsMember: Boolean = true,
    other: UserId = OTHER,
    change: ConversationEntity.() -> ConversationEntity = { this },
) {
    val conversationId = QualifiedIDEntity(id, DOMAIN)
    database.conversationDAO.insertConversation(conversationEntity(conversationId, type, lastModified).change())
    database.memberDAO.insertMembersWithQualifiedId(
        listOfNotNull(
            MemberEntity(SELF.toEntity(), MemberEntity.Role.Admin).takeIf { selfIsMember },
            MemberEntity(other.toEntity(), MemberEntity.Role.Member),
        ),
        conversationId
    )
}

internal suspend fun TestScope.withDatabase(block: suspend (UserDatabaseBuilder) -> Unit) {
    val database = TestUserDatabase(SELF.toEntity(), StandardTestDispatcher(testScheduler))
    try {
        block(database.builder)
    } finally {
        database.builder.close()
        database.delete()
    }
}
