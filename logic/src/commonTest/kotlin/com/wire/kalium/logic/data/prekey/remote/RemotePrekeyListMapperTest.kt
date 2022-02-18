package com.wire.kalium.logic.data.prekey.remote

import com.wire.kalium.logic.data.id.QualifiedID
import com.wire.kalium.logic.data.prekey.PreKey
import com.wire.kalium.logic.data.prekey.PreKeyMapper
import com.wire.kalium.network.api.prekey.PreKeyDTO
import io.mockative.Mock
import io.mockative.any
import io.mockative.classOf
import io.mockative.eq
import io.mockative.given
import io.mockative.mock
import io.mockative.once
import io.mockative.twice
import io.mockative.verify
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

class PreKeyListMapperTest {

    @Mock
    private val preKeyMapper = mock(classOf<PreKeyMapper>())

    private lateinit var subject: PreKeyListMapper

    @BeforeTest
    fun setup() {
        subject = PreKeyListMapper(preKeyMapper)

        given(preKeyMapper)
            .function(preKeyMapper::fromPreKeyDTO)
            .whenInvokedWith(any())
            .then { PreKey(1, "2") }

    }

    @Test
    fun given_PreKeyMap_when_mapping_to_qualifiedUserPreKeyInfo_then_usersIDs_should_be_converted_correctly() {
        val preKeyResponse = mapOf(
            "domA" to mapOf(
                "userA" to mapOf("clientA" to PreKeyDTO(1, "keyA")),
                "userB" to mapOf("clientB" to PreKeyDTO(32, "key"))
            ),
            "domB" to mapOf(
                "userB" to mapOf("clientB" to PreKeyDTO(22, "keyC"))
            )
        )

        val result = subject.fromRemoteQualifiedPreKeyInfoMap(preKeyResponse)

        val doesContain = result.map {
            it.userId
        }.containsAll(
            listOf(
                QualifiedID("domA", "userA"),
                QualifiedID("domA", "userB"),
                QualifiedID("domB", "userB")
            )
        )

        assertEquals(true, doesContain)
        assertEquals(3, result.size)
    }

    @Test
    fun given_PreKeyMap_when_mapping_to_list_QualifiedUserPreKeyInfo_then_PreKeyMapper_should_be_used() {
        val preKeyResponse = mapOf(
            "domA" to mapOf(
                "userA" to mapOf(
                    "clientA" to PreKeyDTO(1, "keyA"),
                    "clientB" to PreKeyDTO(1, "keyB")
                )
            )
        )

        subject.fromRemoteQualifiedPreKeyInfoMap(preKeyResponse)

        verify(preKeyMapper)
            .function(preKeyMapper::fromPreKeyDTO)
            .with(any())
            .wasInvoked(exactly = twice)
    }

    @Test
    fun given_PreKeyMap_when_mapping_to_list_QualifiedUserPreKeyInfo_then_PreKeyMapper_should_receive_the_correct_arguments() {
        val firstKey = PreKeyDTO(1, "keyA")
        val secondKey = PreKeyDTO(4, "keyB")
        val preKeyResponse = mapOf(
            "domA" to mapOf(
                "userA" to mapOf(
                    "clientA" to firstKey,
                    "clientB" to secondKey
                )
            )
        )

        subject.fromRemoteQualifiedPreKeyInfoMap(preKeyResponse)

        verify(preKeyMapper)
            .function(preKeyMapper::fromPreKeyDTO)
            .with(eq(firstKey))
            .wasInvoked(exactly = once)

        verify(preKeyMapper)
            .function(preKeyMapper::fromPreKeyDTO)
            .with(eq(secondKey))
            .wasInvoked(exactly = once)
    }

    @Test
    fun given_PreKeyMap_when_mapping_to_list_QualifiedUserPreKeyInfo_then_clients_should_be_returned_in_the_right_users() {
        val preKeyResponse = mapOf(
            "domA" to mapOf(
                "userA" to mapOf(
                    "clientA" to PreKeyDTO(1, "keyA"),
                    "clientB" to PreKeyDTO(1, "keyA")
                ),
                "userB" to mapOf("clientC" to PreKeyDTO(1, "keyA"))
            )
        )

        val result = subject.fromRemoteQualifiedPreKeyInfoMap(preKeyResponse)

        val clientsA = result.first().clientsInfo.map { it.clientId }
        print("size = ${clientsA.size} ")
        assertEquals(2, clientsA.size)
        val doesContain1 = clientsA.containsAll(listOf("clientA", "clientB"))
        assertEquals(true, doesContain1)

        val clientsB = result[1].clientsInfo.map { it.clientId }
        assertEquals(1, clientsB.size)
        val doesContain2 = clientsB.containsAll(listOf("clientC"))
        assertEquals(true, doesContain2)
    }

    @Test
    fun `given_PreKeyMap_when_mapping_to_list_QualifiedUserPreKeyInfo_then_keys_should_be_returned_in_the_right_clients`() {
        class KeyMappingTestSet(val clientId: String, val response: PreKeyDTO, val mapped: PreKey)

        val firstKeySet = KeyMappingTestSet("a", PreKeyDTO(1, "keyA"), PreKey(1, "keyA"))
        val secondKeySet = KeyMappingTestSet("b", PreKeyDTO(4, "keyB"), PreKey(4, "keyB"))
        val thirdKeySet = KeyMappingTestSet("c", PreKeyDTO(4, "keyC"), PreKey(4, "keyC"))

        val preKeyResponse = mapOf(
            "domA" to mapOf(
                "userA" to mapOf(
                    firstKeySet.clientId to firstKeySet.response,
                    secondKeySet.clientId to secondKeySet.response
                ),
                "userB" to mapOf(thirdKeySet.clientId to thirdKeySet.response)
            )
        )
        given(preKeyMapper)
            .function(preKeyMapper::fromPreKeyDTO)
            .whenInvokedWith(eq(firstKeySet.response))
            .then { firstKeySet.mapped }

        given(preKeyMapper)
            .function(preKeyMapper::fromPreKeyDTO)
            .whenInvokedWith(eq(secondKeySet.response))
            .then { secondKeySet.mapped }

        given(preKeyMapper)
            .function(preKeyMapper::fromPreKeyDTO)
            .whenInvokedWith(eq(thirdKeySet.response))
            .then { thirdKeySet.mapped }

        val result = subject.fromRemoteQualifiedPreKeyInfoMap(preKeyResponse)

        val userAInfo = result.first().clientsInfo
        val firstClient = userAInfo.first()
        assertEquals(firstClient.clientId, firstKeySet.clientId)
        assertEquals(firstClient.preKey, firstKeySet.mapped)

        val secondClient = userAInfo[1]

        assertEquals(secondClient.clientId, secondKeySet.clientId)
        assertEquals(secondClient.preKey, secondKeySet.mapped)

        val thirdClient = result[1].clientsInfo.first()
        assertEquals(thirdClient.clientId, thirdKeySet.clientId)
        assertEquals(thirdClient.preKey, thirdKeySet.mapped)
    }

}
