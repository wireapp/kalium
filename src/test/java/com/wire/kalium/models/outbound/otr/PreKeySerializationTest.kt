package com.wire.kalium.models.outbound.otr

import com.wire.kalium.tools.KtxSerializer
import com.wire.kalium.tools.commonMissingFieldTests
import com.wire.kalium.tools.json.PreKeyJson
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString

internal class PreKeySerializationTest : BehaviorSpec({

    Given("A valid JSON Provider") {
        val validJsonProvider = PreKeyJson.valid

        When("Deserializing it") {
            val result = KtxSerializer.json.decodeFromString<PreKey>(validJsonProvider.rawJson)

            Then("The ID should match") {
                result.id shouldBe validJsonProvider.serializableData.id
            }
            Then("The Key should match") {
                result.key shouldBe validJsonProvider.serializableData.key
            }
        }

        When("Serializing it") {
            val result = KtxSerializer.json.encodeToString(validJsonProvider.serializableData)

            Then("The serialized string should match") {
                result shouldBe validJsonProvider.rawJson
            }
        }
    }

    Given("A JSON with missing ID") {
        commonMissingFieldTests<PreKey>(PreKeyJson.missingId.rawJson)
    }
    Given("A JSON with wrong ID format") {
        commonMissingFieldTests<PreKey>(PreKeyJson.wrongIdFormat.rawJson)
    }
    Given("A JSON with missing Key") {
        commonMissingFieldTests<PreKey>(PreKeyJson.missingKey.rawJson)
    }
    Given("A JSON with wrong Key format") {
        commonMissingFieldTests<PreKey>(PreKeyJson.wrongKeyFormat.rawJson)
    }
})
