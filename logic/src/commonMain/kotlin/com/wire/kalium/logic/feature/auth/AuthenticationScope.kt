package com.wire.kalium.logic.feature.auth

import com.benasher44.uuid.uuid4
import com.wire.kalium.logic.configuration.server.ServerConfig
import com.wire.kalium.logic.configuration.server.ServerConfigMapper
import com.wire.kalium.logic.data.auth.login.LoginRepository
import com.wire.kalium.logic.data.auth.login.LoginRepositoryImpl
import com.wire.kalium.logic.data.auth.login.SSOLoginRepository
import com.wire.kalium.logic.data.auth.login.SSOLoginRepositoryImpl
import com.wire.kalium.logic.data.register.RegisterAccountDataSource
import com.wire.kalium.logic.data.register.RegisterAccountRepository
import com.wire.kalium.logic.di.MapperProvider
import com.wire.kalium.logic.feature.auth.sso.SSOLoginScope
import com.wire.kalium.logic.feature.register.RegisterScope
import com.wire.kalium.network.ServerMetaDataManager
import com.wire.kalium.network.UnauthenticatedNetworkContainer
import com.wire.kalium.network.tools.ServerConfigDTO
import com.wire.kalium.persistence.dao_kalium_db.ServerConfigurationDAO
import com.wire.kalium.persistence.db.GlobalDatabaseProvider
import com.wire.kalium.persistence.kmm_settings.KaliumPreferences

class AuthenticationScope(
    private val clientLabel: String,
    private val globalPreferences: KaliumPreferences,
    private val globalDataBae: GlobalDatabaseProvider,
    private val backendLinks: ServerConfig.Links
) {

    private val unauthenticatedNetworkContainer: UnauthenticatedNetworkContainer by lazy {
        UnauthenticatedNetworkContainer(
            MapperProvider.serverConfigMapper().toDTO(backendLinks),
            serverMetaDataManager = ServerMetaDataManagerImpl(globalDataBae.serverConfigurationDAO)
        )
    }
    private val loginRepository: LoginRepository get() = LoginRepositoryImpl(unauthenticatedNetworkContainer.loginApi, clientLabel)

    private val registerAccountRepository: RegisterAccountRepository
        get() = RegisterAccountDataSource(
            unauthenticatedNetworkContainer.registerApi
        )
    private val ssoLoginRepository: SSOLoginRepository get() = SSOLoginRepositoryImpl(unauthenticatedNetworkContainer.sso)

    val validateEmailUseCase: ValidateEmailUseCase get() = ValidateEmailUseCaseImpl()
    val validateUserHandleUseCase: ValidateUserHandleUseCase get() = ValidateUserHandleUseCaseImpl()
    val validatePasswordUseCase: ValidatePasswordUseCase get() = ValidatePasswordUseCaseImpl()

    val login: LoginUseCase get() = LoginUseCaseImpl(loginRepository, validateEmailUseCase, validateUserHandleUseCase, backendLinks)
    val register: RegisterScope get() = RegisterScope(registerAccountRepository, backendLinks)
    val ssoLoginScope: SSOLoginScope get() = SSOLoginScope(ssoLoginRepository)
}

class ServerMetaDataManagerImpl internal constructor(
    private val serverConfigurationDAO: ServerConfigurationDAO,
    private val serverConfigMapper: ServerConfigMapper = MapperProvider.serverConfigMapper()
) : ServerMetaDataManager {

    override fun getLocalMetaData(backendLinks: ServerConfigDTO.Links): ServerConfigDTO? = with(backendLinks) {
        serverConfigurationDAO.configByLinks(title, api.toString(), webSocket.toString())
    }?.let { serverConfigMapper.toDTO(it) }

    override fun storeBackend(links: ServerConfigDTO.Links, metaData: ServerConfigDTO.MetaData): ServerConfigDTO {
        val newId = uuid4().toString()
        serverConfigurationDAO.insert(
            ServerConfigurationDAO.InsertData(
                id = newId,
                title = links.title,
                apiBaseUrl = links.api.toString(),
                accountBaseUrl = links.accounts.toString(),
                webSocketBaseUrl = links.webSocket.toString(),
                blackListUrl = links.blackList.toString(),
                websiteUrl = links.website.toString(),
                teamsUrl = links.teams.toString(),
                federation = metaData.federation,
                commonApiVersion = metaData.commonApiVersion.version,
                domain = metaData.domain
            )
        )
        return ServerConfigDTO(newId, links, metaData)
    }
}
