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

package com.wire.kalium.logic.data.user

import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.id.TeamId
import com.wire.kalium.logic.data.user.type.UserType
import com.wire.kalium.logic.framework.TestUser
import com.wire.kalium.persistence.dao.BotIdEntity
import com.wire.kalium.persistence.dao.ConnectionEntity
import com.wire.kalium.persistence.dao.QualifiedIDEntity
import com.wire.kalium.persistence.dao.SupportedProtocolEntity
import com.wire.kalium.persistence.dao.UserAvailabilityStatusEntity
import com.wire.kalium.persistence.dao.UserDetailsEntity
import com.wire.kalium.persistence.dao.UserTypeEntity
import com.wire.kalium.util.time.UNIX_FIRST_DATE
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals

class UserMapperTest {

    @Test
    fun givenUserProfileDTOAndUserTypeEntity_whenMappingFromApiResponse_thenDaoModelIsReturned() = runTest {
        // Given
        val givenResponse = TestUser.USER_PROFILE_DTO
        val givenUserTypeEntity = UserTypeEntity.EXTERNAL
        val expectedResult = TestUser.ENTITY.copy(
            phone = null, // UserProfileDTO doesn't contain the phone
            connectionStatus = ConnectionEntity.State.NOT_CONNECTED
        )
        val (_, userMapper) = Arrangement().arrange()
        // When
        val result = userMapper.fromUserProfileDtoToUserEntity(
            givenResponse,
            expectedResult.connectionStatus,
            givenUserTypeEntity
        )
        // Then
        assertEquals(expectedResult, result)
    }

    @Test
    fun givenUserDetailsEntity_whenMappingToSelfUser_thenSelfUserWithProperDataIsReturned() = runTest {
        // Given
        val givenUserDetailsEntity = UserDetailsEntity(
            id = TestUser.ENTITY_ID,
            name = "username",
            handle = "handle",
            email = "email",
            phone = "phone",
            accentId = 0,
            team = "teamId",
            connectionStatus = ConnectionEntity.State.ACCEPTED,
            previewAssetId = QualifiedIDEntity("value1", "domain"),
            completeAssetId = QualifiedIDEntity("value2", "domain"),
            availabilityStatus = UserAvailabilityStatusEntity.NONE,
            supportedProtocols = setOf(SupportedProtocolEntity.PROTEUS, SupportedProtocolEntity.MLS),
            userType = UserTypeEntity.EXTERNAL,
            botService = null,
            deleted = false,
            expiresAt = Instant.UNIX_FIRST_DATE,
            defederated = false,
            isProteusVerified = false,
            activeOneOnOneConversationId = null,
            isUnderLegalHold = false,
        )
        val expectedResult = SelfUser(
            TestUser.USER_ID,
            name = "username",
            handle = "handle",
            email = "email",
            phone = "phone",
            accentId = 0,
            teamId = TeamId("teamId"),
            connectionStatus = ConnectionState.ACCEPTED,
            previewPicture = UserAssetId("value1", "domain"),
            completePicture = UserAssetId("value2", "domain"),
            availabilityStatus = UserAvailabilityStatus.NONE,
            supportedProtocols = setOf(SupportedProtocol.PROTEUS, SupportedProtocol.MLS),
            userType = UserType.EXTERNAL,
            expiresAt = Instant.UNIX_FIRST_DATE,
            isUnderLegalHold = false,
        )
        val (_, userMapper) = Arrangement().arrange()
        // When
        val result = userMapper.fromUserDetailsEntityToSelfUser(givenUserDetailsEntity)
        // Then
        assertEquals(expectedResult, result)
    }

    @Test
    fun givenUserDetailsEntity_whenMappingToOtherUser_thenOtherUserWithProperDataIsReturned() = runTest {
        // Given
        val givenUserDetailsEntity = UserDetailsEntity(
            id = TestUser.ENTITY_ID,
            name = "username",
            handle = "handle",
            email = "email",
            phone = "phone",
            accentId = 0,
            team = "teamId",
            connectionStatus = ConnectionEntity.State.ACCEPTED,
            previewAssetId = QualifiedIDEntity("value1", "domain"),
            completeAssetId = QualifiedIDEntity("value2", "domain"),
            availabilityStatus = UserAvailabilityStatusEntity.NONE,
            supportedProtocols = setOf(SupportedProtocolEntity.PROTEUS, SupportedProtocolEntity.MLS),
            userType = UserTypeEntity.EXTERNAL,
            botService = BotIdEntity("botid", "provider"),
            deleted = false,
            expiresAt = Instant.UNIX_FIRST_DATE,
            defederated = false,
            isProteusVerified = false,
            activeOneOnOneConversationId = QualifiedIDEntity("convid", "domain"),
            isUnderLegalHold = false,
        )
        val expectedResult = OtherUser(
            TestUser.USER_ID,
            name = "username",
            handle = "handle",
            email = "email",
            phone = "phone",
            accentId = 0,
            teamId = TeamId("teamId"),
            connectionStatus = ConnectionState.ACCEPTED,
            previewPicture = UserAssetId("value1", "domain"),
            completePicture = UserAssetId("value2", "domain"),
            userType = UserType.EXTERNAL,
            availabilityStatus = UserAvailabilityStatus.NONE,
            supportedProtocols = setOf(SupportedProtocol.PROTEUS, SupportedProtocol.MLS),
            botService = BotService("botid", "provider"),
            deleted = false,
            expiresAt = Instant.UNIX_FIRST_DATE,
            defederated = false,
            isProteusVerified = false,
            activeOneOnOneConversationId = ConversationId("convid", "domain"),
            isUnderLegalHold = false,
        )
        val (_, userMapper) = Arrangement().arrange()
        // When
        val result = userMapper.fromUserDetailsEntityToOtherUser(givenUserDetailsEntity)
        // Then
        assertEquals(expectedResult, result)
    }

    private class Arrangement {

        private val userMapper = UserMapperImpl()

        fun arrange() = this to userMapper
    }
}
