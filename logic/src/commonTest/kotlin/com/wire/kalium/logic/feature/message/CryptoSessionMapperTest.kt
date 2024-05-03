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

package com.wire.kalium.logic.feature.message

import kotlin.test.Test
import com.wire.kalium.cryptography.PreKeyCrypto
import com.wire.kalium.logic.data.message.CryptoSessionMapper
import com.wire.kalium.logic.data.message.CryptoSessionMapperImpl
import com.wire.kalium.logic.data.prekey.PreKeyMapperImpl
import com.wire.kalium.network.api.base.authenticated.prekey.PreKeyDTO
import com.wire.kalium.persistence.dao.QualifiedIDEntity
import com.wire.kalium.persistence.dao.UserIDEntity
import kotlin.test.BeforeTest
import kotlin.test.assertEquals

class CryptoSessionMapperTest {

    private lateinit var cryptoSessionMapper: CryptoSessionMapper

    @BeforeTest
    fun setup() {
        cryptoSessionMapper = CryptoSessionMapperImpl(PreKeyMapperImpl())
    }

    @Test
    fun givenListOfQualifiedUserPreKeyInfo_whenMappingToCryptoSessions_thenValidClientsAreSeperatedFromInvalid() {

        val domainToUserIdTOClientIdToPrekeyMap: Map<String, Map<String, Map<String, PreKeyDTO?>>> =
            mapOf(
                "domain1" to mapOf(
                    "user1" to mapOf(
                        "client1_null" to null,
                        "valid_client" to PreKeyDTO(1, "key1")
                    ),
                    "user2" to mapOf(
                        "client1_null" to null,
                        "client2_null" to null
                    )
                )
            )

        val expectedValid: Map<String, Map<String, Map<String, PreKeyCrypto>>> = mapOf(
            "domain1" to mapOf(
                "user1" to mapOf(
                    "valid_client" to PreKeyCrypto(
                        id = 1,
                        encodedData = "key1"
                    )
                ),
                "user2" to emptyMap<String, PreKeyCrypto>()
            )
        )

        val expectedInvalid: List<Pair<QualifiedIDEntity, List<String>>> = listOf(
            UserIDEntity("user1", "domain1") to listOf("client1_null"),
            UserIDEntity("user2", "domain1") to listOf("client1_null", "client2_null"),
        )
        cryptoSessionMapper.getMapOfSessionIdsToPreKeysAndMarkNullClientsAsInvalid(domainToUserIdTOClientIdToPrekeyMap).also { actual ->
            assertEquals(expectedValid, actual.valid)
            assertEquals(expectedInvalid, actual.invalid)
        }
    }
}
