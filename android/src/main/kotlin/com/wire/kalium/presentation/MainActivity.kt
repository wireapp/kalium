package com.wire.kalium.presentation

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import com.wire.kalium.logic.AuthenticationScope
import com.wire.kalium.logic.CoreLogic
import com.wire.kalium.network.api.conversation.ConversationResponse
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            val core = CoreLogic()
            val session = core.authentication {
                runBlocking {
                    loginUsingEmail(
                        "username@example.com",
                        "password"
                    ) as AuthenticationScope.AuthenticationResult.Success // assuming success
                }
            }.userSession
            val conversations = core.session(session) {
                runBlocking {
                    conversations.getConversations().first()
                }
            }
            MainLayout(conversations)
        }
    }
}

@Composable
//TODO: Don't use serialization data!
fun MainLayout(conversations: List<ConversationResponse>) {
    Column {
        Text("Your conversations:")
        conversations.forEach {
            Text("ID: ${it.id}; Name: ${it.name}")
        }
    }
}
