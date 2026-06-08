/*
 * Wire
 * Copyright (C) 2026 Wire Swiss GmbH
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

@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.wire.kalium.calling.notifications

import avs.wcall_event_create
import avs.wcall_event_end
import avs.wcall_event_process
import avs.wcall_event_start
import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.COpaquePointer
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.StableRef
import kotlinx.cinterop.asStableRef
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.staticCFunction
import kotlinx.cinterop.toKString
import kotlinx.cinterop.usePinned

internal actual fun createPlatformAvsCallNotificationProcessor(
    selfUserId: String,
    selfClientId: String,
    callbacks: AvsCallNotificationCallbacks
): AvsCallNotificationProcessor {
    val stableRef = StableRef.create(callbacks)
    return runCatching {
        val handle = wcall_event_create(
            userid = selfUserId,
            clientid = selfClientId,
            incomingh = incomingHandler,
            missedh = missedHandler,
            closeh = closeHandler,
            arg = stableRef.asCPointer()
        )
        DefaultAvsCallNotificationProcessor(AppleAvsCallNotificationNativeGateway(handle, stableRef))
    }.getOrElse { throwable ->
        stableRef.dispose()
        FailedAvsCallNotificationProcessor(
            AvsCallNotificationProcessingFailure.NativeFailure(
                operation = AvsCallNotificationNativeOperation.Create,
                message = throwable.message ?: throwable::class.simpleName
            )
        )
    }
}

private class AppleAvsCallNotificationNativeGateway(
    private val handle: UInt,
    private val callbacksRef: StableRef<AvsCallNotificationCallbacks>
) : AvsCallNotificationNativeGateway {

    override fun start(): AvsCallNotificationNativeResult = runCatching {
        wcall_event_start(handle)
        AvsCallNotificationNativeResult.Success
    }.getOrElse {
        it.toNativeFailure(AvsCallNotificationNativeOperation.Start)
    }

    override fun process(notification: AvsCallNotification, notificationIndex: Int): AvsCallNotificationNativeResult = runCatching {
        val result = notification.payload.usePinned { pinned ->
            wcall_event_process(
                wuser = handle,
                buf = pinned.addressOf(0).reinterpret(),
                len = notification.payload.size.toULong(),
                curr_time = notification.currentTimeSeconds,
                msg_time = notification.messageTimeSeconds,
                convid = notification.conversationId,
                userid = notification.senderUserId,
                clientid = notification.senderClientId,
                conv_type = notification.conversationType.value
            )
        }
        if (result == AVS_SUCCESS) {
            AvsCallNotificationNativeResult.Success
        } else {
            AvsCallNotificationNativeResult.Failure(
                AvsCallNotificationProcessingFailure.NativeFailure(
                    operation = AvsCallNotificationNativeOperation.Process,
                    code = result,
                    eventIndex = notificationIndex
                )
            )
        }
    }.getOrElse {
        it.toNativeFailure(AvsCallNotificationNativeOperation.Process, notificationIndex)
    }

    override fun end(): AvsCallNotificationNativeResult = runCatching {
        wcall_event_end(handle)
        AvsCallNotificationNativeResult.Success
    }.getOrElse {
        it.toNativeFailure(AvsCallNotificationNativeOperation.End)
    }

    override fun close() {
        callbacksRef.dispose()
    }
}

private class FailedAvsCallNotificationProcessor(
    private val failure: AvsCallNotificationProcessingFailure.NativeFailure
) : AvsCallNotificationProcessor {
    override fun process(notifications: List<AvsCallNotification>): AvsCallNotificationProcessingResult =
        AvsCallNotificationProcessingResult.Failure(failure)

    override fun close() = Unit
}

private fun Throwable.toNativeFailure(
    operation: AvsCallNotificationNativeOperation,
    eventIndex: Int? = null
): AvsCallNotificationNativeResult.Failure =
    AvsCallNotificationNativeResult.Failure(
        AvsCallNotificationProcessingFailure.NativeFailure(
            operation = operation,
            eventIndex = eventIndex,
            message = message ?: this::class.simpleName
        )
    )

private fun callbacks(arg: COpaquePointer?): AvsCallNotificationCallbacks? =
    arg?.asStableRef<AvsCallNotificationCallbacks>()?.get()

private fun CPointer<ByteVar>?.string(): String = this?.toKString().orEmpty()

private val incomingHandler = staticCFunction {
        conversationId: CPointer<ByteVar>?,
        messageTime: UInt,
        userId: CPointer<ByteVar>?,
        clientId: CPointer<ByteVar>?,
        video: Int,
        shouldRing: Int,
        conversationType: Int,
        arg: COpaquePointer? ->
    callbacks(arg)?.onIncomingCall(
        AvsIncomingCallNotification(
            conversationId = conversationId.string(),
            messageTimeSeconds = messageTime,
            callerUserId = userId.string(),
            callerClientId = clientId?.toKString(),
            isVideoCall = video != 0,
            shouldRing = shouldRing != 0,
            conversationType = AvsCallNotificationConversationType(conversationType)
        )
    )
    Unit
}

private val missedHandler = staticCFunction {
        conversationId: CPointer<ByteVar>?,
        messageTime: UInt,
        userId: CPointer<ByteVar>?,
        _: CPointer<ByteVar>?,
        video: Int,
        arg: COpaquePointer? ->
    callbacks(arg)?.onMissedCall(
        AvsMissedCallNotification(
            conversationId = conversationId.string(),
            messageTimeSeconds = messageTime,
            callerUserId = userId.string(),
            isVideoCall = video != 0
        )
    )
    Unit
}

private val closeHandler = staticCFunction {
        reason: Int,
        conversationId: CPointer<ByteVar>?,
        messageTime: UInt,
        userId: CPointer<ByteVar>?,
        clientId: CPointer<ByteVar>?,
        arg: COpaquePointer? ->
    callbacks(arg)?.onClosedCall(
        AvsClosedCallNotification(
            reason = AvsCallNotificationClosedReason(reason),
            conversationId = conversationId.string(),
            messageTimeSeconds = messageTime,
            callerUserId = userId.string(),
            callerClientId = clientId?.toKString()
        )
    )
    Unit
}

private const val AVS_SUCCESS = 0
