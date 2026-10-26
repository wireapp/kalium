/*
 * Wire
 * Copyright (C) 2026 Wire Swiss GmbH
 * SPDX-License-Identifier: GPL-3.0-only
 */
@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package com.wire.kalium.util

import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Admission boundary for account resources whose owners outlive a foreground session.
 * Adapters must keep whole transactions and their callbacks inside [withAccess].
 * New operations suspend without blocking threads while [pause] drains admitted work.
 */
class AccountResourceAccess {
    private val transition = Mutex()
    private val state = MutableStateFlow(State())

    suspend fun <T> withAccess(operation: suspend () -> T): T {
        val parent = currentCoroutineContext()[Access]
        // Count nested operations too: a callback may run in an independently owned coroutine.
        // acquirePermit still admits them while pause drains their enclosing operation.
        val permit = acquirePermit(trackCoroutine = false)
        return try {
            withContext(Access(this, permit, parent)) { operation() }
        } finally {
            permit.release()
        }
    }

    /**
     * Close all resources before releasing ownership; close failure makes access unavailable.
     * [quiesce] first stops work that must end before the pause while operations are still admitted;
     * no resume can interleave before [closeResources]. If it fails, access is unchanged.
     */
    suspend fun pause(quiesce: suspend () -> Unit = {}, closeResources: suspend () -> Unit) {
        checkOutsideOperation()
        transition.withLock {
            checkFailure()
            if (state.value.paused) return@withLock
            quiesce()
            state.update { it.copy(accepting = false) }
            try {
                state.first { it.permits.isEmpty() }
            } catch (cancelled: CancellationException) {
                // Closing has not started, so resources and ownership are still valid.
                withContext(NonCancellable) {
                    state.update { it.copy(accepting = true) }
                }
                throw cancelled
            }
            withContext(NonCancellable) {
                try {
                    withLifecycleAccess(closeResources)
                    state.update { it.copy(paused = true) }
                } catch (@Suppress("TooGenericExceptionCaught") failure: Throwable) {
                    fail(failure)
                    throw failure
                }
            }
        }
    }

    /** Restore ownership before releasing queued operations. */
    suspend fun resume(openResources: suspend () -> Unit) {
        checkOutsideOperation()
        transition.withLock { restore({}, openResources) }
    }

    /**
     * Admit a lifecycle operation atomically with resumption, before another pause can start.
     * When paused, [acquireOwnership] runs first and stays cancellable: if it fails, access stays paused and can
     * resume later. [openResources] then runs non-cancellably.
     */
    suspend fun <T> withResumedAccess(
        acquireOwnership: suspend () -> Unit,
        openResources: suspend () -> Unit,
        operation: suspend () -> T,
    ): T {
        val parent = currentCoroutineContext()[Access]
        if (parent?.contains(this) == true) return withAccess(operation)
        checkOutsideOperation()
        val permit = transition.withLock {
            restore(acquireOwnership, openResources)
            acquirePermit(trackCoroutine = false)
        }
        return try {
            withContext(Access(this, permit, parent)) { operation() }
        } finally {
            permit.release()
        }
    }

    private suspend fun restore(acquireOwnership: suspend () -> Unit, openResources: suspend () -> Unit) {
        checkFailure()
        if (state.value.accepting) return
        acquireOwnership()
        withContext(NonCancellable) {
            try {
                withLifecycleAccess(openResources)
                state.value = State()
            } catch (@Suppress("TooGenericExceptionCaught") failure: Throwable) {
                fail(failure)
                throw failure
            }
        }
    }

    /** Synchronous native statements finish before returning their QueryResult.Value. */
    fun tryAcquirePermit(): Permit? {
        while (true) {
            val snapshot = state.value
            snapshot.failure?.let { throw IllegalStateException("Account resource ownership is unavailable", it) }
            if (!snapshot.accepting) return null
            val permit = Permit(null) { released -> state.update { it.copy(permits = it.permits - released) } }
            if (state.compareAndSet(snapshot, snapshot.copy(permits = snapshot.permits + permit))) return permit
        }
    }

    /** For native transaction APIs whose completion is reported by a non-suspending callback. */
    suspend fun acquirePermit(): Permit = acquirePermit(trackCoroutine = true)

    private suspend fun acquirePermit(trackCoroutine: Boolean): Permit {
        val context = currentCoroutineContext()
        while (true) {
            context.ensureActive()
            val snapshot = state.value
            snapshot.failure?.let { throw IllegalStateException("Account resource ownership is unavailable", it) }
            // SQLDelight reports transaction completion through callbacks, so it cannot install
            // our context element around the body. Its coroutine and structured children still
            // belong to that admitted transaction (for example when they invoke CoreCrypto).
            val nested = context[Access]?.contains(this) == true || snapshot.permits.any { it.owns(context[Job]) }
            if (snapshot.accepting || nested) {
                val permit = Permit(context[Job].takeIf { trackCoroutine }) { released ->
                    state.update { it.copy(permits = it.permits - released) }
                }
                if (state.compareAndSet(snapshot, snapshot.copy(permits = snapshot.permits + permit))) return permit
            } else {
                state.first { it != snapshot }
            }
        }
    }

    class Permit internal constructor(
        private val job: Job?,
        private val onRelease: (Permit) -> Unit,
    ) {
        private val released = MutableStateFlow(false)
        internal val active: Boolean get() = !released.value

        @Suppress("ReturnCount") // Walk coroutine ancestry without allocating a collection.
        internal fun owns(candidate: Job?): Boolean {
            if (job == null || !active) return false
            var current = candidate
            while (current != null) {
                if (current === job) return true
                current = current.parent
            }
            return false
        }
        fun release() {
            if (released.compareAndSet(false, true)) onRelease(this)
        }
    }

    private suspend fun withLifecycleAccess(operation: suspend () -> Unit) {
        val permit = Permit(null) {}
        try {
            withContext(Access(this, permit, currentCoroutineContext()[Access])) { operation() }
        } finally {
            permit.release()
        }
    }

    private suspend fun checkOutsideOperation() {
        val context = currentCoroutineContext()
        check(context[Access]?.contains(this) != true && state.value.permits.none { it.owns(context[Job]) }) {
            "Account resource lifecycle cannot change from an admitted operation"
        }
    }

    private fun checkFailure() {
        state.value.failure?.let { throw IllegalStateException("Account resource ownership is unavailable", it) }
    }

    private fun fail(failure: Throwable) {
        state.update { it.copy(failure = failure) }
    }

    private data class State(
        val accepting: Boolean = true,
        val paused: Boolean = false,
        val permits: Set<Permit> = emptySet(),
        val failure: Throwable? = null,
    )

    private class Access(
        private val owner: AccountResourceAccess,
        private val permit: Permit,
        private val parent: Access?,
    ) : AbstractCoroutineContextElement(Key) {
        fun contains(candidate: AccountResourceAccess): Boolean =
            (owner === candidate && permit.active) || parent?.contains(candidate) == true
        companion object Key : CoroutineContext.Key<Access>
    }
}
