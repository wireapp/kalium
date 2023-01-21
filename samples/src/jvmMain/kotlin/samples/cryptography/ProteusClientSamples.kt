package samples.cryptography

import com.wire.kalium.cryptography.ProteusClientImpl
import kotlinx.coroutines.CoroutineDispatcher
import kotlin.coroutines.CoroutineContext

fun jvmInitialization() {
    val proteusClient = ProteusClientImpl("rootDirectory", null)
}
