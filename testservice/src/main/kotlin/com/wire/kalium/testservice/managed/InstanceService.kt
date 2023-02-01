/*
 * Wire
 * Copyright (C) 2023 Wire Swiss GmbH
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see http://www.gnu.org/licenses/.
 */

package com.wire.kalium.testservice.managed

import com.codahale.metrics.Gauge
import com.codahale.metrics.MetricRegistry
import com.wire.kalium.logger.KaliumLogLevel
import com.wire.kalium.logic.CoreLogger
import com.wire.kalium.logic.CoreLogic
import com.wire.kalium.logic.configuration.server.ServerConfig
import com.wire.kalium.logic.data.client.ClientType
import com.wire.kalium.logic.data.client.DeleteClientParam
import com.wire.kalium.logic.data.conversation.ClientId
import com.wire.kalium.logic.data.logout.LogoutReason
import com.wire.kalium.logic.feature.auth.AddAuthenticatedUserUseCase
import com.wire.kalium.logic.feature.auth.AuthenticationResult
import com.wire.kalium.logic.feature.auth.AuthenticationScope
import com.wire.kalium.logic.feature.auth.autoVersioningAuth.AutoVersionAuthScopeUseCase
import com.wire.kalium.logic.feature.client.GetProteusFingerprintResult
import com.wire.kalium.logic.feature.client.RegisterClientResult
import com.wire.kalium.logic.feature.client.RegisterClientUseCase
import com.wire.kalium.logic.feature.session.CurrentSessionResult
import com.wire.kalium.logic.featureFlags.KaliumConfigs
import com.wire.kalium.testservice.models.FingerprintResponse
import com.wire.kalium.testservice.models.Instance
import com.wire.kalium.testservice.models.InstanceRequest
import io.dropwizard.lifecycle.Managed
import kotlinx.coroutines.runBlocking
import org.slf4j.LoggerFactory
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap
import javax.ws.rs.WebApplicationException
import javax.ws.rs.core.Response

/*
This service makes sure that instances are destroyed (used files deleted) on
shutdown of the service and also periodically checks for leftover instances
which are not needed anymore.
 */
class InstanceService(val metricRegistry: MetricRegistry) : Managed {

    private val log = LoggerFactory.getLogger(InstanceService::class.java.name)
    private val instances: MutableMap<String, Instance> = ConcurrentHashMap<String, Instance>()

    override fun start() {
        log.info("Instance service started.")

        // metrics
        metricRegistry.register(MetricRegistry.name("testservice", "instances", "total", "size"),
            Gauge { instances.size })
        metricRegistry.register(
            MetricRegistry.name("testservice", "instances", "startup", "avg"),
            Gauge {
                instances.values
                    .filter { it.startupTime != 0L }
                    .map { it.startupTime?.toDouble() ?: 0.0 }
                    .average()
            }
        )
    }

    override fun stop() {
        log.info("Instance service stopping...")
        instances.forEach { instance ->
            log.info("Instance ${instance.key}: stopping")
            deleteInstance(instance.key)
        }
        log.info("Instance service stopped.")
    }

    fun getInstances(): Collection<Instance> {
        return instances.values
    }

    fun getInstance(id: String): Instance? {
        return instances.get(id)
    }

    fun getInstanceOrThrow(id: String): Instance {
        return instances.get(id) ?: throw WebApplicationException(
            "Instance $id: Instance not found or already destroyed"
        )
    }

    @Suppress("LongMethod", "ThrowsCount")
    suspend fun createInstance(instanceId: String, instanceRequest: InstanceRequest): Instance {
        val before = System.currentTimeMillis()
        val instancePath = System.getProperty("user.home") +
                File.separator + ".testservice" + File.separator + instanceId
        log.info("Instance $instanceId: Creating $instancePath")
        val kaliumConfigs = KaliumConfigs(developmentApiEnabled = true)
        val coreLogic = CoreLogic("$instancePath", kaliumConfigs)
        CoreLogger.setLoggingLevel(KaliumLogLevel.VERBOSE)

        val serverConfig = if (instanceRequest.customBackend != null) {
            ServerConfig.Links(
                api = instanceRequest.customBackend.rest,
                webSocket = instanceRequest.customBackend.ws,
                title = instanceRequest.customBackend.name,
                accounts = ServerConfig.STAGING.accounts,
                blackList = ServerConfig.STAGING.blackList,
                teams = ServerConfig.STAGING.teams,
                website = ServerConfig.STAGING.website,
                isOnPremises = true,
                apiProxy = null
            )
        } else {
            if (instanceRequest.backend == "staging") {
                ServerConfig.STAGING
            } else {
                ServerConfig.DEFAULT
            }
        }

        log.info("Instance $instanceId: Login with ${instanceRequest.email} on ${instanceRequest.backend}")
        val loginResult = provideVersionedAuthenticationScope(coreLogic, serverConfig)
            .login(instanceRequest.email, instanceRequest.password, true).let {
                if (it !is AuthenticationResult.Success) {
                    throw WebApplicationException("Instance $instanceId: Login failed, check your credentials")
                } else {
                    it
                }
            }

        log.info("Instance $instanceId: Save Session")
        val userId = coreLogic.globalScope {
            val addAccountResult = addAuthenticatedAccount(
                loginResult.serverConfigId, loginResult.ssoID, loginResult.authData, null, true
            )
            if (addAccountResult !is AddAuthenticatedUserUseCase.Result.Success) {
                throw WebApplicationException("Instance $instanceId: Failed to save session")
            }
            loginResult.authData.userId
        }

        var clientId: String? = null

        log.info("Instance $instanceId: Register client device")
        runBlocking {
            coreLogic.sessionScope(userId) {
                if (client.needsToRegisterClient()) {
                    when (val result = client.getOrRegister(
                        RegisterClientUseCase.RegisterClientParam(instanceRequest.password, emptyList(), ClientType.Permanent)
                    )) {
                        is RegisterClientResult.Failure ->
                            throw WebApplicationException("Instance $instanceId: Client registration failed")
                        is RegisterClientResult.Success -> {
                            clientId = result.client.id.value
                            log.info("Instance $instanceId: Login with new device $clientId successful")
                            syncManager.waitUntilLive()
                        }
                    }
                }
            }
        }

        val instance = Instance(
            instanceRequest.backend,
            clientId,
            instanceId,
            instanceRequest.name,
            coreLogic,
            instancePath,
            instanceRequest.password,
            System.currentTimeMillis() - before
        )
        instances.put(instanceId, instance)

        return instance
    }

    fun deleteInstance(id: String) {
        val instance = getInstanceOrThrow(id)
        log.info("Instance $id: Delete device ${instance.clientId} and logout")
        instance.coreLogic?.globalScope {
            val result = session.currentSession()
            if (result is CurrentSessionResult.Success) {
                instance.coreLogic.sessionScope(result.accountInfo.userId) {
                    instance.clientId?.let {
                        runBlocking {
                            client.deleteClient(DeleteClientParam(instance.password, ClientId(instance.clientId)))
                        }
                    }
                    log.info("Instance $id: Device ${instance.clientId} deleted")
                    runBlocking { logout(LogoutReason.SELF_SOFT_LOGOUT) }
                }
            }
            log.info("Instance $id: Delete sessions in preference file")
            // TODO Something like session.allSessions.deleteInvalidSession()
        }
        log.info("Instance $id: Logged out")
        log.info("Instance $id: Delete locate files in ${instance.instancePath}")
        instance.instancePath?.let {
            try {
                val files = Files.walk(Path.of(instance.instancePath))

                // delete directory including files and sub-folders
                files.sorted(Comparator.reverseOrder())
                    .map { obj: Path -> obj.toFile() }
                    .forEach { obj: File -> obj.delete() }

                // close the stream
                files.close()
            } catch (e: IOException) {
                log.warn("Instance $id: Could not delete directory ${instance.instancePath}: ${e.message}")
            }
        }
        instances.remove(id)
    }

    private suspend fun provideVersionedAuthenticationScope(coreLogic: CoreLogic, serverLinks: ServerConfig.Links): AuthenticationScope =
        when (val result = coreLogic.versionedAuthenticationScope(serverLinks).invoke()) {
            is AutoVersionAuthScopeUseCase.Result.Failure.Generic ->
                throw WebApplicationException("failed to create authentication scope: ${result.genericFailure}")

            AutoVersionAuthScopeUseCase.Result.Failure.TooNewVersion ->
                throw WebApplicationException("failed to create authentication scope: api version not supported")

            AutoVersionAuthScopeUseCase.Result.Failure.UnknownServerVersion ->
                throw WebApplicationException("failed to create authentication scope: unknown server version")

            is AutoVersionAuthScopeUseCase.Result.Success -> result.authenticationScope
        }

    fun getFingerprint(id: String): Response {
        log.info("Instance $id: Get fingerprint of client")
        val instance = getInstanceOrThrow(id)
        instance.coreLogic?.globalScope {
            val result = session.currentSession()
            if (result is CurrentSessionResult.Success) {
                instance.coreLogic.sessionScope(result.accountInfo.userId) {
                    return runBlocking {
                        when (val fingerprint = client.getProteusFingerprint()) {
                            is GetProteusFingerprintResult.Success -> {
                                return@runBlocking Response.status(Response.Status.OK).entity(
                                    FingerprintResponse(fingerprint.fingerprint, id)
                                ).build()
                            }

                            is GetProteusFingerprintResult.Failure -> {
                                return@runBlocking Response.status(Response.Status.NO_CONTENT)
                                    .entity("Instance $id: Cannot get fingerprint: "
                                            + fingerprint.genericFailure).build()
                            }
                        }
                    }
                }
            } else {
                return Response.status(Response.Status.NOT_FOUND)
                    .entity("Instance $id: No current session found").build()
            }
        }
        throw WebApplicationException("Instance $id: No client assigned to instance yet")
    }

}
