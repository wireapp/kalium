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

package com.wire.kalium.calling.notifications

import com.sun.jna.Callback
import com.sun.jna.IntegerType
import com.sun.jna.Library
import com.sun.jna.Native
import com.sun.jna.Pointer

internal actual fun createPlatformAvsCallNotificationProcessor(
    selfUserId: String,
    selfClientId: String,
    callbacks: AvsCallNotificationCallbacks
): AvsCallNotificationProcessor =
    JvmAndroidAvsCallNotificationProcessorFactory.create(selfUserId, selfClientId, callbacks)

private object JvmAndroidAvsCallNotificationProcessorFactory {
    fun create(
        selfUserId: String,
        selfClientId: String,
        callbacks: AvsCallNotificationCallbacks
    ): AvsCallNotificationProcessor {
        val incomingHandler = callbacks.incomingCallHandler()
        val missedHandler = callbacks.missedCallHandler()
        val closeHandler = callbacks.closeCallHandler()

        return runCatching {
            val handle = AvsEventCalling.INSTANCE.wcall_event_create(
                userId = selfUserId,
                clientId = selfClientId,
                incomingCallHandler = incomingHandler,
                missedCallHandler = missedHandler,
                closeCallHandler = closeHandler,
                arg = null
            )
            DefaultAvsCallNotificationProcessor(
                JvmAndroidAvsCallNotificationNativeGateway(
                    handle = handle,
                    incomingHandler = incomingHandler,
                    missedHandler = missedHandler,
                    closeHandler = closeHandler
                )
            )
        }.getOrElse { throwable ->
            FailedAvsCallNotificationProcessor(
                AvsCallNotificationProcessingFailure.NativeFailure(
                    operation = AvsCallNotificationNativeOperation.Create,
                    message = throwable.message ?: throwable::class.simpleName
                )
            )
        }
    }
}

private fun AvsCallNotificationCallbacks.incomingCallHandler(): IncomingCallHandler =
    IncomingCallHandler { conversationId, messageTime, userId, clientId, isVideoCall, shouldRing, conversationType, _ ->
        onIncomingCall(
            AvsIncomingCallNotification(
                conversationId = conversationId,
                messageTimeSeconds = messageTime.value.toUInt(),
                callerUserId = userId,
                callerClientId = clientId,
                isVideoCall = isVideoCall,
                shouldRing = shouldRing,
                conversationType = AvsCallNotificationConversationType(conversationType)
            )
        )
    }

private fun AvsCallNotificationCallbacks.missedCallHandler(): MissedCallHandler =
    MissedCallHandler { conversationId, messageTime, userId, isVideoCall, _ ->
        onMissedCall(
            AvsMissedCallNotification(
                conversationId = conversationId,
                messageTimeSeconds = messageTime.value.toUInt(),
                callerUserId = userId,
                isVideoCall = isVideoCall
            )
        )
    }

private fun AvsCallNotificationCallbacks.closeCallHandler(): CloseCallHandler =
    CloseCallHandler { reason, conversationId, messageTime, userId, clientId, _ ->
        onClosedCall(
            AvsClosedCallNotification(
                reason = AvsCallNotificationClosedReason(reason),
                conversationId = conversationId,
                messageTimeSeconds = messageTime.value.toUInt(),
                callerUserId = userId,
                callerClientId = clientId
            )
        )
    }

private class JvmAndroidAvsCallNotificationNativeGateway(
    private val handle: Handle,
    @Suppress("unused") private val incomingHandler: IncomingCallHandler,
    @Suppress("unused") private val missedHandler: MissedCallHandler,
    @Suppress("unused") private val closeHandler: CloseCallHandler
) : AvsCallNotificationNativeGateway {

    override fun start(): AvsCallNotificationNativeResult = runCatching {
        AvsEventCalling.INSTANCE.wcall_event_start(handle)
        AvsCallNotificationNativeResult.Success
    }.getOrElse {
        it.toNativeFailure(AvsCallNotificationNativeOperation.Start)
    }

    override fun process(notification: AvsCallNotification, notificationIndex: Int): AvsCallNotificationNativeResult = runCatching {
        val result = AvsEventCalling.INSTANCE.wcall_event_process(
            inst = handle,
            msg = notification.payload,
            len = notification.payload.size,
            currTime = UInt32(notification.currentTimeSeconds.toLong()),
            msgTime = UInt32(notification.messageTimeSeconds.toLong()),
            convId = notification.conversationId,
            userId = notification.senderUserId,
            clientId = notification.senderClientId,
            convType = notification.conversationType.value
        )
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
        AvsEventCalling.INSTANCE.wcall_event_end(handle)
        AvsCallNotificationNativeResult.Success
    }.getOrElse {
        it.toNativeFailure(AvsCallNotificationNativeOperation.End)
    }

    override fun close() = Unit
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
    notificationIndex: Int? = null
): AvsCallNotificationNativeResult.Failure =
    AvsCallNotificationNativeResult.Failure(
        AvsCallNotificationProcessingFailure.NativeFailure(
            operation = operation,
            eventIndex = notificationIndex,
            message = message ?: this::class.simpleName
        )
    )

@Suppress("FunctionNaming")
private interface AvsEventCalling : Library {
    fun wcall_event_create(
        userId: String,
        clientId: String,
        incomingCallHandler: IncomingCallHandler,
        missedCallHandler: MissedCallHandler,
        closeCallHandler: CloseCallHandler,
        arg: Pointer?
    ): Handle

    fun wcall_event_start(inst: Handle)

    @Suppress("LongParameterList")
    fun wcall_event_process(
        inst: Handle,
        msg: ByteArray,
        len: Int,
        currTime: UInt32,
        msgTime: UInt32,
        convId: String,
        userId: String,
        clientId: String,
        convType: Int
    ): Int

    fun wcall_event_end(inst: Handle)

    companion object {
        val INSTANCE: AvsEventCalling by lazy { Native.load("avs", AvsEventCalling::class.java)!! }
    }
}

private fun interface IncomingCallHandler : Callback {
    @Suppress("LongParameterList")
    fun onIncomingCall(
        conversationId: String,
        messageTime: UInt32,
        userId: String,
        clientId: String?,
        isVideoCall: Boolean,
        shouldRing: Boolean,
        conversationType: Int,
        arg: Pointer?
    )
}

private fun interface MissedCallHandler : Callback {
    fun onMissedCall(
        conversationId: String,
        messageTime: UInt32,
        userId: String,
        isVideoCall: Boolean,
        arg: Pointer?
    )
}

private fun interface CloseCallHandler : Callback {
    fun onClosedCall(
        reason: Int,
        conversationId: String,
        messageTime: UInt32,
        userId: String,
        clientId: String?,
        arg: Pointer?
    )
}

private typealias Handle = UInt32

private const val INTEGER_SIZE = 4

private data class UInt32(val value: Long = 0) : IntegerType(INTEGER_SIZE, value, true) {
    override fun toByte(): Byte = value.toByte()

    override fun toChar(): Char = value.toInt().toChar()

    override fun toShort(): Short = value.toShort()
}

private const val AVS_SUCCESS = 0
