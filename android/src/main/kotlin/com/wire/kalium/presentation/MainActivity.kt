package com.wire.kalium.presentation

import android.graphics.BitmapFactory
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material.Divider
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import com.wire.kalium.KaliumApplication
import com.wire.kalium.logic.CoreLogic
import com.wire.kalium.logic.configuration.server.ServerConfig
import com.wire.kalium.logic.data.conversation.Conversation
import com.wire.kalium.logic.data.user.SelfUser
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.feature.asset.PublicAssetResult
import com.wire.kalium.logic.feature.auth.AuthenticationResult
import com.wire.kalium.logic.feature.conversation.GetConversationsUseCase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import okio.buffer
import java.io.IOException

class MainActivity : ComponentActivity() {

    private val serverConfig: ServerConfig.Links by lazy { ServerConfig.DEFAULT }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        loginAndFetchConversationList((application as KaliumApplication).coreLogic)
    }

    private fun loginAndFetchConversationList(coreLogic: CoreLogic) = lifecycleScope.launchWhenCreated {
        login(coreLogic, serverConfig).let {
            val session = coreLogic.getSessionScope(it)
            val kaliumFileSystem = session.kaliumFileSystem
            val conversations = runBlocking { session.conversations.getConversations() }.let { result ->
                when (result) {
                    is GetConversationsUseCase.Result.Failure -> {
                        throw IOException()
                    }

                    is GetConversationsUseCase.Result.Success -> result.convFlow.first()
                }
            }

            // Uploading image code
            val imageContent = applicationContext.assets.open("moon1.jpg").readBytes()
            val tempAvatarPath = kaliumFileSystem.providePersistentAssetPath("temp_avatar.jpg")
            val tempAvatarSink = kaliumFileSystem.sink(tempAvatarPath)
            tempAvatarSink.buffer().use { sink ->
                sink.write(imageContent)
            }

            session.users.uploadUserAvatar(tempAvatarPath, imageContent.size.toLong())

            val selfUser = session.users.getSelfUser().first()

            val avatarAsset = when (val publicAsset = session.users.getPublicAsset(selfUser.previewPicture!!)) {
                is PublicAssetResult.Success -> {
                    // We read the avatar data stored in the assetPath
                    session.kaliumFileSystem.readByteArray(publicAsset.assetPath)
                }
                else -> null
            }

            setContent {
                MainLayout(conversations, selfUser, avatarAsset)
            }
        }
    }

    private suspend fun login(coreLogic: CoreLogic, backendLinks: ServerConfig.Links): UserId {
        val result = coreLogic.authenticationScope(backendLinks) {
            login("jacob.persson+summer1@wire.com", "hepphepp", false)
        }

        if (result !is AuthenticationResult.Success) {
            throw RuntimeException(
                "There was an error on the login :(" + "Check the credentials and the internet connection and try again"
            )
        }

        coreLogic.globalScope {
            addAuthenticatedAccount(
                serverConfigId = result.serverConfigId,
                ssoId = result.ssoID,
                authTokens = result.authData
            )
        }

        return result.authData.userId
    }
}

@Composable
fun MainLayout(conversations: List<Conversation>, selfUser: SelfUser, avatarAsset: ByteArray?) {
    Column {
        Text("Conversation count:")
        Text("${conversations.size}")
        Text("SelfUser:")
        Text("$selfUser")

        Divider(
            modifier = Modifier.fillMaxWidth(), thickness = 0.5.dp
        )

        Text(text = "Avatar picture:")

        avatarAsset?.let { byteArray ->
            Image(
                bitmap = BitmapFactory.decodeByteArray(byteArray, 0, byteArray.size)?.asImageBitmap()!!,
                contentDescription = "",
                modifier = Modifier.size(300.dp)
            )
        }
    }
}
