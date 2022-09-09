package com.wire.kalium.logic.feature.message

import com.wire.kalium.logic.data.prekey.ClientPreKeyInfo
import com.wire.kalium.logic.data.prekey.QualifiedUserPreKeyInfo
import com.wire.kalium.logic.data.user.UserId
import kotlin.test.Test
import com.wire.kalium.cryptography.PreKeyCrypto
import kotlin.test.BeforeTest
import kotlin.test.assertEquals

class CryptoSessionMapperTest {

    private lateinit var cryptoSessionMapper: CryptoSessionMapper

    @BeforeTest
    fun setup() {
        cryptoSessionMapper = CryptoSessionMapperImpl()
    }

    @Test
    fun givenListOfQualifiedUserPreKeyInfo_whenMappingToCryptoSessions_thenClientsWithNullPreyKeyAreIgnored() {
        val qualifiedUserPreKeyInfo = listOf(
            QualifiedUserPreKeyInfo(
                userId = UserId("user1", "domain1"),
                clientsInfo = listOf(
                    ClientPreKeyInfo(
                        clientId = "client1_null",
                        preKey = null
                    ),
                    ClientPreKeyInfo(
                        clientId = "client1_null",
                        preKey = PreKeyCrypto(
                            id = 1,
                            encodedData = "key1"
                        )
                    )
                )
            ),
            QualifiedUserPreKeyInfo(
                userId = UserId("user2", "domain1"),
                clientsInfo = listOf(
                    ClientPreKeyInfo(
                        clientId = "client1_null",
                        preKey = null
                    ),
                    ClientPreKeyInfo(
                        clientId = "client1_null",
                        preKey = null
                    )
                )
            )
        )

        val expected: Map<String, Map<String, Map<String, PreKeyCrypto>>> = mapOf(
            "domain1" to mapOf(
                "user1" to mapOf(
                    "client1_null" to PreKeyCrypto(
                        id = 1,
                        encodedData = "key1"
                    )
                )
            )
        )

        cryptoSessionMapper.getMapOfSessionIdsToPreKeysAndIgnoreNull(qualifiedUserPreKeyInfo).also { actual ->
            assertEquals(expected, actual)
        }
    }
}
