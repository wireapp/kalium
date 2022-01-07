package com.wire.kalium.logic.data.id

import com.wire.kalium.network.api.user.client.SimpleClientResponse
import kotlin.test.Test
import kotlin.test.assertEquals

class IdMapperTest {

    private val idMapper = IdMapper()

    @Test
    fun givenAQualifiedId_whenMappingToApiModel_thenTheFieldsShouldBeMappedCorrectly() {
        val qualifiedID = QualifiedID("value", "domain")

        val networkID = idMapper.toApiModel(qualifiedID)

        assertEquals(qualifiedID.value, networkID.value)
        assertEquals(qualifiedID.domain, networkID.domain)
    }

    @Test
    fun givenAnAPIQualifiedId_whenMappingFromApiModel_thenTheFieldsShouldBeMappedCorrectly() {
        val networkID = NetworkQualifiedId("value", "domain")

        val qualifiedID = idMapper.fromApiModel(networkID)

        assertEquals(networkID.value, qualifiedID.value)
        assertEquals(networkID.domain, qualifiedID.domain)
    }

    @Test
    fun givenASimpleClientResponse_whenMappingFromSimpleClientResponse_thenTheIDShouldBeMappedCorrectly() {
        val simpleClientResponse = SimpleClientResponse("an ID", "it doesn't matter")

        val clientID = idMapper.fromSimpleClientResponse(simpleClientResponse)

        assertEquals(simpleClientResponse.id, clientID.value)
    }

}
