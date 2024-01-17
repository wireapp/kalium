/*
 * Wire
 * Copyright (C) 2024 Wire Swiss GmbH
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

import com.wire.kalium.logic.data.call.CallType
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.feature.call.usecase.StartCallUseCase
import com.wire.kalium.logic.feature.session.CurrentSessionResult
import com.wire.kalium.testservice.models.Call
import com.wire.kalium.testservice.models.Instance
import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentHashMap
import javax.ws.rs.core.Response

sealed class CallRepository {

    companion object {

        private val CALLS: ConcurrentHashMap<String, Call> = ConcurrentHashMap<String, Call>()
        private val log = LoggerFactory.getLogger(CallRepository::class.java.name)

        suspend fun start(
            instance: Instance,
            conversationId: ConversationId,
            callType: CallType
        ): Response = instance.coreLogic.globalScope {
            when (val session = session.currentSession()) {
                is CurrentSessionResult.Success -> {
                    instance.coreLogic.sessionScope(session.accountInfo.userId) {
                        log.info("Instance ${instance.instanceId}: Start call to conversation ${conversationId.value}")
                        val call = Call(conversationId.value, conversationId.domain, status = "STARTED")
                        CALLS.putIfAbsent(call.id, call)
                        when (val result = calls.startCall.invoke(conversationId = conversationId, callType)) {
                            is StartCallUseCase.Result.Success -> {
                                Response.ok(call).build()
                            }

                            StartCallUseCase.Result.SyncFailure -> {
                                Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity(result).build()
                            }
                        }
                    }
                }

                is CurrentSessionResult.Failure -> {
                    Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity("Session failure").build()
                }
            }
        }

        suspend fun stop(instance: Instance, callId: String): Response = instance.coreLogic.globalScope {
            when (val session = session.currentSession()) {
                is CurrentSessionResult.Success -> {
                    instance.coreLogic.sessionScope(session.accountInfo.userId) {
                        if (!calls.isCallRunning()) {
                            log.error("Instance ${instance.instanceId}: No call is running")
                        }
                        val call = CALLS.get(callId)
                        call?.let {
                            log.info("Instance ${instance.instanceId}: Stop call $callId in conversation ${call.conversationId}")
                            calls.endCall.invoke(conversationId = ConversationId(call.conversationId, call.conversationDomain))
                            Response.ok(call).build()
                        }
                        Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity("Could not find call by id").build()
                    }
                }

                is CurrentSessionResult.Failure -> {
                    Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity("Session failure").build()
                }
            }
        }

    }
}
