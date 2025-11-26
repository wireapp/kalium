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
package com.wire.kalium.network

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Manages the lifecycle of [NetworkStateObserver] based on active user sync requests.
 *
 * This manager tracks which users have active sync requests and only keeps the network
 * observer registered when at least one user needs it. This optimization prevents
 * unnecessary network state monitoring when no users are actively syncing.
 *
 * Usage:
 * - Call [acquireNetworkObservation] when a user starts syncing
 * - Call [releaseNetworkObservation] when a user stops syncing
 * - The network observer will be registered/unregistered automatically
 */
interface NetworkStateObserverManager {

    /**
     * Acquires a network observation slot for the given user.
     * If this is the first active user, the network observer will be registered.
     *
     * @param userId Unique identifier for the user session
     */
    suspend fun acquireNetworkObservation(userId: String)

    /**
     * Releases a network observation slot for the given user.
     * If this was the last active user, the network observer will be unregistered.
     *
     * @param userId Unique identifier for the user session
     */
    suspend fun releaseNetworkObservation(userId: String)

    /**
     * Returns the current number of active observers (users with active sync).
     */
    val activeObserverCount: StateFlow<Int>
}

/**
 * Implementation of [NetworkStateObserverManager] that coordinates network observation
 * across multiple user sessions.
 *
 * @param networkStateObserver The underlying network state observer to manage
 */
class NetworkStateObserverManagerImpl(
    private val networkStateObserver: NetworkStateObserver,
) : NetworkStateObserverManager {

    private val mutex = Mutex()
    private val activeUsers = mutableSetOf<String>()
    private val _activeObserverCount = MutableStateFlow(0)
    override val activeObserverCount: StateFlow<Int> = _activeObserverCount

    override suspend fun acquireNetworkObservation(userId: String) {
        mutex.withLock {
            val wasEmpty = activeUsers.isEmpty()
            activeUsers.add(userId)
            _activeObserverCount.value = activeUsers.size

            if (wasEmpty && activeUsers.isNotEmpty()) {
                kaliumUtilLogger.i("NetworkStateObserverManager: First user acquiring, registering network observer")
                networkStateObserver.register()
            }
        }
    }

    override suspend fun releaseNetworkObservation(userId: String) {
        mutex.withLock {
            activeUsers.remove(userId)
            _activeObserverCount.value = activeUsers.size

            if (activeUsers.isEmpty()) {
                kaliumUtilLogger.i("NetworkStateObserverManager: Last user released, unregistering network observer")
                networkStateObserver.unregister()
            }
        }
    }
}