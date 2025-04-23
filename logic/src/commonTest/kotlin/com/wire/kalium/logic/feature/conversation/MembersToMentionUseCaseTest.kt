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

package com.wire.kalium.logic.feature.conversation

import com.wire.kalium.logic.data.conversation.Conversation
import com.wire.kalium.logic.data.conversation.MemberDetails
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.id.TeamId
import com.wire.kalium.logic.data.user.ConnectionState
import com.wire.kalium.logic.data.user.OtherUser
import com.wire.kalium.logic.data.user.SelfUser
import com.wire.kalium.logic.data.user.UserAssetId
import com.wire.kalium.logic.data.user.UserAvailabilityStatus
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.data.user.UserRepository
import com.wire.kalium.logic.data.user.type.UserType
import com.wire.kalium.logic.test_util.TestKaliumDispatcher
import io.mockative.any
import io.mockative.coEvery
import io.mockative.mock
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

class MembersToMentionUseCaseTest {

        private val selfUserId = SELF_USER.id

        private val observeConversationMembers = mock(ObserveConversationMembersUseCase::class)

    private lateinit var membersToMention: MembersToMentionUseCase

    @BeforeTest
    fun setup() = runBlocking {
        membersToMention = MembersToMentionUseCase(observeConversationMembers, selfUserId = selfUserId, TestKaliumDispatcher)
        coEvery {
            observeConversationMembers.invoke(any())
        }.returns(flowOf(members))
    }

    @Test
    fun givenAListOfMembers_whenRequestingMembersToMentionWithAnEmptySearchQuery_thenReturnAllConversationMembers() =
        runTest(TestKaliumDispatcher.main) {
            val searchQuery = ""

            val result = membersToMention(CONVERSATION_ID, searchQuery)

            assertEquals(members, result)
        }

    @Test
    fun givenAListOfMembers_whenRequestingMembersToMentionWithWhiteSpaceSearchQuery_thenReturnAnEmptyList() =
        runTest(TestKaliumDispatcher.main) {
            val searchQuery = " "

            val result = membersToMention(CONVERSATION_ID, searchQuery)

            assertEquals(true, result.isEmpty())
        }

    @Test
    fun givenAListOfMembers_whenRequestingMembersToMentionWithSearchQueryThatDoesNotExistInTheList_thenReturnAnEmptyList() =
        runTest(TestKaliumDispatcher.main) {
            val searchQuery = "randomName9-0("

            val result = membersToMention(CONVERSATION_ID, searchQuery)

            assertEquals(true, result.isEmpty())
        }

    @Test
    fun givenAListOfMembers_whenRequestingMembersToMentionWithValidSearchQuery_thenReturnSortedMembersToMention() =
        runTest(TestKaliumDispatcher.main) {
            val searchQuery = "KillUa"

            val result = membersToMention(CONVERSATION_ID, searchQuery)

            assertEquals(5, result.size)
            assertEquals(members[3], result.first())
            assertEquals(members.last(), result[1])
            assertEquals(members[4], result[2])
            assertEquals(members[1], result[3])
            assertEquals(members.first(), result[4])

        }

    companion object {
        private const val DOMAIN = "some_domain"
        val CONVERSATION_ID = ConversationId("conversation-id", DOMAIN)
        val SELF_USER = SelfUser(
            id = UserId("slef_id", DOMAIN),
            name = "some_name",
            handle = "some_handle",
            email = "some_email",
            phone = null,
            accentId = 1,
            teamId = null,
            connectionStatus = ConnectionState.ACCEPTED,
            previewPicture = UserAssetId("value1", DOMAIN),
            completePicture = UserAssetId("value2", DOMAIN),
            userType = UserType.INTERNAL,
            availabilityStatus = UserAvailabilityStatus.NONE,
            supportedProtocols = null,
        )
        private val OTHER_USER = OtherUser(
            UserId(value = "other-id", DOMAIN),
            name = "Feitan k",
            handle = "FeitanKillua",
            email = "otherEmail",
            phone = "otherPhone",
            accentId = 0,
            teamId = TeamId("otherTeamId"),
            connectionStatus = ConnectionState.ACCEPTED,
            previewPicture = UserAssetId("value", "domain"),
            completePicture = UserAssetId("value", "domain"),
            availabilityStatus = UserAvailabilityStatus.AVAILABLE,
            userType = UserType.INTERNAL,
            botService = null,
            deleted = false,
            defederated = false,
            isProteusVerified = false,
            supportedProtocols = null
        )

        val members = listOf(
            MemberDetails(OTHER_USER, Conversation.Member.Role.Member),
            MemberDetails(OTHER_USER.copy(name = "AckermanKillua", handle = "ack_"), Conversation.Member.Role.Member),
            MemberDetails(OTHER_USER.copy(name = "Gon Fritz", handle = "g_zoldyk"), Conversation.Member.Role.Member),
            MemberDetails(OTHER_USER.copy(name = "Killua Zoldyk", handle = "k_z"), Conversation.Member.Role.Member),
            MemberDetails(OTHER_USER.copy(name = "Zoldyk", handle = "Killua_z"), Conversation.Member.Role.Member),
            MemberDetails(OTHER_USER.copy(name = "Zoldyk Killua", handle = "z_k"), Conversation.Member.Role.Member),
        )
    }
}
