import com.wire.kalium.models.backend.ServiceReferenceResponse
import com.wire.kalium.models.backend.json.ServiceReferenceResponseJson
import com.wire.kalium.tools.KtxSerializer
import io.kotest.core.spec.style.BehaviorSpec
import kotlinx.serialization.decodeFromString

class ServiceReferenceSerializationTest : BehaviorSpec({
    Given("A valid JSON of a service reference asset") {
        val json = ServiceReferenceResponseJson.valid

        When("Deserializing it"){
            KtxSerializer.json.decodeFromString<ServiceReferenceResponse>(json)
        }
    }
})
