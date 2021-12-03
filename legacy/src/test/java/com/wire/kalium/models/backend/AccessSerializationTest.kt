package com.wire.kalium.models.backend

import com.wire.kalium.tools.json.AccessJson
import com.wire.kalium.tools.KtxSerializer
import com.wire.kalium.tools.commonMissingFieldTests
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.equality.shouldBeEqualToComparingFields
import kotlinx.serialization.decodeFromString

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
        commonMissingFieldTests<Access>(AccessJson.missingAccessToken.rawJson)
    }
    Given("A JSON with missing user") {
        commonMissingFieldTests<Access>(AccessJson.missingUser.rawJson)
    }
    Given("A JSON with missing token_type") {
        commonMissingFieldTests<Access>(AccessJson.missingTokenType.rawJson)
    }
    Given("A JSON with missing expiration") {
        commonMissingFieldTests<Access>(AccessJson.missingExpiration.rawJson)
    }
})
