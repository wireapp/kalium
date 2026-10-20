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

package com.wire.kalium.logic.feature.connection

/** Compatibility facade retaining only its own fresh extension and its constructor captures. */
public class ConnectionScope internal constructor(private val api: LegacyConnectionApi) {
    public val sendConnectionRequest: SendConnectionRequestUseCase
        get() = api.connectionSendConnectionRequest

    public val acceptConnectionRequest: AcceptConnectionRequestUseCase
        get() = api.connectionAcceptConnectionRequest

    public val cancelConnectionRequest: CancelConnectionRequestUseCase
        get() = api.connectionCancelConnectionRequest

    public val ignoreConnectionRequest: IgnoreConnectionRequestUseCase
        get() = api.connectionIgnoreConnectionRequest

    internal val markConnectionRequestAsNotified: MarkConnectionRequestAsNotifiedUseCase
        get() = api.connectionMarkConnectionRequestAsNotified

    public val blockUser: BlockUserUseCase
        get() = api.connectionBlockUser

    public val unblockUser: UnblockUserUseCase
        get() = api.connectionUnblockUser
}
