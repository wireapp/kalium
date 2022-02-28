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
import com.wire.kalium.logic.configuration.ServerConfig
import com.wire.kalium.logic.data.conversation.Conversation
import com.wire.kalium.logic.data.user.SelfUser
import com.wire.kalium.logic.feature.auth.AuthSession
import com.wire.kalium.logic.feature.auth.AuthenticationResult
import com.wire.kalium.logic.feature.auth.AuthenticationScope
import com.wire.kalium.logic.functional.map
import kotlinx.coroutines.flow.first

class MainActivity : ComponentActivity() {

    val serverConfig: ServerConfig by lazy { ServerConfig.DEFAULT }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        loginAndFetchConverationList((application as KaliumApplication).coreLogic)
    }

    fun loginAndFetchConverationList(coreLogic: CoreLogic) = lifecycleScope.launchWhenCreated {
        login(coreLogic.getAuthenticationScope())?.let {
            val session = coreLogic.getSessionScope(it)
            val conversations = session.conversations.getConversations().first()

            // Uploading image code
//            val imageContent = applicationContext.assets.open("moon1.jpg").readBytes()
//            session.users.uploadUserAvatar("image/jpg", imageContent)

            val selfUser = session.users.getSelfUser().first()

            var avatarAsset: ByteArray? = null
            session.users.getPublicAsset(selfUser.previewPicture.toString()).map { data ->
                avatarAsset = data
            }

            setContent {
                MainLayout(conversations, selfUser, avatarAsset)
            }
        }
    }

    suspend fun login(authenticationScope: AuthenticationScope): AuthSession? {
        val result = authenticationScope.login("jacob.persson+summer1@wire.com", "hepphepp", false, serverConfig)

        if (result !is AuthenticationResult.Success) {
            throw RuntimeException(
                "There was an error on the login :(" +
                    "Check the credentials and the internet connection and try again"
            )
        }

        return result.userSession
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
            modifier = Modifier.fillMaxWidth(),
            thickness = 0.5.dp
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
