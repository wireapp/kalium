package com.wire.kalium

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import com.wire.kalium.api.AuthenticationManager
import com.wire.kalium.api.KtorHttpClient
import com.wire.kalium.api.user.client.ClientApiImp
import com.wire.kalium.api.user.client.RegisterClientRequest
import com.wire.kalium.api.user.login.LoginApi
import com.wire.kalium.api.user.login.LoginApiImp
import com.wire.kalium.api.user.login.LoginWithEmailRequest
import com.wire.kalium.api.user.logout.LogoutApiImp
import com.wire.kalium.crypto.Crypto
import com.wire.kalium.crypto.CryptoFile
import com.wire.kalium.models.outbound.otr.PreKey
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
    override fun accessToken(): String = accessToken
    override fun refreshToken(): String = refreshToken
}

class CliApplication() : CliktCommand() {
    val convid: String by option(help = "conversation id").required()
    val message: String by option(help = "message").required()

    //val email: String by option(help = "wire account email").prompt(text = "email ")
    //val password: String by option(help = "wire account password").prompt(text = "password ")
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
            val body = LoginWithEmailRequest(email = "dejan@wire.com", password = "12345678", label = "ktor")
            val crypto: Crypto = CryptoFile("./data/joker")
            val loginResult = loginApi.emailLogin(body, false)
            println("login: $loginResult ")
            val authenticationManager = AuthenticationManagerImp(loginResult.accessToken, loginResult.tokenType, "")
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
            val httpClient = KtorHttpClient(HostProvider, okHttp, authenticationManager).provideKtorHttpClient
            val registerClient = ClientApiImp(httpClient).registerClient(registerClientRequest)
            println(registerClient.resultBody)
            val logoutResponse = LogoutApiImp(httpClient).logout(authenticationManager.accessToken())
            println(logoutResponse)
        }
    }
}

fun main(args: Array<String>) = CliApplication().main(args)
