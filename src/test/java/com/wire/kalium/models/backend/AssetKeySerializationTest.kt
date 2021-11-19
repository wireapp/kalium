package com.wire.kalium.models.backend

import com.wire.kalium.models.backend.json.AssetKeyJson
import com.wire.kalium.models.backend.json.FaultyJsonProvider
import com.wire.kalium.models.backend.json.ValidJsonProvider
import com.wire.kalium.tools.KtxSerializer
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.core.spec.style.scopes.BehaviorSpecGivenContainerContext
import io.kotest.matchers.equality.shouldBeEqualToComparingFields
import io.kotest.matchers.should
import io.kotest.matchers.types.beInstanceOf
import kotlinx.serialization.SerializationException
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

class AssetKeySerializationTest : BehaviorSpec({
    Given("A valid JSON of a complete image asset") {
        commonValidTests(AssetKeyJson.validCompleteImage)
    }
    Given("A valid JSON of a preview image asset") {
        commonValidTests(AssetKeyJson.validPreviewImage)
    }
    Given("A JSON with missing access_token") {
        commonMissingFieldTests(AssetKeyJson.missingKey)
    }
    Given("A JSON with missing user") {
        commonMissingFieldTests(AssetKeyJson.missingSize)
    }
    Given("A JSON with missing token_type") {
        commonMissingFieldTests(AssetKeyJson.missingType)
    }
})

private suspend fun BehaviorSpecGivenContainerContext.commonValidTests(validJson: ValidJsonProvider<AssetKey>) {
    When("Deserializing it") {
        val result: AssetKey = KtxSerializer.json.decodeFromString(validJson.rawJson)

        Then("Size should match") {
            result.size shouldBeEqualToComparingFields validJson.serializableData.size
        }
        Then("Key should match") {
            result.key shouldBeEqualToComparingFields validJson.serializableData.key
        }
        Then("Type should match") {
            result.type shouldBeEqualToComparingFields validJson.serializableData.type
        }
    }
}

private suspend fun BehaviorSpecGivenContainerContext.commonMissingFieldTests(json: FaultyJsonProvider) {
    When("Deserializing it") {
        val exception = runCatching {
            KtxSerializer.json.decodeFromString<AssetKey>(json.rawJson)
        }.exceptionOrNull()

        Then("An SerializationException should be throw") {
            exception should beInstanceOf<SerializationException>()
        }
    }
}
