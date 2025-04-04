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
package com.wire.kalium.logic.data.conversation.channel

import com.wire.kalium.common.functional.isLeft
import com.wire.kalium.common.functional.isRight
import com.wire.kalium.logic.data.conversation.ConversationDetails
import com.wire.kalium.logic.data.conversation.ConversationRepositoryTest.Companion.CONVERSATION_ID
import com.wire.kalium.logic.test_util.TestNetworkException
import com.wire.kalium.network.api.authenticated.conversation.ChannelAddUserPermissionTypeDTO
import com.wire.kalium.network.api.authenticated.conversation.UpdateChannelAddUserPermissionResponse
import com.wire.kalium.network.api.authenticated.conversation.channel.ChannelAddUserPermissionDTO
import com.wire.kalium.network.api.authenticated.notification.EventContentDTO
import com.wire.kalium.network.api.base.authenticated.conversation.ConversationApi
import com.wire.kalium.network.utils.NetworkResponse
import com.wire.kalium.persistence.dao.conversation.ConversationDAO
import io.ktor.http.HttpStatusCode
import io.mockative.Mock
import io.mockative.any
import io.mockative.coEvery
import io.mockative.coVerify
import io.mockative.mock
import io.mockative.once
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Clock
import kotlin.test.Test
import kotlin.test.assertTrue

class ChannelRepositoryTest {

    @Test
    fun givenAPIError_whenUpdateAddUserPermissionIsCalled_thenDoNothing() = runTest {
        val error = NetworkResponse.Error(TestNetworkException.generic)

        val (arrange, channelRepository) = Arrangement()
            .withUpdateChannelAddUserPermissionRemotelyReturning(error)
            .arrange()

        val result = channelRepository.updateAddUserPermission(
            CONVERSATION_ID,
            ConversationDetails.Group.Channel.ChannelAddUserPermission.ADMINS
        )

        coVerify {
            arrange.conversationDAO.updateChannelAddUserPermission(any(), any())
        }.wasNotInvoked()
        assertTrue { result.isLeft() }
    }


    @Test
    fun givenPermissionUnchanged_whenUpdateAddUserPermissionIsCalled_thenDoNothing() = runTest {
        val permissionUnchanged =
            NetworkResponse.Success(
                value = UpdateChannelAddUserPermissionResponse.AddUserPermissionUnchanged,
                mapOf(),
                HttpStatusCode.OK.value
            )

        val (arrange, channelRepository) = Arrangement()
            .withUpdateChannelAddUserPermissionRemotelyReturning(permissionUnchanged)
            .arrange()

        val result = channelRepository.updateAddUserPermission(
            CONVERSATION_ID,
            ConversationDetails.Group.Channel.ChannelAddUserPermission.ADMINS
        )

        coVerify {
            arrange.conversationDAO.updateChannelAddUserPermission(any(), any())
        }.wasNotInvoked()
        assertTrue { result.isRight() }
    }

    @Test
    fun givenPermissionChanged_whenUpdateChannelAddUserPermissionIsCalled_thenUpdateStateLocallyUser() = runTest {
        val permissionUpdated = NetworkResponse.Success(
            value = UpdateChannelAddUserPermissionResponse.AddUserPermissionUpdated(
                EventContentDTO.Conversation.ChannelAddUserPermissionUpdate(
                    "conversationId",
                    com.wire.kalium.network.api.model.ConversationId("conversationId", "domain"),
                    ChannelAddUserPermissionDTO(ChannelAddUserPermissionTypeDTO.ADMINS),
                    from = "userId",
                    qualifiedFrom = com.wire.kalium.network.api.model.UserId("from_id", "from_domain"),
                    time = Clock.System.now()
                )
            ), mapOf(), HttpStatusCode.OK.value
        )

        val (arrange, channelRepository) = Arrangement()
            .withUpdateChannelAddUserPermissionRemotelyReturning(permissionUpdated)
            .arrange()

        val result = channelRepository.updateAddUserPermission(
            CONVERSATION_ID,
            ConversationDetails.Group.Channel.ChannelAddUserPermission.ADMINS
        )

        coVerify {
            arrange.conversationDAO.updateChannelAddUserPermission(any(), any())
        }.wasInvoked(exactly = once)
        assertTrue { result.isRight() }
    }

    private class Arrangement {
        @Mock
        val conversationApi: ConversationApi = mock(ConversationApi::class)

        @Mock
        val conversationDAO: ConversationDAO = mock(ConversationDAO::class)

        suspend fun withUpdateChannelAddUserPermissionRemotelyReturning(result: NetworkResponse<UpdateChannelAddUserPermissionResponse>) =
            apply {
                coEvery {
                    conversationApi.updateChannelAddUserPermission(any(), any())
                }.returns(result)
            }

        fun arrange() = this to ChannelDataSource(conversationDAO, conversationApi)
    }
}
