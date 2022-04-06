package com.wire.kalium.persistence.server_config

import com.wire.kalium.persistence.kmm_settings.KaliumPreferences
import com.wire.kalium.persistence.model.ServerConfigEntity
import kotlinx.serialization.Serializable
import kotlin.jvm.JvmInline

interface ServerConfigStorage {
    fun storeConfig(serverConfig: ServerConfigEntity): ServerConfigEntity
    fun deleteConfig(title: String)
    fun deleteConfig(serverConfig: ServerConfigEntity)
    fun storedConfig(): List<ServerConfigEntity>?
    fun serverConfigByTitle(title: String): ServerConfigEntity?

    fun currentAuthServer(): ServerConfigEntity
    fun updateCurrentAuthServer(title: String)
}


class ServerConfigStorageImpl(private val kaliumPreferences: KaliumPreferences) : ServerConfigStorage {

    override fun storeConfig(serverConfig: ServerConfigEntity): ServerConfigEntity =
        kaliumPreferences.getSerializable(SERVER_CONFIG_KEY, ServerConfigMap.serializer())?.data?.let { serverConfigMap ->
            serverConfigMap[serverConfig.title] ?: run {
                val temp = serverConfigMap.toMutableMap()
                temp[serverConfig.title] = serverConfig
                storeConfigMap(temp)
                serverConfig
            }
        } ?: run {
            storeConfigMap(mapOf(serverConfig.title to serverConfig))
            serverConfig
        }

    override fun deleteConfig(title: String) {
        kaliumPreferences.getSerializable(SERVER_CONFIG_KEY, ServerConfigMap.serializer())?.let { serverConfigMap ->
            val temp = serverConfigMap.data.toMutableMap()
            temp.remove(title)
            storeConfigMap(temp)
        }
    }

    override fun deleteConfig(serverConfig: ServerConfigEntity) {
        deleteConfig(serverConfig.title)
    }

    override fun storedConfig(): List<ServerConfigEntity> =
        kaliumPreferences.getSerializable(SERVER_CONFIG_KEY, ServerConfigMap.serializer())?.data?.values?.toList() ?: run {
            listOf(storeConfig(ServerConfigEntity.PRODUCTION))
        }

    override fun serverConfigByTitle(title: String): ServerConfigEntity? =
        kaliumPreferences.getSerializable(SERVER_CONFIG_KEY, ServerConfigMap.serializer())?.data?.get(title)

    override fun currentAuthServer(): ServerConfigEntity = kaliumPreferences.getString(CURRENT_AUTH_SERVER_KEY)?.let { currentConfigTitle ->
        serverConfigByTitle(currentConfigTitle) ?: run {
            storedConfig().first().also {
                updateCurrentAuthServer(it.title)
            }
        }
    } ?: run {
        storedConfig().first().also {
            updateCurrentAuthServer(it.title)
        }
    }


    override fun updateCurrentAuthServer(title: String) = kaliumPreferences.putString(CURRENT_AUTH_SERVER_KEY, title)

    private fun storeConfigMap(map: Map<String, ServerConfigEntity>) =
        kaliumPreferences.putSerializable(SERVER_CONFIG_KEY, ServerConfigMap(map), ServerConfigMap.serializer())

    private companion object {
        const val SERVER_CONFIG_KEY = "server-config-key"
        const val CURRENT_AUTH_SERVER_KEY = "current-auth-server"
    }
}

@Serializable
@JvmInline
private value class ServerConfigMap(val data: Map<String, ServerConfigEntity>)
