package com.wire.kalium.models.backend

import com.wire.kalium.tools.KtxSerializer
import io.kotest.core.spec.style.scopes.BehaviorSpecGivenContainerContext
import io.kotest.matchers.should
import io.kotest.matchers.types.beInstanceOf
import kotlinx.serialization.SerializationException
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

suspend inline fun <reified T> BehaviorSpecGivenContainerContext.commonMissingFieldTests(
    jsonValue: String,
    jsonSerializer: Json = KtxSerializer.json
) {
    When("Deserializing it") {
        val exception = runCatching {
            jsonSerializer.decodeFromString<T>(jsonValue)
        }.exceptionOrNull()

        Then("An SerializationException should be throw") {
            exception should beInstanceOf<SerializationException>()
        }
    }
}
