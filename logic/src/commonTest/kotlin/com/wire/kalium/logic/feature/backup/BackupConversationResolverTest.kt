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
package com.wire.kalium.logic.feature.backup

import com.wire.kalium.common.error.CoreFailure
import com.wire.kalium.common.functional.Either
import com.wire.kalium.logic.data.MockConversation
import com.wire.kalium.logic.data.conversation.Conversation
import com.wire.kalium.logic.data.conversation.ConversationDetails
import com.wire.kalium.logic.data.conversation.CreateConversationParam
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.feature.conversation.createconversation.ConversationCreationResult
import com.wire.kalium.logic.feature.conversation.createconversation.CreateRegularGroupUseCase
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class BackupConversationResolverTest {

    @Test
    fun givenMatchingCellsBackupConversationExists_whenResolving_thenReusesExistingConversation() = runTest {
        val existingConversationId = ConversationId("existing-backup", "domain")
        val createRegularGroup = RecordingCreateRegularGroupUseCase()
        val resolver = arrangement(
            createRegularGroup = createRegularGroup,
            conversations = listOf(regularGroup(existingConversationId, BACKUP_CONVERSATION_NAME, wireCell = "cell-name")),
        )

        val result = resolver.getOrCreateBackupConversation()

        assertEquals(existingConversationId, assertIs<Either.Right<ConversationId>>(result).value)
        assertFalse(createRegularGroup.wasCalled)
    }

    @Test
    fun givenMatchingNonCellsBackupConversationExists_whenResolving_thenCreatesCellsBackupConversation() = runTest {
        val createdConversationId = ConversationId("created-backup", "domain")
        val createRegularGroup = RecordingCreateRegularGroupUseCase(
            result = ConversationCreationResult.Success(MockConversation.group(id = createdConversationId))
        )
        val resolver = arrangement(
            createRegularGroup = createRegularGroup,
            conversations = listOf(regularGroup(ConversationId("non-cells", "domain"), BACKUP_CONVERSATION_NAME, wireCell = null)),
        )

        val result = resolver.getOrCreateBackupConversation()

        assertEquals(createdConversationId, assertIs<Either.Right<ConversationId>>(result).value)
        assertTrue(createRegularGroup.wasCalled)
    }

    @Test
    fun givenNoMatchingBackupConversation_whenResolving_thenCreatesSelfOnlyCellsConversation() = runTest {
        val createRegularGroup = RecordingCreateRegularGroupUseCase()
        val resolver = arrangement(createRegularGroup = createRegularGroup)

        val result = resolver.getOrCreateBackupConversation()

        assertIs<Either.Right<ConversationId>>(result)
        assertEquals(BACKUP_CONVERSATION_NAME, createRegularGroup.name)
        assertEquals(emptyList(), createRegularGroup.userIdList)
        assertEquals(CreateConversationParam.Protocol.MLS, createRegularGroup.options?.protocol)
        assertEquals(Conversation.defaultGroupAccess, createRegularGroup.options?.access)
        assertEquals(Conversation.defaultGroupAccessRoles, createRegularGroup.options?.accessRole)
        assertTrue(createRegularGroup.options?.wireCellEnabled == true)
        assertFalse(createRegularGroup.options?.skipCreator == true)
    }

    @Test
    fun givenConversationCreationFails_whenResolving_thenReturnsFailure() = runTest {
        val failure = CoreFailure.Unknown(null)
        val resolver = arrangement(
            createRegularGroup = RecordingCreateRegularGroupUseCase(
                result = ConversationCreationResult.UnknownFailure(failure)
            )
        )

        val result = resolver.getOrCreateBackupConversation()

        assertEquals(failure, assertIs<Either.Left<*>>(result).value)
    }

    private fun arrangement(
        createRegularGroup: RecordingCreateRegularGroupUseCase = RecordingCreateRegularGroupUseCase(),
        conversations: List<ConversationDetails> = emptyList(),
    ) = BackupConversationResolverImpl(
        selfUserId = SELF_USER_ID,
        createRegularGroup = createRegularGroup,
        conversationListDetailsProvider = { conversations },
        defaultProtocol = { CreateConversationParam.Protocol.MLS },
    )

    private class RecordingCreateRegularGroupUseCase(
        private val result: ConversationCreationResult = ConversationCreationResult.Success(
            MockConversation.group(id = ConversationId("created-backup", "domain"))
        ),
    ) : CreateRegularGroupUseCase {
        var wasCalled = false
        var name: String? = null
        var userIdList: List<UserId>? = null
        var options: CreateConversationParam? = null

        override suspend fun invoke(
            name: String,
            userIdList: List<UserId>,
            options: CreateConversationParam,
        ): ConversationCreationResult {
            wasCalled = true
            this.name = name
            this.userIdList = userIdList
            this.options = options
            return result
        }
    }

    private companion object {
        val SELF_USER_ID = UserId("self-user", "domain")
        const val BACKUP_CONVERSATION_NAME = "auto_backup_self-user"

        fun regularGroup(
            id: ConversationId,
            name: String,
            wireCell: String?,
        ): ConversationDetails.Group.Regular =
            ConversationDetails.Group.Regular(
                conversation = MockConversation.group(id = id).copy(name = name),
                isSelfUserMember = true,
                selfRole = Conversation.Member.Role.Admin,
                wireCell = wireCell,
            )
    }
}
