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

package com.wire.kalium.logic.data.prekey

import com.wire.kalium.cryptography.PreKeyCrypto
import com.wire.kalium.logic.data.id.QualifiedID
import com.wire.kalium.network.api.authenticated.prekey.PreKeyDTO
import io.mockative.Mock
import io.mockative.any
import io.mockative.eq
import io.mockative.every
import io.mockative.mock
import io.mockative.once
import io.mockative.twice
import io.mockative.verify
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

class PreKeyListMapperTest {

    @Mock
    private val preKeyMapper = mock(PreKeyMapper::class)

    private lateinit var subject: PreKeyListMapper

    @BeforeTest
    fun setup() {
        subject = PreKeyListMapper(preKeyMapper)

        every {
            preKeyMapper.fromPreKeyDTO(any())
        }.returns(PreKeyCrypto(1, "2"))
    }

    @Test
    fun given_PreKeyMap_when_mapping_to_qualifiedUserPreKeyInfo_then_usersIDs_should_be_converted_correctly() {
        val preKeyResponse = mapOf(
            "domA" to mapOf(
                "userA" to mapOf(
                    "clientA" to PreKeyDTO(1, "keyA")
                ),
                "userB" to mapOf(
                    "clientB" to PreKeyDTO(32, "key")
                )
            ),
            "domB" to mapOf(
                "userB" to mapOf(
                    "clientB" to PreKeyDTO(22, "keyC")
                )
            )
        )

        val result = subject.fromRemoteQualifiedPreKeyInfoMap(preKeyResponse)

        val doesContain = result.map {
            it.userId
        }.containsAll(
            listOf(
                QualifiedID("userA", "domA"),
                QualifiedID("userB", "domA"),
                QualifiedID("userB", "domB")
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

        verify {
            preKeyMapper.fromPreKeyDTO(any())
        }.wasInvoked(exactly = twice)
    }

    @Test
    fun given_PreKeyMap_when_mapping_to_list_QualifiedUserPreKeyInfo_then_PreKeyMapper_should_receive_the_correct_arguments() {
        val firstKey = PreKeyDTO(1, "keyA")
        val secondKey = PreKeyDTO(4, "keyB")
        val preKeyResponse = mapOf(
            "domA" to mapOf(
                "userA" to mapOf(
                    "clientA" to firstKey, "clientB" to secondKey
                )
            )
        )

        subject.fromRemoteQualifiedPreKeyInfoMap(preKeyResponse)

        verify {
            preKeyMapper.fromPreKeyDTO(eq(firstKey))
        }.wasInvoked(exactly = once)

        verify {
            preKeyMapper.fromPreKeyDTO(eq(secondKey))
        }.wasInvoked(exactly = once)
    }

    @Test
    fun given_PreKeyMap_when_mapping_to_list_QualifiedUserPreKeyInfo_then_clients_should_be_returned_in_the_right_users() {
        val preKeyResponse = mapOf(
            "domA" to mapOf(
                "userA" to mapOf(
                    "clientA" to PreKeyDTO(1, "keyA"),
                    "clientB" to PreKeyDTO(1, "keyA")
                ),
                "userB" to mapOf(
                    "clientC" to PreKeyDTO(1, "keyA")
                )
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
    fun given_PreKeyMap_when_mapping_to_list_QualifiedUserPreKeyInfo_then_keys_should_be_returned_in_the_right_clients() {
        class KeyMappingTestSet(val clientId: String, val response: PreKeyDTO, val mapped: PreKeyCrypto)

        val firstKeySet = KeyMappingTestSet(
            "a",
            PreKeyDTO(1, "keyA"),
            PreKeyCrypto(1, "keyA")
        )
        val secondKeySet = KeyMappingTestSet(
            "b",
            PreKeyDTO(4, "keyB"),
            PreKeyCrypto(4, "keyB")
        )
        val thirdKeySet = KeyMappingTestSet(
            "c",
            PreKeyDTO(4, "keyC"),
            PreKeyCrypto(4, "keyC")
        )

        val preKeyResponse = mapOf(
            "domA" to mapOf(
                "userA" to mapOf(
                    firstKeySet.clientId to firstKeySet.response, secondKeySet.clientId to secondKeySet.response
                ),
                "userB" to mapOf(thirdKeySet.clientId to thirdKeySet.response)
            )
        )
        every {
            preKeyMapper.fromPreKeyDTO(eq(firstKeySet.response))
        }.returns(firstKeySet.mapped)

        every {
            preKeyMapper.fromPreKeyDTO(eq(secondKeySet.response))
        }.returns(secondKeySet.mapped)

        every {
            preKeyMapper.fromPreKeyDTO(eq(thirdKeySet.response))
        }.returns(thirdKeySet.mapped)

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
