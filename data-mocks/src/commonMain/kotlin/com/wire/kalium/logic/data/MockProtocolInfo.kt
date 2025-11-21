/*
 * Wire
 * Copyright (C) 2025 Wire Swiss GmbH
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
package com.wire.kalium.logic.data

import com.wire.kalium.logic.data.conversation.Conversation.ProtocolInfo
import com.wire.kalium.logic.data.id.GroupID
import com.wire.kalium.logic.data.mls.CipherSuite
import com.wire.kalium.persistence.dao.conversation.ConversationEntity
import kotlinx.datetime.Instant

object MockProtocolInfo {
    fun mls(groupID: GroupID = GroupID("testGroupId")) = ProtocolInfo.MLS(
        groupID,
        ProtocolInfo.MLSCapable.GroupState.PENDING_JOIN,
        1UL,
        Instant.parse("2021-03-30T15:36:00.000Z"),
        cipherSuite = CipherSuite.MLS_128_DHKEMX25519_AES128GCM_SHA256_Ed25519
    )

    fun mlsEntity(groupID: String = "testGroupId") = ConversationEntity.ProtocolInfo.MLS(
        groupId = groupID,
        ConversationEntity.GroupState.PENDING_JOIN,
        epoch = 0UL,
        Instant.parse("2021-03-30T15:36:00.000Z"),
        cipherSuite = ConversationEntity.CipherSuite.MLS_128_DHKEMX25519_AES128GCM_SHA256_Ed25519
    )
}
