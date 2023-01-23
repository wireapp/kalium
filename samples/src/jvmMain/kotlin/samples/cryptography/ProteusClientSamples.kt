package samples.cryptography

import com.wire.kalium.cryptography.ProteusClientImpl

fun jvmInitialization() {
    val proteusClient = ProteusClientImpl(
        "rootDirectory",
        null,
        defaultContext = Dispatchers.Default,
        ioContext = Dispatchers.IO
    )
}
