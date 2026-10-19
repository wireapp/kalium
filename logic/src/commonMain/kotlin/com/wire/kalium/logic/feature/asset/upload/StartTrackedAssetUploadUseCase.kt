/*
 * Wire
 * Copyright (C) 2025 Wire Swiss GmbH
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
package com.wire.kalium.logic.feature.asset.upload

import com.wire.kalium.common.error.CoreFailure
import com.wire.kalium.common.functional.Either
import com.wire.kalium.common.functional.fold
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext
import kotlin.uuid.Uuid

/** Starts an upload whose lifetime and final message-send result can be owned by the application. */
public class StartTrackedAssetUploadUseCase internal constructor(private val scheduler: ScheduleNewAssetMessageUseCaseImpl) {
    public suspend operator fun invoke(asset: AssetUploadParams): StartTrackedAssetUploadResult =
        scheduler.startTracked(asset, Uuid.random().toString())
}

public sealed interface StartTrackedAssetUploadResult {
    public data class Started(val operation: TrackedAssetUpload) : StartTrackedAssetUploadResult
    public data class Failure(val reason: ScheduleNewAssetMessageResult.Failure) : StartTrackedAssetUploadResult
}

/** These are completed-work phases, not an estimate of transferred bytes. */
public enum class AssetUploadProgress {
    QUEUED, UPLOADING_AND_SENDING, COMPLETED, FAILED, CANCELLED
}

public sealed interface AssetUploadCompletion {
    public data object Success : AssetUploadCompletion
    public data class Failure(val coreFailure: CoreFailure) : AssetUploadCompletion
    public data object Cancelled : AssetUploadCompletion
}

public interface TrackedAssetUpload {
    public val operationId: String
    public val messageId: String
    public val progress: StateFlow<AssetUploadProgress>

    /** Waits for upload, message send and monitor/cancellation cleanup. Cancelling the waiter does not cancel this operation. */
    public suspend fun awaitCompletion(): AssetUploadCompletion
    public fun cancel()

    /** Retries a failed/cancelled attempt with the same persisted message. Active and successful attempts return themselves. */
    public suspend fun retry(): TrackedAssetUpload
}

/** Records an acknowledged send before dispatcher return can discard its result on cancellation. */
internal class AssetMessageSendReceipt : AbstractCoroutineContextElement(Key) {
    companion object Key : CoroutineContext.Key<AssetMessageSendReceipt>
    var succeeded: Boolean = false
}

internal class TrackedAssetUploadImpl(
    override val messageId: String,
    scope: CoroutineScope,
    private val retryOperation: suspend () -> TrackedAssetUpload,
    monitor: suspend () -> Unit,
    upload: suspend () -> Either<CoreFailure, Unit>,
    onCancelled: suspend () -> Unit,
) : TrackedAssetUpload {
    override val operationId: String = Uuid.random().toString()
    private val mutableProgress = MutableStateFlow(AssetUploadProgress.QUEUED)
    override val progress: StateFlow<AssetUploadProgress> = mutableProgress.asStateFlow()
    private val completion = CompletableDeferred<AssetUploadCompletion>()
    private val retryMutex = Mutex()
    private var nextAttempt: TrackedAssetUpload? = null
    private val sendReceipt = AssetMessageSendReceipt()

    // Enter the try block before returning the handle, including when the owner scope has already been cancelled.
    // This operation boundary turns unexpected upload/monitor exceptions into a terminal result for its owner.
    @Suppress("TooGenericExceptionCaught")
    private val job = scope.launch(sendReceipt, start = CoroutineStart.UNDISPATCHED) {
        val result = try {
            coroutineScope {
                val uploadJob = currentCoroutineContext().job
                val monitorJob = launch(start = CoroutineStart.UNDISPATCHED) {
                    monitor()
                    uploadJob.cancel(CancellationException("Asset message is no longer visible"))
                }
                try {
                    mutableProgress.value = AssetUploadProgress.UPLOADING_AND_SENDING
                    upload().fold(
                        { AssetUploadCompletion.Failure(it) },
                        {
                            sendReceipt.succeeded = true
                            AssetUploadCompletion.Success
                        }
                    )
                } finally {
                    withContext(NonCancellable) { monitorJob.cancelAndJoin() }
                }
            }
        } catch (_: CancellationException) {
            withContext(NonCancellable) {
                if (!sendReceipt.succeeded) runCatching { onCancelled() }
                if (sendReceipt.succeeded) {
                    AssetUploadCompletion.Success
                } else {
                    AssetUploadCompletion.Cancelled
                }
            }
        } catch (exception: Exception) {
            AssetUploadCompletion.Failure(CoreFailure.Unknown(exception))
        }
        mutableProgress.value = when (result) {
            AssetUploadCompletion.Success -> AssetUploadProgress.COMPLETED
            is AssetUploadCompletion.Failure -> AssetUploadProgress.FAILED
            AssetUploadCompletion.Cancelled -> AssetUploadProgress.CANCELLED
        }
        completion.complete(result)
    }

    override suspend fun awaitCompletion(): AssetUploadCompletion = completion.await()

    override fun cancel() {
        job.cancel()
    }

    override suspend fun retry(): TrackedAssetUpload = retryMutex.withLock {
        if (!completion.isCompleted || completion.await() == AssetUploadCompletion.Success) {
            this
        } else {
            nextAttempt ?: retryOperation().also { nextAttempt = it }
        }
    }
}
