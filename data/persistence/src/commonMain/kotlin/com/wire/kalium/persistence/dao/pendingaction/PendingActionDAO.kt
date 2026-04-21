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
package com.wire.kalium.persistence.dao.pendingaction

import io.mockative.Mockable

enum class PendingActionType(val dbValue: String) {
    RESOLVE_ONE_ON_ONE_CONVERSATION("resolve_one_on_one_conversation")
}

data class PendingActionEntity(
    val actionKey: String,
    val payload: String?,
    val createdAt: Long,
)

@Mockable
interface PendingActionDAO {
    suspend fun upsert(actionType: PendingActionType, actionKey: String, payload: String?, createdAt: Long)
    suspend fun getByActionType(actionType: PendingActionType): List<PendingActionEntity>
    suspend fun deleteByActionTypeAndKeys(actionType: PendingActionType, actionKeys: List<String>)
    suspend fun deleteByActionType(actionType: PendingActionType)
}
