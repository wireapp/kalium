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
package com.wire.kalium.logic.data.conversation.mls

import com.wire.kalium.logic.data.id.QualifiedID
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.network.tools.KtxSerializer
import com.wire.kalium.persistence.dao.pendingaction.PendingActionType

internal object OneOnOneResolutionPendingAction {
    val actionType = PendingActionType.RESOLVE_ONE_ON_ONE_CONVERSATION

    fun actionKey(userId: UserId): String = "${userId.value}@${userId.domain}"

    fun userIdFromActionKey(actionKey: String): UserId? {
        val separatorIndex = actionKey.indexOf('@')
        if (separatorIndex <= 0 || separatorIndex == actionKey.lastIndex) return null
        val value = actionKey.substring(0, separatorIndex)
        val domain = actionKey.substring(separatorIndex + 1)
        return QualifiedID(value = value, domain = domain)
    }

    fun payload(userId: UserId): String =
        KtxSerializer.json.encodeToString(QualifiedID.serializer(), userId)
}
