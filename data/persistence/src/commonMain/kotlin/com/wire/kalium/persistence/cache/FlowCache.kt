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
package com.wire.kalium.persistence.cache

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

/**
 * In-memory cache for sharing flows.
 * This aims to bundle all interested collectors
 * and read from a single upstream source, reducing IO reading.
 *
 * New collectors will get the latest value immediately.
 * It converts produced flows into shared flows with a replay cache of 1.
 *
 * Each individual call to [get] will get its own buffer (size of 1),
 * and oldest values are dropped if the collector is slow.
 *
 * Once the cached flows have no more collectors, the flows are removed from memory after [flowTimeoutDuration].
 *
 * Once the [cacheScope] is canceled, the whole cache stops.
 */
internal class FlowCache<Key : Any, Value>(
    private val cacheScope: CoroutineScope,
    private val flowTimeoutDuration: Duration = FLOW_OBSERVING_TIMEOUT_IN_MILLIS.milliseconds,
) {

    private val cacheParentJob: Job = requireNotNull(cacheScope.coroutineContext[Job]) {
        "cacheScope must have a Job in its context so sharing jobs can be parented to it"
    }
    private val mutex = Mutex()
    private val storage = hashMapOf<Key, Flow<Value>>()
    private val sharingJobs = hashMapOf<Key, Job>()

    suspend fun get(
        key: Key,
        flowProducer: suspend (key: Key) -> Flow<Value>
    ): Flow<Value> {
        suspend fun createFlow(): Flow<Value> {
            val sharingJob = SupervisorJob(cacheParentJob)
            val sharingScope = CoroutineScope(cacheScope.coroutineContext + sharingJob)
            var registered = false
            try {
                val sharedFlow = flowProducer(key)
                    .onCompletion { removeIfOwned(key, sharingJob) }
                    .shareIn(
                        scope = sharingScope,
                        started = SharingStarted.WhileSubscribed(
                            stopTimeoutMillis = flowTimeoutDuration.inWholeMilliseconds
                        ),
                        replay = 1
                    )
                sharingJobs[key] = sharingJob
                registered = true
                return sharedFlow
            } finally {
                // Only fires when flowProducer/shareIn failed before the job was registered;
                // cancel the orphan SupervisorJob so it doesn't linger as a child of cacheScope.
                if (!registered) sharingJob.cancel()
            }
        }

        return mutex.withLock {
            val result = storage.getOrPut(key) {
                createFlow()
            }
            result
        }.distinctUntilChanged()
            .buffer(
                capacity = 1,
                onBufferOverflow = BufferOverflow.DROP_OLDEST
            )
    }

    suspend fun remove(key: Key) = mutex.withLock {
        storage.remove(key)
        sharingJobs.remove(key)?.cancel()
    }

    /**
     * Removes the entry for [key] and cancels [owningJob] only if [owningJob] is still the
     * currently-registered sharing job for [key]. Used by the upstream's [onCompletion] hook so
     * that a late-firing completion from an already-evicted flow does not tear down a
     * newly-recreated entry under the same key. The job cancellation is required to kill the
     * surrounding `launchSharing` coroutine that [shareIn] keeps alive across STOP signals.
     */
    private suspend fun removeIfOwned(key: Key, owningJob: Job) = mutex.withLock {
        if (sharingJobs[key] === owningJob) {
            storage.remove(key)
            sharingJobs.remove(key)
            owningJob.cancel()
        }
    }

    companion object {
        const val FLOW_OBSERVING_TIMEOUT_IN_MILLIS = 5_000L
    }
}
