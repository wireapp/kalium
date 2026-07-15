@file:OptIn(
    com.wire.kalium.calling.runtime.ExperimentalCallingRuntimeApi::class,
    com.wire.kalium.conversation.ExperimentalConversationApi::class,
    com.wire.kalium.logic.service.api.ExperimentalKaliumServiceApi::class,
)
@file:Suppress("TooGenericExceptionCaught")

/*
 * Wire
 * Copyright (C) 2026 Wire Swiss GmbH
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.wire.kalium.logic.service

import com.wire.kalium.calling.runtime.SelfConversationResult
import com.wire.kalium.calling.runtime.SelfConversationTarget
import com.wire.kalium.calling.runtime.ServiceSelfConversationProvider
import com.wire.kalium.conversation.CallConversationProtocol
import com.wire.kalium.logic.data.id.GroupID
import com.wire.kalium.logic.data.id.QualifiedID
import com.wire.kalium.network.api.authenticated.conversation.ConvProtocol
import com.wire.kalium.network.api.authenticated.conversation.ConversationResponse
import com.wire.kalium.network.utils.NetworkResponse
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Discovers the self-conversations AVS needs for signalling the account's other clients. */
internal class WireRemoteSelfConversationProvider(
    private val owner: JvmServiceNetworkOwner,
) : ServiceSelfConversationProvider {
    private val mutex = Mutex()
    private var cached: List<SelfConversationTarget>? = null

    override suspend fun getSelfConversations(): SelfConversationResult = mutex.withLock {
        cached?.let { return@withLock SelfConversationResult.Success(it) }
        try {
            when (val discovered = discover()) {
                is SelfConversationResult.Failure -> discovered
                is SelfConversationResult.Success -> discovered.also { cached = it.targets }
            }
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (failure: Throwable) {
            SelfConversationResult.Failure("Remote self-conversation discovery failed", failure)
        }
    }

    @Suppress("ReturnCount")
    private suspend fun discover(): SelfConversationResult {
        val api = owner.requireNetwork().conversationApi
        val targets = mutableListOf<SelfConversationTarget>()
        var pagingState: String? = null
        do {
            val page = when (val response = api.fetchConversationsIds(pagingState)) {
                is NetworkResponse.Error -> return SelfConversationResult.Failure(
                    "Unable to list conversations while discovering self-conversations",
                    response.kException,
                )
                is NetworkResponse.Success -> response.value
            }
            if (page.conversationsIds.isNotEmpty()) {
                val details = when (val response = api.fetchConversationsListDetails(page.conversationsIds)) {
                    is NetworkResponse.Error -> return SelfConversationResult.Failure(
                        "Unable to fetch conversation details while discovering self-conversations",
                        response.kException,
                    )
                    is NetworkResponse.Success -> response.value.conversationsFound
                }
                details.filter { it.type == ConversationResponse.Type.SELF }
                    .mapNotNullTo(targets) { it.toSelfConversationTarget() }
            }
            pagingState = page.pagingState
        } while (page.hasMore)

        val distinct = targets.distinctBy { it.conversationId }
        return if (distinct.isEmpty()) {
            SelfConversationResult.Failure("The backend returned no self-conversations for this account")
        } else {
            SelfConversationResult.Success(distinct)
        }
    }

    private fun ConversationResponse.toSelfConversationTarget(): SelfConversationTarget? {
        val callProtocol = when (protocol) {
            ConvProtocol.PROTEUS -> CallConversationProtocol.Proteus
            ConvProtocol.MLS -> groupId?.let { CallConversationProtocol.Mls(GroupID(it), epoch) }
            ConvProtocol.MIXED -> groupId?.let { CallConversationProtocol.Mixed(GroupID(it), epoch) }
        } ?: return null
        return SelfConversationTarget(QualifiedID(id.value, id.domain), callProtocol)
    }
}
