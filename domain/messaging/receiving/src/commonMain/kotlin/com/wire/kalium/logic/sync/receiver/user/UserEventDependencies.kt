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
package com.wire.kalium.logic.sync.receiver.user

import com.wire.kalium.common.error.CoreFailure
import com.wire.kalium.common.error.StorageFailure
import com.wire.kalium.common.functional.Either
import com.wire.kalium.cryptography.CryptoTransactionContext
import com.wire.kalium.logic.data.event.Event
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.user.ConnectionState
import com.wire.kalium.logic.data.user.UserId

/** User operations that may require remote data while applying incoming user events. */
public interface UserEventRepository {
    public suspend fun updateUserFromEvent(event: Event.User.Update): Either<CoreFailure, Unit>
    public suspend fun markUserDeletedForEvent(userId: UserId): Either<CoreFailure, Unit>
    public suspend fun fetchUserForConnectionEvent(userId: UserId): Either<CoreFailure, ConnectionUserFetchResult>
}

public interface NewClientEventRepository {
    public suspend fun saveNewClientEvent(event: Event.User.NewClient): Either<CoreFailure, Unit>
}

public enum class ConnectionUserFetchResult {
    SUCCESS,
    NOT_FOUND,
}

public interface NewConnectionEventRepository {
    public suspend fun getConnectionStatusForEvent(
        conversationId: ConversationId
    ): Either<StorageFailure, ConnectionState>

    public suspend fun insertConnectionFromEvent(
        transactionContext: CryptoTransactionContext,
        event: Event.User.NewConnection,
    ): Either<CoreFailure, Unit>
}

public fun interface SessionRefreshRepository {
    public suspend fun refreshSession(): Either<CoreFailure, Unit>
}
