package com.wire.kalium.presentation

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import com.wire.kalium.cryptography.CryptoClientId
import com.wire.kalium.cryptography.CryptoSessionId
import com.wire.kalium.cryptography.ProteusClient
import com.wire.kalium.cryptography.UserId
import java.io.File

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val rootProteusDir = File(this.filesDir, "proteus")

        val alice = SampleUser("aliceId", "Alice")
        val aliceFile = File(rootProteusDir, alice.id)
        aliceFile.mkdirs()
        val aliceClient = ProteusClient(rootProteusDir.absolutePath, alice.id)
        aliceClient.open()
        val aliceSessionId = CryptoSessionId(UserId(alice.id), CryptoClientId("aliceClient"))
        val aliceKey = aliceClient.newPreKeys(0, 10).first()

        val bob = SampleUser("bobId", "Bob")
        val bobFile = File(rootProteusDir, bob.id)
        bobFile.mkdirs()
        val bobClient = ProteusClient(rootProteusDir.absolutePath, bob.id)
        val bobSessionId = CryptoSessionId(UserId(bob.id), CryptoClientId("bobClient"))
        bobClient.open()

        bobClient.createSession(aliceKey, aliceSessionId)
        val message = "Oi Alice!"
        val encryptedMessage = bobClient.encrypt(message.toByteArray(Charsets.UTF_8), aliceSessionId)!!
        val decryptedMessage = aliceClient.decrypt(encryptedMessage, bobSessionId)

        setContent {
            MainLayout(decryptedMessage.toString(Charsets.UTF_8))
        }
    }

    data class SampleUser(val id: String, val name: String)
}

data class SampleUser(val id: String, val name: String)


@Composable
fun MainLayout(messageFromBob: String) {
    Column {
        Text("Bob said:")
        Text(messageFromBob)
    }
}
