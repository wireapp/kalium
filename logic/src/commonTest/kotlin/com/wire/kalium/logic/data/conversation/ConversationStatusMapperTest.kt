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

package com.wire.kalium.logic.data.conversation

import com.wire.kalium.logic.data.id.IdMapper
import com.wire.kalium.logic.di.MapperProvider
import com.wire.kalium.network.api.authenticated.conversation.MutedStatus
import com.wire.kalium.persistence.dao.conversation.ConversationEntity
import io.mockative.Mock
import io.mockative.mock
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

class ConversationStatusMapperTest {
    @Mock
    val idMapper = mock(IdMapper::class)

    private lateinit var conversationStatusMapper: ConversationStatusMapper

    @BeforeTest
    fun setup() {
        conversationStatusMapper = ConversationStatusMapperImpl(MapperProvider.idMapper())
    }

    @Test
    fun givenAConversationModel_whenMappingToApiModel_thenTheMappingStatusesShouldBeOk() {
        val result = conversationStatusMapper.toMutedStatusApiModel(MutedConversationStatus.OnlyMentionsAndRepliesAllowed, 1649708697237L)

        assertEquals(MutedStatus.ONLY_MENTIONS_ALLOWED, result.otrMutedStatus)
        assertEquals("2022-04-11T20:24:57.237Z", result.otrMutedRef)
    }

    @Test
    fun givenAConversationModelWithArchivedField_whenMappingToApiModel_thenTheMappingStatusesShouldBeOk() {
        val isArchived = true
        val result = conversationStatusMapper.toArchivedStatusApiModel(isArchived = isArchived, 1649708697237L)

        assertEquals(isArchived, result.otrArchived)
        assertEquals("2022-04-11T20:24:57.237Z", result.otrArchivedRef)
    }

    @Test
    fun givenAConversationModel_whenMappingToDaoModel_thenTheMappingStatusesShouldBeOk() {
        val result = conversationStatusMapper.toMutedStatusDaoModel(MutedConversationStatus.OnlyMentionsAndRepliesAllowed)

        assertEquals(ConversationEntity.MutedStatus.ONLY_MENTIONS_AND_REPLIES_ALLOWED, result)
    }

}
