/*
 * Wire
 * Copyright (C) 2024 Wire Swiss GmbH
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see http://www.gnu.org/licenses/.
 */

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
import com.wire.kalium.logic.feature.auth.AuthenticationScope
import com.wire.kalium.logic.feature.auth.autoVersioningAuth.AutoVersionAuthScopeUseCase
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

    private suspend fun provideAuthScope(coreLogic: CoreLogic, backendLinks: ServerConfig.Links): AuthenticationScope =
        when (val result = coreLogic.versionedAuthenticationScope(backendLinks).invoke(null)) {
            is AutoVersionAuthScopeUseCase.Result.Failure.Generic -> error("Generic failure")
            AutoVersionAuthScopeUseCase.Result.Failure.TooNewVersion -> error("Too new version")
            AutoVersionAuthScopeUseCase.Result.Failure.UnknownServerVersion -> error("Unknown server version")
            is AutoVersionAuthScopeUseCase.Result.Success -> result.authenticationScope
        }

    private suspend fun login(coreLogic: CoreLogic, backendLinks: ServerConfig.Links): UserId {
        val result = provideAuthScope(coreLogic, backendLinks)
            .login("jacob.persson+summer1@wire.com", "hepphepp", false)

        if (result !is AuthenticationResult.Success) {
            error(
                """
                There was an error on the login :(
                Check the credentials and the internet connection and try again
                """.trimIndent()
            )
        }

        coreLogic.globalScope {
            addAuthenticatedAccount(
                serverConfigId = result.serverConfigId,
                ssoId = result.ssoID,
                authTokens = result.authData,
                proxyCredentials = null
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
