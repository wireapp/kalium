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
package com.wire.kalium.logic.data.client

import com.wire.kalium.logic.data.event.Event
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.di.MapperProvider
import com.wire.kalium.logic.framework.TestEvent
import com.wire.kalium.logic.framework.TestUser
import com.wire.kalium.persistence.daokaliumdb.GlobalMetadataDAO
import io.mockative.Mock
import io.mockative.any
import io.mockative.classOf
import io.mockative.eq
import io.mockative.given
import io.mockative.mock
import io.mockative.once
import io.mockative.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.test.Test

@OptIn(ExperimentalCoroutinesApi::class)
class NewClientRepositoryTest {

    @Test
    fun givenNoNewClientsInMemory_whenSavingNewClient_thenNewClientSaved() = runTest {
        val (arrangement, repository) = Arrangement()
            .withNewClients("")
            .arrange()

        val newClientEvent = TestEvent.newClient()
        val stringToSave = toStringForSaving(listOf(newClientEvent to TestUser.USER_ID))

        repository.saveNewClientEvent(newClientEvent, TestUser.USER_ID)

        verify(arrangement.globalMetadataDAO)
            .suspendFunction(arrangement.globalMetadataDAO::insertValue)
            .with(eq(stringToSave))
            .wasInvoked(exactly = once)
    }

    @Test
    fun givenSomeNewClientsInMemory_whenSavingNewClient_thenAllNewClientsSaved() = runTest {
        val oldList = listOf(TestEvent.newClient() to TestUser.USER_ID)
        val (arrangement, repository) = Arrangement()
            .withNewClients(toStringForSaving(oldList))
            .arrange()

        val newClientEvent = TestEvent.newClient().copy(id = "new_new_client_event")
        val stringToSave = toStringForSaving(oldList.plus(newClientEvent to TestUser.USER_ID))

        repository.saveNewClientEvent(newClientEvent, TestUser.USER_ID)

        verify(arrangement.globalMetadataDAO)
            .suspendFunction(arrangement.globalMetadataDAO::insertValue)
            .with(eq(stringToSave))
            .wasInvoked(exactly = once)
    }

    @Test
    fun givenSomeNewClientsInMemory_whenClearNewClientsForUser_thenNewClientsForOtherUsersKept() = runTest {
        val oldList = listOf(
            TestEvent.newClient() to TestUser.USER_ID,
            TestEvent.newClient().copy(id = "new_new_client_event") to TestUser.OTHER_USER_ID
        )
        val (arrangement, repository) = Arrangement()
            .withNewClients(toStringForSaving(oldList))
            .arrange()

        val stringToSave = toStringForSaving(oldList.filter { it.second != TestUser.USER_ID })

        repository.clearNewClientsForUser(TestUser.USER_ID)

        verify(arrangement.globalMetadataDAO)
            .suspendFunction(arrangement.globalMetadataDAO::insertValue)
            .with(eq(stringToSave))
            .wasInvoked(exactly = once)
    }

    private fun toStringForSaving(list: List<Pair<Event.User.NewClient, UserId>>) =
        Json.encodeToString(list.map { MapperProvider.clientMapper().fromNewClientEvent(it.first) to it.second })

    private class Arrangement {

        @Mock
        val globalMetadataDAO = mock(classOf<GlobalMetadataDAO>())

        var newClientRepository: NewClientRepository = NewClientDataSource(globalMetadataDAO)

        init {
            given(globalMetadataDAO)
                .suspendFunction(globalMetadataDAO::insertValue)
                .whenInvokedWith(any(), any())
                .thenReturn(Unit)
            given(globalMetadataDAO)
                .suspendFunction(globalMetadataDAO::deleteValue)
                .whenInvokedWith(any())
                .thenReturn(Unit)
            given(globalMetadataDAO)
                .suspendFunction(globalMetadataDAO::clear)
                .whenInvokedWith(any())
                .thenReturn(Unit)
        }

        fun withNewClients(result: String) = apply {
            given(globalMetadataDAO)
                .suspendFunction(globalMetadataDAO::valueByKey)
                .whenInvokedWith(eq(NewClientDataSource.NEW_CLIENTS_LIST_KEY))
                .thenReturn(result)
        }

        fun arrange() = this to newClientRepository
    }
}
