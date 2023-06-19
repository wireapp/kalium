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

/**
 * This singleton allow us to have a bridge between [com.wire.kalium.logic.sync.receiver.UserEventReceiver]
 * and [com.wire.kalium.logic.feature.client.ObserveNewClientsUseCase],
 * so we can observe [Event.User.NewClient] without saving it into the DB.
 */
internal object NewClientManagerImpl : NewClientManager {

    private val mapper by lazy { MapperProvider.clientMapper() }

    private val newClients =
        Channel<Pair<Client, UserId>>(capacity = Channel.CONFLATED) { emptyFlow<Pair<Client, UserId>>() }

    /**
     * Observes all the new Clients for the users logged in on device.
     * @return Flow of Pair, where [Pair.first] is [Client] that was registered
     * and [Pair.second] is [UserId] of the user for which that Client was registered.
     */
    override suspend fun observeNewClients(): Flow<Pair<Client, UserId>> {
        return newClients.consumeAsFlow()
    }

    /**
     * Send a new event into [newClients] Channel to inform that the new Client was registered.
     * Use this method in [com.wire.kalium.logic.sync.receiver.UserEventReceiver] or where ever [Event.User.NewClient] come into.
     */
    override suspend fun scheduleNewClientEvent(newClientEvent: Event.User.NewClient, userId: UserId) {
        newClients.send(newClientEvent.client to userId)
    }

}

internal interface NewClientManager {
    suspend fun observeNewClients(): Flow<Pair<Client, UserId>>
    suspend fun scheduleNewClientEvent(newClientEvent: Event.User.NewClient, userId: UserId)
}
