package com.wire.kalium.base

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.options.counted
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import com.wire.kalium.cli.Crypto
import com.wire.kalium.cli.InMemoryCredentialsLedger
import com.wire.kalium.network.NetworkModule
import com.wire.kalium.network.api.ConversationId
import com.wire.kalium.network.api.user.client.ClientType
import com.wire.kalium.network.api.user.client.DeviceType
import com.wire.kalium.network.api.user.client.RegisterClientRequest
import com.wire.kalium.network.api.user.login.LoginWithEmailRequest
import kotlinx.coroutines.runBlocking
import java.lang.NumberFormatException

abstract class ConversationsApplication : CliktCommand() {
    private val email: String by option(help = "wire account email").required()
    private val password: String by option(help = "wire account password").required()
    protected val verbosity by option("-v").counted()

    protected lateinit var networkModule: NetworkModule
    protected lateinit var crypto: Crypto

    protected lateinit var conversationId: ConversationId
    protected lateinit var clientId: String


    open suspend fun onAppStart() {
        val credentialsLedger = InMemoryCredentialsLedger()
        networkModule = NetworkModule(credentialsLedger)

        val loginResult = networkModule.loginApi.emailLogin(
            LoginWithEmailRequest(email = email, password = password, label = "ktor"),
            false
        ).resultBody

        credentialsLedger.onAuthenticate(loginResult.accessToken, "") //TODO extract refresh token from cookie response

        registerClient()
    }

    open suspend fun onAppStarted() {
        val conversations = networkModule.conversationApi.conversationsByBatch(null, 100).resultBody.conversations
        println("Your conversations:")
        conversations.forEachIndexed { index, conversation ->
            println("($index) ID:${conversation.id}, Name: ${conversation.name}")
        }
        print("Chose a conversation from above to monitor:")
        while (true) {
            try {
                val index = readln().toInt()
                if (index in conversations.indices) {
                    conversationId = conversations[index].id
                    break
                } else {
                    echo("please chose a valid conversation from above!")
                }
            } catch (e: NumberFormatException) {
                echo("not a valid number, try again:")
            }
        }
    }

    open suspend fun onAppRunning() {}

    private suspend fun getConvRecipients() {
        /*
        val param = MessageApi.Parameters.DefaultParameters(
            sender = clientId,
            priority = MessagePriority.Low,
            nativePush = false,
            recipients = mapOf(),
            transient = false,
        )
        val messageResult =
            networkModule.messageApi.sendMessage(conversationId = conversationId, option = MessageApi.MessageOption.ReportAll, parameters = param)
        when (messageResult.resultBody) {
            is SendMessageResponse.MissingDevicesResponse -> {
                recipients = (messageResult.resultBody as SendMessageResponse.MissingDevicesResponse).missing
                // start a crypto session for the recipients
                //val emptyMessage = MessageText("").createGenericMsg().toByteArray()
                val res = networkModule.preKeyApi.getUsersPreKey(recipients)
                //crypto.encrypt(res.resultBody, emptyMessage)
            }
        }
        */
    }

    private suspend fun registerClient() {
        // register client and send preKeys
        val registerClientRequest = RegisterClientRequest(
            password = password,
            deviceType = DeviceType.Desktop,
            label = "ktor",
            type = ClientType.Temporary,
            preKeys = crypto.newPreKeys(0, 100),
            lastKey = crypto.newLastPreKey(),
            model = "model",
            capabilities = listOf()
        )
        val registerClientResponse = networkModule.clientApi.registerClient(registerClientRequest)
        clientId = registerClientResponse.resultBody.clientId
    }

    override fun run(): Unit = runBlocking {
        onAppStart()
        onAppStarted()

        onAppRunning()
    }

}
