package com.wire.kalium.logic.feature.message

import com.wire.kalium.logic.data.prekey.ClientPreKeyInfo
import com.wire.kalium.logic.data.prekey.QualifiedUserPreKeyInfo
import com.wire.kalium.logic.data.user.UserId
import kotlin.test.Test
import com.wire.kalium.cryptography.PreKeyCrypto
import com.wire.kalium.logic.data.prekey.PreKeyMapperImpl
import com.wire.kalium.network.api.base.authenticated.prekey.PreKeyDTO
import com.wire.kalium.persistence.dao.QualifiedIDEntity
import com.wire.kalium.persistence.dao.UserIDEntity
import kotlin.test.BeforeTest
import kotlin.test.Ignore
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
            mapOf("domain1" to mapOf(
                "user1" to mapOf(
                    "client1_null" to null,
                    "valid_client" to PreKeyDTO(1, "key1")
                ),
                "user2" to mapOf(
                    "client1_null" to null,
                    "client2_null" to null
                    )
            ))


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

        val expectedInvalid:  List<Pair<QualifiedIDEntity, List<String>>> = listOf(
            UserIDEntity("user1", "domain1") to listOf("client1_null"),
            UserIDEntity("user2", "domain1") to listOf("client1_null", "client2_null"),

        )
        cryptoSessionMapper.getMapOfSessionIdsToPreKeysAndMarkNullClientsAsInvalid(domainToUserIdTOClientIdToPrekeyMap).also { actual ->
            assertEquals(expectedValid, actual.valid)
            assertEquals(expectedInvalid, actual.invalid)
        }
    }
}
