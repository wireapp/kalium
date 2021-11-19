package com.wire.kalium.models.backend

import com.wire.kalium.models.backend.json.AccessJson
import com.wire.kalium.models.backend.json.FaultyJsonProvider
import com.wire.kalium.tools.KtxSerializer
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.core.spec.style.scopes.BehaviorSpecGivenContainerContext
import io.kotest.matchers.equality.shouldBeEqualToComparingFields
import io.kotest.matchers.should
import io.kotest.matchers.types.beInstanceOf
import kotlinx.serialization.SerializationException
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

class AccessSerializationTest : BehaviorSpec({
    Given("A valid JSON") {
        val validJson = AccessJson.valid

        When("Deserializing it") {
            val result: Access = KtxSerializer.json.decodeFromString(validJson.rawJson)

            Then("Access Token should match") {
                result.accessToken shouldBeEqualToComparingFields validJson.serializableData.accessToken
            }
            Then("User should match") {
                result.user shouldBeEqualToComparingFields validJson.serializableData.user
            }
            Then("Expiration should match") {
                result.expires_in shouldBeEqualToComparingFields validJson.serializableData.expires_in
            }
            Then("Token type should match") {
                result.token_type shouldBeEqualToComparingFields validJson.serializableData.token_type
            }
        }
    }

    Given("A JSON with missing access_token") {
        commonMissingFieldTests(AccessJson.missingAccessToken)
    }
    Given("A JSON with missing user") {
        commonMissingFieldTests(AccessJson.missingUser)
    }
    Given("A JSON with missing token_type") {
        commonMissingFieldTests(AccessJson.missingTokenType)
    }
    Given("A JSON with missing expiration") {
        commonMissingFieldTests(AccessJson.missingExpiration)
    }
})

private suspend fun BehaviorSpecGivenContainerContext.commonMissingFieldTests(json: FaultyJsonProvider) {
    When("Deserializing it") {
        val exception = runCatching {
            KtxSerializer.json.decodeFromString<Access>(json.rawJson)
        }.exceptionOrNull()

        Then("An SerializationException should be throw") {
            exception should beInstanceOf<SerializationException>()
        }
    }
}
