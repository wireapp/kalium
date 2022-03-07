package com.wire.kalium.cli

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import com.wire.kalium.cli.CLIUtils.getResource
import com.wire.kalium.cryptography.utils.calcMd5
import com.wire.kalium.logic.configuration.ServerConfig
import com.wire.kalium.logic.configuration.ServerConfigMapper
import com.wire.kalium.logic.configuration.ServerConfigMapperImpl
import com.wire.kalium.network.AuthenticatedNetworkContainer
import com.wire.kalium.network.LoginNetworkContainer
import com.wire.kalium.network.api.model.AssetMetadataRequest
import com.wire.kalium.network.api.model.AssetRetentionType
import com.wire.kalium.network.api.user.login.LoginApi
import com.wire.kalium.network.tools.BackendConfig
import com.wire.kalium.network.utils.isSuccessful
import kotlinx.coroutines.runBlocking

class ConversationsApplication : CliktCommand() {
    private val email: String by option(help = "wire account email").required()
    private val password: String by option(help = "wire account password").required()

    override fun run(): Unit = runBlocking {
        val serverConfigMapper: ServerConfigMapper = ServerConfigMapperImpl()
        val backendConfig: BackendConfig = serverConfigMapper.toBackendConfig(ServerConfig.DEFAULT)
        val loginContainer = LoginNetworkContainer()

        val loginResult = loginContainer.loginApi.login(
            LoginApi.LoginParam.LoginWithEmail(email = email, password = password, label = "ktor"), false, backendConfig.apiBaseUrl
        )

        if (!loginResult.isSuccessful()) {
            println("There was an error on the login :( check the credentials and the internet connection and try again please")
        } else {
            val sessionData = loginResult.value
            //TODO: Get them 🍪 refresh token
            val networkModule = AuthenticatedNetworkContainer(
                sessionDTO = sessionData,
                backendConfig = backendConfig
            )
            val conversationsResponse = networkModule.conversationApi.conversationsByBatch(null, 100)

            if (!conversationsResponse.isSuccessful()) {
                println("There was an error loading the conversations :( check the internet connection and try again please")
            } else {
                println("Your conversations:")
                conversationsResponse.value.conversations.forEach {
                    println("ID:${it.id}, Name: ${it.name}")
                }
            }

            uploadTestAsset(networkModule)
        }
    }

    private suspend fun uploadTestAsset(networkModule: AuthenticatedNetworkContainer) {
        val imageBytes: ByteArray = getResource("moon1.jpg")
        val uploadResult = networkModule.assetApi.uploadAsset(
            AssetMetadataRequest("image/jpeg", true, AssetRetentionType.ETERNAL, calcMd5(imageBytes)),
            imageBytes
        )
        println("The upload result is -> $uploadResult")
    }
}

fun main(args: Array<String>) = ConversationsApplication().main(args)
