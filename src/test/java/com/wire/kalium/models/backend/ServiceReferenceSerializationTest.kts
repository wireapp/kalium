import com.wire.kalium.models.backend.ServiceReferenceResponse
import com.wire.kalium.tools.commonMissingFieldTests
import com.wire.kalium.tools.json.ServiceReferenceResponseJson
import com.wire.kalium.tools.KtxSerializer
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import kotlinx.serialization.decodeFromString

class ServiceReferenceSerializationTest : BehaviorSpec({
    Given("A valid JSON of a service reference asset") {
        val validJsonProvider = ServiceReferenceResponseJson.valid

        When("Deserializing it") {
            val result = KtxSerializer.json.decodeFromString<ServiceReferenceResponse>(validJsonProvider.rawJson)

            Then("ID should match") {
                result.id shouldBe validJsonProvider.serializableData.id
            }
            Then("Provider should match") {
                result.provider shouldBe validJsonProvider.serializableData.provider
            }
        }
    }

    Given("A JSON with missing ID") {
        commonMissingFieldTests<ServiceReferenceResponse>(ServiceReferenceResponseJson.missingId.rawJson)
    }
    Given("A JSON with wrong ID format") {
        commonMissingFieldTests<ServiceReferenceResponse>(ServiceReferenceResponseJson.wrongIdFormat.rawJson)
    }
    Given("A JSON with missing Provider") {
        commonMissingFieldTests<ServiceReferenceResponse>(ServiceReferenceResponseJson.missingId.rawJson)
    }
    Given("A JSON with wrong Provider format") {
        commonMissingFieldTests<ServiceReferenceResponse>(ServiceReferenceResponseJson.wrongProviderFormat.rawJson)
    }
})
