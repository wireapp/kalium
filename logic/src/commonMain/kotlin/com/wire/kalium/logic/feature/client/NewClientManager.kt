/*
 * Wire
 * Copyright (C) 2023 Wire Swiss GmbH
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
package com.wire.kalium.logic.feature.client

import com.wire.kalium.logic.data.client.Client
import com.wire.kalium.logic.data.event.Event
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.di.MapperProvider
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.flow.emptyFlow

object NewClientManagerImpl : NewClientManager {

    private val mapper by lazy { MapperProvider.clientMapper() }

    private val newClients =
        Channel<Pair<Client, UserId>>(capacity = Channel.CONFLATED) { emptyFlow<Pair<Client, UserId>>() }

    override suspend fun observeNewClients(): Flow<Pair<Client, UserId>> {
        return newClients.consumeAsFlow()
    }

    override suspend fun scheduleNewClientEvent(newClientEvent: Event.User.NewClient, userId: UserId) {
        newClients.send(mapper.fromNewClientEvent(newClientEvent) to userId)
    }

}

interface NewClientManager {
    suspend fun observeNewClients(): Flow<Pair<Client, UserId>>
    suspend fun scheduleNewClientEvent(newClientEvent: Event.User.NewClient, userId: UserId)
}
