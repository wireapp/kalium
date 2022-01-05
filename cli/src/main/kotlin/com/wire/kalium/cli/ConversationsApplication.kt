package com.wire.kalium.cli

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import com.wire.kalium.cryptography.utils.calcMd5
import com.wire.kalium.network.AuthenticatedNetworkContainer
import com.wire.kalium.network.LoginNetworkContainer
import com.wire.kalium.network.api.SessionCredentials
import com.wire.kalium.network.api.model.AssetMetadata
import com.wire.kalium.network.api.model.AssetRetentionType
import com.wire.kalium.network.api.user.login.LoginWithEmailRequest
import com.wire.kalium.network.utils.isSuccessful
import kotlinx.coroutines.runBlocking
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream

class ConversationsApplication : CliktCommand() {
    private val email: String by option(help = "wire account email").required()
    private val password: String by option(help = "wire account password").required()

    override fun run(): Unit = runBlocking {

        val loginContainer = LoginNetworkContainer()

        val loginResult = loginContainer.loginApi.emailLogin(
            LoginWithEmailRequest(email = email, password = password, label = "ktor"), false
        )

        if (!loginResult.isSuccessful()) {
            println("There was an error on the login :( check the credentials and the internet connection and try again please")
        } else {
            val sessionData = loginResult.value
            //TODO: Get them 🍪 refresh token
            val sessionCredentials = SessionCredentials(sessionData.tokenType, sessionData.accessToken, "refreshToken")
            val networkModule = AuthenticatedNetworkContainer(sessionCredentials)
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
        val imageBytes: ByteArray? = getResource("moon1.jpg")
        val uploadResult = networkModule.assetApi.uploadAsset(
            AssetMetadata("image/jpeg", true, AssetRetentionType.ETERNAL, calcMd5(imageBytes)),
            imageBytes ?: ByteArray(16)
        )
        println("The upload result -> $uploadResult")
    }

    @Throws(IOException::class)
    fun getResource(name: String?): ByteArray? {
        val classLoader = this::class.java.classLoader
        classLoader.getResourceAsStream(name).use { resourceAsStream -> return toByteArray(resourceAsStream) }
    }

    @Throws(IOException::class)
    fun toByteArray(input: InputStream?): ByteArray? = input?.let { inputStream ->
        ByteArrayOutputStream().use { output ->
            var n: Int
            val buffer = ByteArray(1024 * 4)
            while (-1 != inputStream.read(buffer).also { n = it }) {
                output.write(buffer, 0, n)
            }
            return output.toByteArray()
        }
    }
}

fun main(args: Array<String>) = ConversationsApplication().main(args)
