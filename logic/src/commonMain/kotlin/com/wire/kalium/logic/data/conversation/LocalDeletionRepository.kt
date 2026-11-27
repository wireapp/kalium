/*
 * Wire
 * Copyright (C) 2026 Wire Swiss GmbH
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.wire.kalium.logic.data.conversation

import com.wire.kalium.common.error.CoreFailure
import com.wire.kalium.common.error.wrapStorageRequest
import com.wire.kalium.common.functional.Either
import com.wire.kalium.common.functional.map
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.id.toDao
import com.wire.kalium.logic.data.id.toModel
import com.wire.kalium.persistence.dao.conversation.ConversationDAO
import com.wire.kalium.persistence.dao.pendingaction.PendingActionDAO
import com.wire.kalium.persistence.dao.pendingaction.PendingActionType
import kotlin.time.Clock

internal interface LocalDeletionRepository {
    suspend fun markPending(conversationId: ConversationId): Either<CoreFailure, Unit>
    suspend fun getPending(): Either<CoreFailure, List<ConversationId>>
    suspend fun acknowledge(conversationId: ConversationId): Either<CoreFailure, Unit>
}

internal class LocalDeletionRepositoryImpl(
    private val conversationDAO: ConversationDAO,
    private val pendingActionDAO: PendingActionDAO,
) : LocalDeletionRepository {
    override suspend fun markPending(conversationId: ConversationId): Either<CoreFailure, Unit> = wrapStorageRequest {
        conversationDAO.markConversationDeletedLocallyPending(
            conversationId.toDao(),
            TYPE,
            Clock.System.now().toEpochMilliseconds(),
        )
    }

    override suspend fun getPending(): Either<CoreFailure, List<ConversationId>> = wrapStorageRequest {
        pendingActionDAO.getByActionType(TYPE)
    }.map { actions -> actions.map { it.qualifiedId.toModel() } }

    override suspend fun acknowledge(conversationId: ConversationId): Either<CoreFailure, Unit> = wrapStorageRequest {
        pendingActionDAO.deleteByActionTypeAndIds(TYPE, listOf(conversationId.toDao()))
    }

    private companion object {
        val TYPE = PendingActionType.PENDING_LOCAL_DELETION
    }
}
