package com.wire.kalium.logic.data.id

import com.wire.kalium.network.api.base.authenticated.client.DeviceTypeDTO
import com.wire.kalium.network.api.base.authenticated.client.SimpleClientResponse
import com.wire.kalium.network.api.base.model.UserAssetDTO
import com.wire.kalium.network.api.base.model.UserAssetTypeDTO
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
