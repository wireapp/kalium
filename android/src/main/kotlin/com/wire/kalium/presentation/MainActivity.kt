package com.wire.kalium.presentation

import android.content.Context
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import com.wire.kalium.logic.CoreLogic
import com.wire.kalium.logic.data.conversation.Conversation
import com.wire.kalium.logic.feature.auth.AuthenticationResult
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            val conversations = runBlocking {
                val rootProteusDirectoryPath = getDir("proteus", Context.MODE_PRIVATE).path
                val core = CoreLogic(clientLabel, rootProteusDirectoryPath)
                val session = core.authenticationScope {
                    loginUsingEmail(
                        email = "username@example.com", //TODO Form or something to get user input
                        password = "password",
                        shouldPersistClient = false
                    ) as AuthenticationResult.Success // assuming success
                }.userSession

                val conversationList = core.sessionScope(session) {
                    conversations.getConversations().first()
                }

                core.sessionScope(session) {
                    try {
                        messages.sendTextMessage(conversationList.first().id, "Hello everyone")
                    } catch (notImplemented: NotImplementedError) {
                        /** But it will be! **/
                    }
                }

                conversationList
            }
            MainLayout(conversations)
        }
    }

    @Composable
    fun MainLayout(conversations: List<Conversation>) {
        Column {
            Text("Your conversations:")
            conversations.forEach {
                Text("ID: ${it.id}; Name: ${it.name}")
            }
        }
    }

    companion object {
        val clientLabel = "Kalium Android Sample; ${Build.MANUFACTURER} ${Build.MODEL} Android Build:${Build.VERSION.SDK_INT}"
    }
}
