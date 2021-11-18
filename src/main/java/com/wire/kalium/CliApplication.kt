package com.wire.kalium

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import com.wire.kalium.api.KtorHttpClient
import com.wire.kalium.api.user.client.ClientApiImp
import com.wire.kalium.api.user.client.RegisterClientRequest
import com.wire.kalium.api.user.login.LoginApi
import com.wire.kalium.api.user.login.LoginApiImp
import com.wire.kalium.api.user.login.LoginWithEmailRequest
import com.wire.kalium.crypto.Crypto
import com.wire.kalium.crypto.CryptoFile
import com.wire.kalium.models.outbound.otr.PreKey
import kotlinx.coroutines.runBlocking


class CliApplication() : CliktCommand() {
    val convid: String by option(help="conversation id").required()
    val message: String by option(help="message").required()

    //val email: String by option(help = "wire account email").prompt(text = "email ")
    //val password: String by option(help = "wire account password").prompt(text = "password ")
    override fun run() {
        runBlocking {
            val httpClient = KtorHttpClient().ktorHttpClient
            val loginApi: LoginApi = LoginApiImp(httpClient)
            val body = LoginWithEmailRequest(email = "dejan@wire.com", password = "12345678", label = "ktor")
            val crypto: Crypto = CryptoFile("./data/joker")
            val loginResult = loginApi.emailLogin(body, false)
            println(loginResult.resultBody)
            val preKeys: ArrayList<PreKey> = crypto.newPreKeys(0, 20)
            val lastKey: PreKey = crypto.newLastPreKey()
            val deviceClass = "tablet"
            val type = "temporary"
            val registerClientRequest = RegisterClientRequest(
                    password = "12345678",
                    deviceType = deviceClass,
                    label = "ktor",
                    type = type,
                    preKeys = preKeys,
                    lastKey = lastKey
            )
            val registerClient = ClientApiImp(httpClient).registerClient(registerClientRequest, loginResult.resultBody.accessToken)
            println(registerClient.resultBody)
        }

        // login stuff here
        /*val conversationId = UUID.fromString(convid)
        val httpClient: Client = ClientBuilder
                .newClient()
        val loggingFeature = LoggingFeature(Logger.getLOGGER(), Level.INFO, null, null)
        httpClient.register(loggingFeature)

        val crypto = CryptoFile("/tmp/joker")
        val app = Application("dejan+joker@wire.com", "12345678")
                .addClient(httpClient)
                .addCrypto(crypto)

        // Login, create device if needed, setup token refresh timer, pull missed messages and more
        app.start()

        // Create WireClient for this conversationId
        val wireClient = app.getWireClient(conversationId)

        // Send text
        wireClient.send(MessageText("Hi there from Kotlin!"))

        app.stop()

         */
    }
}

fun main(args: Array<String>) =  CliApplication().main(args)
