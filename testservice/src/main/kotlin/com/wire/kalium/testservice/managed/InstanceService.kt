package com.wire.kalium.testservice.managed

import com.wire.kalium.logic.CoreLogic
import com.wire.kalium.logic.configuration.server.ServerConfig
import com.wire.kalium.logic.feature.auth.AddAuthenticatedUserUseCase
import com.wire.kalium.logic.feature.auth.AuthenticationResult
import com.wire.kalium.logic.feature.client.RegisterClientResult
import com.wire.kalium.logic.feature.client.RegisterClientUseCase
import com.wire.kalium.logic.feature.session.GetAllSessionsResult
import com.wire.kalium.logic.featureFlags.KaliumConfigs
import com.wire.kalium.testservice.models.Instance
import com.wire.kalium.testservice.models.InstanceRequest
import io.dropwizard.lifecycle.Managed
import org.slf4j.LoggerFactory
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap
import javax.ws.rs.WebApplicationException

/*
This service makes sure that instances are destroyed (used files deleted) on
shutdown of the service and also periodically checks for leftover instances
which are not needed anymore.
 */
class InstanceService : Managed {

    private val log = LoggerFactory.getLogger(InstanceService::class.java.name)
    private val instances: MutableMap<String, Instance> = ConcurrentHashMap<String, Instance>()

    override fun start() {
        log.info("Instance service started.")
    }

    override fun stop() {
        log.info("Instance service stopping...")
        log.info("Instance service stopped.")
    }

    fun getInstances(): Collection<Instance> {
        return instances.values
    }

    fun getInstance(id: String): Instance? {
        return instances.get(id)
    }

    suspend fun createInstance(instanceId: String, instanceRequest: InstanceRequest): Instance? {

        val instancePath = "${System.getProperty("user.home")}/.testservice/$instanceId"
        val coreLogic = CoreLogic("Kalium Testservice", "$instancePath/accounts", kaliumConfigs = KaliumConfigs())

        val instance = Instance(instanceRequest.backend, "", instanceId, instanceRequest.name, coreLogic, instancePath)
        instances.put(instanceId, instance)

        val serverConfig = if (instanceRequest.customBackend != null) {
            ServerConfig.Links(
                api = instanceRequest.customBackend.rest,
                webSocket = instanceRequest.customBackend.ws,
                title = instanceRequest.customBackend.name,
                accounts = ServerConfig.STAGING.accounts,
                blackList = ServerConfig.STAGING.blackList,
                teams = ServerConfig.STAGING.teams,
                website = ServerConfig.STAGING.website
            )
        } else {
            if (instanceRequest.backend == "staging") {
                ServerConfig.STAGING
            } else {
                ServerConfig.DEFAULT
            }
        }

        // login
        val loginResult = coreLogic.authenticationScope(serverConfig) {
            login(instanceRequest.email, instanceRequest.password, true).let {
                if (it !is AuthenticationResult.Success) {
                    throw WebApplicationException("Login failed, check your credentials")
                } else {
                    it.userSession
                }
            }
        }

        // save session
        val userId = coreLogic.globalScope {
            val sessions = when (val result = this.session.allSessions()) {
                is GetAllSessionsResult.Success -> result.sessions
                is GetAllSessionsResult.Failure.NoSessionFound -> emptyList()
                is GetAllSessionsResult.Failure.Generic -> throw WebApplicationException("Failed retrieve existing sessions: ${result.genericFailure}")
            }
            if (sessions.map { it.tokens.userId }.contains(loginResult.tokens.userId)) {
                this.session.updateCurrentSession(loginResult.tokens.userId)
            } else {
                val addAccountResult = addAuthenticatedAccount(loginResult, true)
                if (addAccountResult !is AddAuthenticatedUserUseCase.Result.Success) {
                    throw WebApplicationException("Failed to save session")
                }
            }
            loginResult.tokens.userId
        }

        // register client device
        coreLogic.sessionScope(userId) {
            if (client.needsToRegisterClient()) {
                when (client.register(RegisterClientUseCase.RegisterClientParam(instanceRequest.password, emptyList()))) {
                    is RegisterClientResult.Failure -> throw WebApplicationException("Client registration failed")
                    is RegisterClientResult.Success -> log.info("Login successful")
                }
            }
        }

        return instance
    }

    fun deleteInstance(id: String): Unit {
        val instance = instances.get(id)
        // TODO: logout
        // delete instances files
        instance?.instancePath?.let {
            try {
                val files = Files.walk(Path.of(instance.instancePath))

                // delete directory including files and sub-folders
                files.sorted(Comparator.reverseOrder())
                    .map { obj: Path -> obj.toFile() }
                    .forEach { obj: File -> obj.delete() }

                // close the stream
                files.close()
            } catch (e: IOException) {
                log.warn("Instance $id: Could not delete directory: ${instance.instancePath}")
            }
        }
        instances.remove(id)
    }

}
