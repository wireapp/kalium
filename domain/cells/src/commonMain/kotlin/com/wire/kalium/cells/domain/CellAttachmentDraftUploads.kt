/* Wire, Copyright (C) 2026 Wire Swiss GmbH, SPDX-License-Identifier: GPL-3.0-or-later */
package com.wire.kalium.cells.domain

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.uuid.Uuid

/**
 * Session-owned Cells conversation drafts. Native ownership is opt-in; Android retains its existing path.
 * Registered -> waiting for owner -> claimed -> transfer and status cleanup -> terminal result.
 * Cancellation joins cleanup before returning. Explicit retry creates another operation from the persisted node;
 * cancelled owners never restart transfers automatically. Ordinary encrypted drafts do not enter this registry.
 */
public interface CellAttachmentDraftUploads {
    public val uploads: StateFlow<List<CellAttachmentDraftUpload>>

    /** Native account lifetime owners enable this before accepting drafts; Android keeps its existing path. */
    public fun requireBackgroundOwner()
}

public enum class CellAttachmentDraftUploadResult { Completed, Failed, Cancelled }

/** A complete handle is registered before any upload work can start. */
public interface CellAttachmentDraftUpload {
    public val id: String
    public val isClaimed: Boolean

    /** Call only after acquiring the account lease and native background lifetime. */
    public fun claim()
    public suspend fun awaitCompletion(): CellAttachmentDraftUploadResult

    /** Includes precheck, late node cancellation, transfer join, and draft status cleanup. */
    public suspend fun cancelAndAwait()
}

internal class CellAttachmentDraftUploadImpl(private val released: (String) -> Unit) : CellAttachmentDraftUpload {
    override val id: String = Uuid.random().toString()
    internal lateinit var job: Job
    internal val completion = CompletableDeferred<CellAttachmentDraftUploadResult>()
    private val claimed = MutableStateFlow(false)
    private val ownerReady = CompletableDeferred<Unit>()
    override val isClaimed: Boolean get() = claimed.value

    /** Call only after acquiring the account lease and native background lifetime. */
    override fun claim() {
        claimed.value = true
        ownerReady.complete(Unit)
        if (completion.isCompleted) released(id)
    }

    override suspend fun awaitCompletion(): CellAttachmentDraftUploadResult = completion.await()

    /** Includes precheck, late-created node cancellation, transfer join, and draft status cleanup. */
    override suspend fun cancelAndAwait() {
        claimed.value = true
        job.cancelAndJoin()
        released(id)
    }

    internal suspend fun awaitOwner() = ownerReady.await()
    internal fun releaseIfClaimed() { if (claimed.value) released(id) }
}

internal class CellAttachmentDraftUploadsImpl(private val scope: CoroutineScope) : CellAttachmentDraftUploads {
    private val mutableUploads = MutableStateFlow<List<CellAttachmentDraftUpload>>(emptyList())
    override val uploads: StateFlow<List<CellAttachmentDraftUpload>> = mutableUploads.asStateFlow()
    private val ownerRequired = MutableStateFlow(false)
    internal val enabled: Boolean get() = ownerRequired.value

    override fun requireBackgroundOwner() { ownerRequired.value = true }

    private val claimedNodes = MutableStateFlow<Set<String>>(emptySet())

    internal fun claimNode(nodeUuid: String): Boolean {
        while (true) {
            val current = claimedNodes.value
            if (nodeUuid in current) return false
            if (claimedNodes.compareAndSet(current, current + nodeUuid)) return true
        }
    }

    internal fun releaseNode(nodeUuid: String) { claimedNodes.update { it - nodeUuid } }

    internal fun launchForNode(
        nodeUuid: String,
        work: suspend () -> CellAttachmentDraftUploadResult,
    ): CellAttachmentDraftUploadImpl? {
        if (!claimNode(nodeUuid)) return null
        var launched = false
        return try {
            launch(work).also { operation ->
                operation.job.invokeOnCompletion { releaseNode(nodeUuid) }
                launched = true
            }
        } finally {
            if (!launched) releaseNode(nodeUuid)
        }
    }

    // The operation contains cleanup supplied by AddAttachmentDraftUseCase. Only its completed result
    // is exposed; cancellation cannot finish the handle while that cleanup still touches the account.
    @Suppress("TooGenericExceptionCaught")
    internal fun launch(work: suspend () -> CellAttachmentDraftUploadResult): CellAttachmentDraftUploadImpl {
        check(enabled)
        val operation = CellAttachmentDraftUploadImpl { id ->
            mutableUploads.update { items -> items.filterNot { it.id == id } }
        }
        operation.job = scope.launch(start = CoroutineStart.LAZY) {
            val result = try {
                operation.awaitOwner()
                work()
            } catch (_: CancellationException) {
                CellAttachmentDraftUploadResult.Cancelled
            } catch (_: Exception) {
                CellAttachmentDraftUploadResult.Failed
            }
            operation.completion.complete(result)
        }
        operation.job.invokeOnCompletion {
            // A cancelled account may prevent even a lazy operation's first instruction from running.
            operation.completion.complete(CellAttachmentDraftUploadResult.Cancelled)
            operation.releaseIfClaimed()
        }
        mutableUploads.update { it + operation }
        operation.job.start()
        return operation
    }
}
