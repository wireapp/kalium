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

package com.wire.kalium.logic.data.id

import com.wire.kalium.network.api.authenticated.client.DeviceTypeDTO
import com.wire.kalium.network.api.authenticated.client.SimpleClientResponse
import com.wire.kalium.network.api.model.UserAssetDTO
import com.wire.kalium.network.api.model.UserAssetTypeDTO
import com.wire.kalium.protobuf.messages.QualifiedConversationId
import kotlin.test.Test
import kotlin.test.assertEquals

class IdMapperTest {

    private val idMapper = IdMapperImpl()

    @Test
    fun givenAQualifiedId_whenMappingToApiModel_thenTheFieldsShouldBeMappedCorrectly() {
        val qualifiedID = QualifiedID("value", "domain")

        val networkID = qualifiedID.toApi()

        assertEquals(qualifiedID.value, networkID.value)
        assertEquals(qualifiedID.domain, networkID.domain)
    }

    @Test
    fun givenAnAPIQualifiedId_whenMappingFromApiModel_thenTheFieldsShouldBeMappedCorrectly() {
        val networkID = NetworkQualifiedId("value", "domain")

        val qualifiedID = networkID.toModel()

        assertEquals(networkID.value, qualifiedID.value)
        assertEquals(networkID.domain, qualifiedID.domain)
    }

    @Test
    fun givenASimpleClientResponse_whenMappingFromSimpleClientResponse_thenTheIDShouldBeMappedCorrectly() {
        val simpleClientResponse = SimpleClientResponse("an ID", DeviceTypeDTO.Desktop)

        val clientID = idMapper.fromSimpleClientResponse(simpleClientResponse)

        assertEquals(simpleClientResponse.id, clientID.value)
    }

    @Test
    fun givenProtoQualifiedConversationId_whenMappingFromProtoModel_thenTheIDShouldBeMappedCorrectly() {
        val qualifiedConversationId = QualifiedConversationId("Test", "Test")

        val conversationId = idMapper.fromProtoModel(qualifiedConversationId)

        assertEquals(qualifiedConversationId.id, conversationId.value)
        assertEquals(qualifiedConversationId.domain, conversationId.domain)
    }

    @Test
    fun givenConversationId_whenMappingToProtoModel_thenTheIDShouldBeMappedCorrectly() {
        val conversationId = ConversationId("Test", "Test")

        val qualifiedConversationId = idMapper.toProtoModel(conversationId)

        assertEquals(qualifiedConversationId.id, conversationId.value)
        assertEquals(qualifiedConversationId.domain, conversationId.domain)
    }

    @Test
    fun givenNetworkAssetAndDomain_whenMappingToQualifiedAssetId_thenShouldReturnACorrectAssetId() {
        val domain = "domain"
        val assetDTO = UserAssetDTO("key", null, UserAssetTypeDTO.IMAGE)
        val qualifiedAssetID = assetDTO.toModel(domain)

        assertEquals(qualifiedAssetID.value, assetDTO.key)
        assertEquals(qualifiedAssetID.domain, domain)
    }

    @Test
    fun givenNetworkAssetAndDomain_whenMappingToQualifiedAssetId_thenShouldReturnACorrectAssetIdEntity() {
        val domain = "domain"
        val assetDTO = UserAssetDTO("key", null, UserAssetTypeDTO.IMAGE)

        val qualifiedIDEntity = assetDTO.toDao(domain)

        assertEquals(qualifiedIDEntity.value, assetDTO.key)
        assertEquals(qualifiedIDEntity.domain, domain)
    }

}
