package samples.cryptography

import com.wire.kalium.cryptography.CryptoClientId
import com.wire.kalium.cryptography.CryptoSessionId
import com.wire.kalium.cryptography.CryptoUserID
import com.wire.kalium.cryptography.ProteusClient

object ProteusClient {
    suspend fun basicEncryption(
        proteusClient: ProteusClient,
        cryptoUserId: CryptoUserID,
        cryptoClientId: CryptoClientId
    ) {
        val messageData = byteArrayOf(0x42, 0x69)
        val payload: ByteArray = proteusClient.encrypt(
            messageData,
            CryptoSessionId(cryptoUserId, cryptoClientId)
        )
    }
}
