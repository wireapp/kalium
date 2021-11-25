package com.wire.kalium

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import com.wire.kalium.api.AuthenticationManager
import com.wire.kalium.api.KtorHttpClient
import com.wire.kalium.api.conversation.ConversationApi
import com.wire.kalium.api.conversation.ConversationApiImp
import com.wire.kalium.api.message.MessageApi
import com.wire.kalium.api.message.MessageApiImp
import com.wire.kalium.api.message.SendMessageResponse
import com.wire.kalium.api.prekey.PreKeyApi
import com.wire.kalium.api.prekey.PreKeyApiImpl
import com.wire.kalium.api.user.client.ClientApi
import com.wire.kalium.api.user.client.ClientApiImp
import com.wire.kalium.api.user.client.RegisterClientRequest
import com.wire.kalium.api.user.login.LoginApi
import com.wire.kalium.api.user.login.LoginApiImp
import com.wire.kalium.api.user.login.LoginWithEmailRequest
import com.wire.kalium.crypto.Crypto
import com.wire.kalium.crypto.CryptoFile
import com.wire.kalium.models.outbound.MessageText
import com.wire.kalium.models.outbound.otr.PreKey
import com.wire.kalium.models.outbound.otr.Recipients
import com.wire.kalium.tools.HostProvider
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.features.defaultRequest
import io.ktor.client.features.json.JsonFeature
import io.ktor.client.features.json.serializer.KotlinxSerializer
import io.ktor.client.request.header
import io.ktor.client.request.host
import io.ktor.http.ContentType
import io.ktor.http.URLProtocol
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import okhttp3.logging.HttpLoggingInterceptor

private class AuthenticationManagerImp(private val accessToken: String, private val tokenType: String, val refreshToken: String) : AuthenticationManager {
    override fun accessToken(): String {
        return accessToken
    }

    override fun refreshToken(): String = refreshToken
}

class CliApplication() : CliktCommand() {
    //val convid: String by option(help = "conversation id").required()

    val email: String by option(help = "wire account email").required()
    val password: String by option(help = "wire account password").required()
    override fun run() {
        runBlocking {
            val okHttp = OkHttp.create {
                val interceptor = HttpLoggingInterceptor()
                interceptor.setLevel(HttpLoggingInterceptor.Level.BODY)
                addInterceptor(interceptor)
            }
            val loginApi: LoginApi = LoginApiImp(HttpClient(OkHttp) {
                expectSuccess = false
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
            val body = LoginWithEmailRequest(email = email, password = password, label = "ktor")
            val crypto: Crypto = CryptoFile("./data/joker")
            val loginResult = loginApi.emailLogin(body, false)
            println("login: ${loginResult.resultBody} ")
            val authenticationManager = AuthenticationManagerImp(loginResult.resultBody.accessToken, loginResult.resultBody.tokenType, "")
            val preKeys: ArrayList<PreKey> = crypto.newPreKeys(0, 100)
            val lastKey: PreKey = crypto.newLastPreKey()
            val deviceClass = "tablet"
            val type = "temporary"
            val registerClientRequest = RegisterClientRequest(
                    password = password,
                    deviceType = deviceClass,
                    label = "ktor",
                    type = type,
                    preKeys = preKeys,
                    lastKey = lastKey
            )
            val httpClient = KtorHttpClient(HostProvider, okHttp, authenticationManager).provideKtorHttpClient
            val clientApi: ClientApi = ClientApiImp(httpClient)
            val messageApi: MessageApi = MessageApiImp(httpClient)
            val conversationApi: ConversationApi = ConversationApiImp(httpClient)
            val registerClient = clientApi.registerClient(registerClientRequest)
            val preKeyApi: PreKeyApi = PreKeyApiImpl(httpClient)

            val conversationsList = conversationApi.conversationsByBatch(null, 500).resultBody.conversations
            for (conv in conversationsList) {
                println("${conv.id.value}  Name: ${conv.name}")
            }

            print("Enter conversation ID:")
            val newConversationID = readLine()!!

            // I know this code looks bad now
            val msg = MessageText("Hello from Ktor!")
            val content = msg.createGenericMsg().toByteArray()
            val param = MessageApi.Parameters.DefaultParameters(
                    sender = registerClient.resultBody.clientId,
                    data = "",
                    nativePush = false,
                    recipients = Recipients(),
                    transient = false,
            )
            val messageResult = messageApi.sendMessage(conversationId = newConversationID, option = MessageApi.MessageOption.ReportAll, parameters = param)
            when (messageResult.resultBody) {
                is SendMessageResponse.MessageSent -> {}
                is SendMessageResponse.MissingDevicesResponse -> {
                    val missing = (messageResult.resultBody as SendMessageResponse.MissingDevicesResponse).missing
                    println(missing.size)
                    val resResult = preKeyApi.getUsersPreKey(missing).resultBody
                    val enc = crypto.encryptPre(resResult, content)
                    val param1 = MessageApi.Parameters.DefaultParameters(
                            sender = registerClient.resultBody.clientId,
                            data = "",
                            nativePush = false,
                            recipients = enc,
                            transient = false,
                    )
                    println(enc.size)
                    messageApi.sendMessage(parameters = param1, newConversationID, MessageApi.MessageOption.ReportAll)
                }
            }
        }
    }
}

fun main(args: Array<String>) = CliApplication().main(args)
