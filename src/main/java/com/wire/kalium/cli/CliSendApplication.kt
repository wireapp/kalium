package com.wire.kalium.cli

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import com.wire.kalium.api.AuthenticationManager
import com.wire.kalium.api.KtorHttpClient
import com.wire.kalium.api.conversation.ConversationApi
import com.wire.kalium.api.conversation.ConversationApiImp
import com.wire.kalium.api.message.MessageApi
import com.wire.kalium.api.message.MessageApiImp
import com.wire.kalium.api.message.MessagePriority
import com.wire.kalium.api.message.SendMessageResponse
import com.wire.kalium.api.message.UserIdToClientMap
import com.wire.kalium.api.prekey.PreKeyApi
import com.wire.kalium.api.prekey.PreKeyApiImpl
import com.wire.kalium.api.user.client.ClientApi
import com.wire.kalium.api.user.client.ClientApiImp
import com.wire.kalium.api.user.client.ClientType
import com.wire.kalium.api.user.client.DeviceType
import com.wire.kalium.api.user.client.RegisterClientRequest
import com.wire.kalium.api.user.login.LoginApi
import com.wire.kalium.api.user.login.LoginApiImp
import com.wire.kalium.api.user.login.LoginWithEmailRequest
import com.wire.kalium.crypto.Crypto
import com.wire.kalium.crypto.CryptoFile
import com.wire.kalium.models.outbound.MessageText
import com.wire.kalium.models.outbound.otr.Recipients
import com.wire.kalium.tools.HostProvider
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.features.defaultRequest
import io.ktor.client.features.json.JsonFeature
import io.ktor.client.features.json.serializer.KotlinxSerializer
import io.ktor.client.features.websocket.webSocket
import io.ktor.client.request.header
import io.ktor.client.request.host
import io.ktor.http.ContentType
import io.ktor.http.HttpMethod
import io.ktor.http.URLProtocol
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import okhttp3.logging.HttpLoggingInterceptor


class CliSendApplication() : CliktCommand() {
    val email: String by option(help = "wire account email").required()
    val password: String by option(help = "wire account password").required()

    private lateinit var loginApi: LoginApi
    private lateinit var conversationApi: ConversationApi
    private lateinit var messageApi: MessageApi
    private lateinit var preKeyApi: PreKeyApi
    private lateinit var clientApi: ClientApi
    private lateinit var authenticationManager: AuthenticationManager
    private lateinit var appHttpClient: HttpClient

    private lateinit var crypto: Crypto

    private lateinit var clientId: String
    private lateinit var conversationId: String
    private lateinit var recipients: UserIdToClientMap

    override fun run() = runBlocking {
        // initialize the login api with a ktor http client
        loginApi = LoginApiImp(HttpClient(OkHttp) {
            defaultRequest {
                header("Content-Type", "application/json")
                host = HostProvider.host
                url.protocol = URLProtocol.HTTPS
            }
            install(JsonFeature) {
                serializer = KotlinxSerializer(Json {
                    prettyPrint = true
                    isLenient = true
                    ignoreUnknownKeys = true
                })
                accept(ContentType.Application.Json)
            }
        })

        val loginResult = loginApi.emailLogin(
                LoginWithEmailRequest(email = email, password = password, label = "ktor"),
                false
        ).resultBody

        // initialize Crypto box
        crypto = CryptoFile("./data/${loginResult.userId}")

        authenticationManager = AuthenticationManagerImpl(
                loginResult.accessToken,
                loginResult.tokenType,
                "" // TODO: Extract zuid cookie after login
        )

        val okHttp = OkHttp.create {
            val interceptor = HttpLoggingInterceptor()
            interceptor.setLevel(HttpLoggingInterceptor.Level.BODY)
            //addInterceptor(interceptor)
        }
        appHttpClient = KtorHttpClient(HostProvider, okHttp, authenticationManager).provideKtorHttpClient
        clientApi = ClientApiImp(appHttpClient)
        messageApi = MessageApiImp(appHttpClient)
        conversationApi = ConversationApiImp(appHttpClient)
        preKeyApi = PreKeyApiImpl(appHttpClient)

        // register client and send preKeys
        val registerClientRequest = RegisterClientRequest(
                password = password,
                deviceType = DeviceType.Desktop,
                label = "ktor",
                type = ClientType.Temporary,
                preKeys = crypto.newPreKeys(0, 100),
                lastKey = crypto.newLastPreKey()
        )
        val registerClientResponse = clientApi.registerClient(registerClientRequest)
        clientId = registerClientResponse.resultBody.clientId

        val conversationsList = conversationApi.conversationsByBatch(null, 500).resultBody.conversations
        for (conv in conversationsList) {
            println("${conv.id.value}  Name: ${conv.name}")
        }

        print("Enter conversation ID:")
        conversationId = readLine()!!
        getConvRecipients()

        while (true) {
            val message = readLine()!!
            sendTextMessage(message = message)
        }
    }

    private suspend fun sendTextMessage(message: String) {
        val msg = MessageText(message)
        val content = msg.createGenericMsg().toByteArray()
        val encryptedMessage = crypto.encrypt(recipients, content)
        val param = MessageApi.Parameters.DefaultParameters(
                sender = clientId,
                priority = MessagePriority.LOW,
                nativePush = false,
                recipients = encryptedMessage,
                transient = false,
        )
        val messageResult = messageApi.sendMessage(conversationId = conversationId, option = MessageApi.MessageOption.ReportAll, parameters = param)
        when (messageResult.resultBody) {
            is SendMessageResponse.MessageSent -> {}
            is SendMessageResponse.MissingDevicesResponse -> {
                getConvRecipients()
                sendTextMessage(message)
            }
        }
    }

    private suspend fun getConvRecipients() {
        val param = MessageApi.Parameters.DefaultParameters(
                sender = clientId,
                priority = MessagePriority.LOW,
                nativePush = false,
                recipients = Recipients(),
                transient = false,
        )
        val messageResult = messageApi.sendMessage(conversationId = conversationId, option = MessageApi.MessageOption.ReportAll, parameters = param)
        when (messageResult.resultBody) {
            is SendMessageResponse.MissingDevicesResponse -> {
                recipients = (messageResult.resultBody as SendMessageResponse.MissingDevicesResponse).missing
                // start a crypto session for the recipients
                val emptyMessage = MessageText("").createGenericMsg().toByteArray()
                val res = preKeyApi.getUsersPreKey(recipients)
                crypto.encryptPre(res.resultBody, emptyMessage)
            }
        }
    }
}

fun main(args: Array<String>) = CliSendApplication().main(args)
