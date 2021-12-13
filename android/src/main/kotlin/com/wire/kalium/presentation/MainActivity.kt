package com.wire.kalium.presentation

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import com.wire.kalium.logic.CoreLogic
import com.wire.kalium.logic.feature.auth.AuthenticationScope
import com.wire.kalium.network.api.conversation.ConversationResponse
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            val conversations = runBlocking {
                val core = CoreLogic()
                val session = core.authenticationScope {
                    loginUsingEmail(
                        "username@example.com", //TODO Form or something to get user input
                        "password"
                    ) as AuthenticationScope.AuthenticationResult.Success // assuming success
                }.userSession

                val userSessionScope = core.getSessionScope(session)

                val sessionScope = core.sessionScope(session)
                conversations.getConversations().first()
                messages.sendTextMessage()
            }
        }
        MainLayout(conversations)
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
