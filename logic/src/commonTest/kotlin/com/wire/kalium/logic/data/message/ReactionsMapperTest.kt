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

package com.wire.kalium.logic.data.message

import com.wire.kalium.logic.data.id.IdMapper
import com.wire.kalium.logic.data.id.QualifiedID
import com.wire.kalium.logic.data.message.reaction.MessageReaction
import com.wire.kalium.logic.data.message.reaction.ReactionsMapperImpl
import com.wire.kalium.logic.data.user.AvailabilityStatusMapper
import com.wire.kalium.logic.data.user.ConnectionState
import com.wire.kalium.logic.data.user.ConnectionStateMapper
import com.wire.kalium.logic.data.user.UserAvailabilityStatus
import com.wire.kalium.logic.data.user.type.DomainUserTypeMapper
import com.wire.kalium.logic.data.user.type.UserType
import com.wire.kalium.persistence.MessageDetailsReactions
import com.wire.kalium.persistence.dao.ConnectionEntity
import com.wire.kalium.persistence.dao.QualifiedIDEntity
import com.wire.kalium.persistence.dao.UserAvailabilityStatusEntity
import com.wire.kalium.persistence.dao.UserTypeEntity
import com.wire.kalium.persistence.dao.reaction.MessageReactionEntity
import io.mockative.Mock
import io.mockative.eq
import io.mockative.coEvery
import io.mockative.every
import io.mockative.mock
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class ReactionsMapperTest {

    @Test
    fun givenMessageDetailsReactions_whenMappingToEntity_thenReturnMessageReactionEntity() = runTest {
        // given
        val messageDetailsReactions = MessageDetailsReactions(
            emoji = "🤯",
            messageId = "messageId",
            conversationId = CONVERSATION_ID,
            userId = USER_ID,
            name = "User Name",
            handle = "userhandle",
            previewAssetId = null,
            userType = UserTypeEntity.STANDARD,
            deleted = false,
            connectionStatus = ConnectionEntity.State.ACCEPTED,
            userAvailabilityStatus = UserAvailabilityStatusEntity.NONE,
            accentId = 0
        )

        val expectedMessageReactionEntity = MessageReactionEntity(
            emoji = "🤯",
            userId = USER_ID,
            name = "User Name",
            handle = "userhandle",
            previewAssetIdEntity = null,
            userTypeEntity = UserTypeEntity.STANDARD,
            deleted = false,
            connectionStatus = ConnectionEntity.State.ACCEPTED,
            availabilityStatus = UserAvailabilityStatusEntity.NONE,
            accentId = 0
        )

        val (_, reactionsMapper) = Arrangement()
            .arrange()

        // when
        val result = reactionsMapper.fromDAOToEntity(messageReaction = messageDetailsReactions)

        // then
        assertEquals(
            expectedMessageReactionEntity,
            result
        )
    }

    @Test
    fun givenMessageReactionEntity_whenMappingToModel_thenReturnMessageReaction() = runTest {
        // given
        val messageReactionEntity = MessageReactionEntity(
            emoji = "🤯",
            userId = SELF_USER_ID_ENTITY,
            name = "Self User Name",
            handle = "selfuserhandle",
            previewAssetIdEntity = null,
            userTypeEntity = UserTypeEntity.STANDARD,
            deleted = false,
            connectionStatus = ConnectionEntity.State.ACCEPTED,
            availabilityStatus = UserAvailabilityStatusEntity.NONE,
            accentId = 0
        )

        val expectedMessageReaction = MessageReaction(
            emoji = "🤯",
            isSelfUser = true,
            userSummary = UserSummary(
                userId = SELF_USER_ID,
                userName = "Self User Name",
                userHandle = "selfuserhandle",
                userPreviewAssetId = null,
                userType = UserType.INTERNAL,
                isUserDeleted = false,
                connectionStatus = ConnectionState.ACCEPTED,
                availabilityStatus = UserAvailabilityStatus.NONE,
                accentId = 0
            )
        )

        val (_, reactionsMapper) = Arrangement()
            .withDomainUserTypeStandard()
            .withConnectionStateAccepted()
            .withAvailabilityStatusNone()
            .arrange()

        // when
        val result = reactionsMapper.fromEntityToModel(
            selfUserId = SELF_USER_ID,
            messageReactionEntity
        )

        // then
        assertEquals(
            expectedMessageReaction,
            result
        )
    }

    private class Arrangement {

        @Mock
        val idMapper = mock(IdMapper::class)

        @Mock
        val availabilityStatusMapper = mock(AvailabilityStatusMapper::class)

        @Mock
        val connectionStateMapper = mock(ConnectionStateMapper::class)

        @Mock
        val domainUserTypeMapper = mock(DomainUserTypeMapper::class)

        fun withDomainUserTypeStandard() = apply {
            every {
                domainUserTypeMapper.fromUserTypeEntity(eq(UserTypeEntity.STANDARD))
            }.returns(UserType.INTERNAL)
        }

        fun withConnectionStateAccepted() = apply {
            every {
                connectionStateMapper.fromDaoConnectionStateToUser(eq(ConnectionEntity.State.ACCEPTED))
            }.returns(ConnectionState.ACCEPTED)
        }

        fun withAvailabilityStatusNone() = apply {
            every {
                availabilityStatusMapper.fromDaoAvailabilityStatusToModel(eq(UserAvailabilityStatusEntity.NONE))
            }.returns(UserAvailabilityStatus.NONE)
        }

        fun arrange() = this to ReactionsMapperImpl(
            idMapper, availabilityStatusMapper, connectionStateMapper, domainUserTypeMapper
        )
    }

    private companion object {
        val CONVERSATION_ID = QualifiedIDEntity(
            value = "convValue",
            domain = "convDomain"
        )
        val SELF_USER_ID = QualifiedID(
            value = "selfUserValue",
            domain = "selfUserDomain"
        )
        val SELF_USER_ID_ENTITY = QualifiedIDEntity(
            value = "selfUserValue",
            domain = "selfUserDomain"
        )
        val USER_ID = QualifiedIDEntity(
            value = "userValue",
            domain = "userDomain"
        )
    }
}
