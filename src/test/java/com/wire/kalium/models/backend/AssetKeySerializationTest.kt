package com.wire.kalium.models.backend

import com.wire.kalium.tools.KtxSerializer
import com.wire.kalium.tools.commonMissingFieldTests
import com.wire.kalium.tools.json.AssetKeyJson
import com.wire.kalium.tools.json.ValidJsonProvider
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.core.spec.style.scopes.BehaviorSpecGivenContainerContext
import io.kotest.matchers.equality.shouldBeEqualToComparingFields
import kotlinx.serialization.decodeFromString

class AssetKeySerializationTest : BehaviorSpec({
    Given("A valid JSON of a complete image asset") {
        commonValidTests(AssetKeyJson.validCompleteImage)
    }
    Given("A valid JSON of a preview image asset") {
        commonValidTests(AssetKeyJson.validPreviewImage)
    }
    Given("A JSON with missing access_token") {
        commonMissingFieldTests<AssetKey>(AssetKeyJson.missingKey.rawJson)
    }
    Given("A JSON with missing user") {
        commonMissingFieldTests<AssetKey>(AssetKeyJson.missingSize.rawJson)
    }
    Given("A JSON with missing token_type") {
        commonMissingFieldTests<AssetKey>(AssetKeyJson.missingType.rawJson)
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
