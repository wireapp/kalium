package com.wire.kalium.cli

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import com.wire.kalium.cli.CLIUtils.getResource
import com.wire.kalium.cryptography.utils.calcMd5
import com.wire.kalium.logger.KaliumLogLevel
import com.wire.kalium.logic.configuration.ServerConfig
import com.wire.kalium.logic.configuration.ServerConfigMapper
import com.wire.kalium.logic.configuration.ServerConfigMapperImpl
import com.wire.kalium.network.AuthenticatedNetworkContainer
import com.wire.kalium.network.NetworkLogger
import com.wire.kalium.network.UnauthenticatedNetworkContainer
import com.wire.kalium.network.api.SessionDTO
import com.wire.kalium.network.api.asset.AssetMetadataRequest
import com.wire.kalium.network.api.model.AccessTokenDTO
import com.wire.kalium.network.api.model.AssetRetentionType
import com.wire.kalium.network.api.model.RefreshTokenDTO
import com.wire.kalium.network.api.user.login.LoginApi
import com.wire.kalium.network.session.SessionManager
import com.wire.kalium.network.tools.ServerConfigDTO
import com.wire.kalium.network.utils.isSuccessful
import kotlinx.coroutines.runBlocking

class InMemorySessionManager(
    private val serverConfigDTO: ServerConfigDTO,
    private var session: SessionDTO
) : SessionManager {

    override fun session(): Pair<SessionDTO, ServerConfigDTO> = Pair(session, serverConfigDTO)

    override fun updateSession(newAccessTokenDTO: AccessTokenDTO, newRefreshTokenDTO: RefreshTokenDTO?): SessionDTO =
        SessionDTO(
            session.userId,
            newAccessTokenDTO.tokenType,
            newAccessTokenDTO.value,
            newRefreshTokenDTO?.value ?: session.refreshToken
        )

    override fun onSessionExpired() {
        throw IllegalAccessError("cookie expired userId: ${session().first.userId}")
    }
}

class ConversationsApplication : CliktCommand() {
    private val email: String by option(help = "wire account email").required()
    private val password: String by option(help = "wire account password").required()

    override fun run(): Unit = runBlocking {
        NetworkLogger.setLoggingLevel(level = KaliumLogLevel.DEBUG)

        val serverConfigMapper: ServerConfigMapper = ServerConfigMapperImpl()
        val serverConfigDTO: ServerConfigDTO = serverConfigMapper.toDTO(ServerConfig.DEFAULT)
        val loginContainer = UnauthenticatedNetworkContainer()

        val loginResult = loginContainer.loginApi.login(
            LoginApi.LoginParam.LoginWithEmail(email = email, password = password, label = "ktor"),
            false,
            serverConfigDTO.apiBaseUrl.toString()
        )

        if (!loginResult.isSuccessful()) {
            println("There was an error on the login :( check the credentials and the internet connection and try again please")
        } else {
            val sessionData = loginResult.value
            val networkModule = AuthenticatedNetworkContainer(InMemorySessionManager(serverConfigDTO, sessionData))
            val conversationsResponse = networkModule.conversationApi.fetchConversationsIds(null)

            if (!conversationsResponse.isSuccessful()) {
                println("There was an error loading the conversations :( check the internet connection and try again please")
            } else {
                println("Your conversations:")
                conversationsResponse.value.conversationsIds.forEach {
                    println("ID:${it.value}, Name: ${it.domain}")
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
