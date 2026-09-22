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
package com.wire.kalium.nse

import com.wire.kalium.common.functional.Either
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.sync.receiving.AppleAccountRuntime
import com.wire.kalium.logic.sync.receiving.notificationProcessor
import com.wire.kalium.persistence.kmmSettings.AppleKeychainAccessibility
import com.wire.kalium.persistence.kmmSettings.ApplePersistenceConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withTimeoutOrNull

/** Shared configuration must exactly match app preparation, including the canonical App Group root. */
public data class KaliumNSEConfiguration(
    public val rootPath: String,
    public val keychainService: String,
    public val keychainAccessGroup: String?,
    public val keychainAccessibility: KaliumNSEKeychainAccessibility,
    public val userAgent: String,
    public val certificatePins: Map<String, List<String>>,
    /** Must match the app's KaliumConfigs build switches. Server settings remain in account storage. */
    public val guestRoomLink: Boolean = true,
    public val selfDeletingMessages: Boolean = true,
    public val drivePermissionsEnabled: Boolean = false,
    public val linkPreviewEnabled: Boolean = false,
    /** Null allows all file types; a list must match the app's BuildFileRestrictionState.AllowSome. */
    public val allowedFileExtensions: List<String>? = null,
)

public enum class KaliumNSEKeychainAccessibility { WHEN_UNLOCKED, AFTER_FIRST_UNLOCK, AFTER_FIRST_UNLOCK_THIS_DEVICE_ONLY }

public data class KaliumNSERequest(
    public val userValue: String,
    public val userDomain: String,
    public val notificationId: String,
    /** The host must derive this from the user's notification privacy setting. */
    public val showMessageContent: Boolean,
    public val budgetMilliseconds: Long,
)

/** SUCCESS without an alert is distinct from failure. The Swift host owns its single system completion callback. */
public data class KaliumNSEResult(
    public val status: KaliumNSEStatus,
    public val shouldNotify: Boolean,
    public val messageId: String?,
    public val conversationValue: String?,
    public val conversationDomain: String?,
    public val text: String?,
    public val contentType: KaliumNSEContentType = KaliumNSEContentType.MESSAGE,
    public val conversationName: String? = null,
    public val senderName: String? = null,
    /** AVS callbacks for the received prefix, in callback order. Report before completing the NSE request. */
    public val calls: List<KaliumNSECall> = emptyList(),
    /** Diagnostic code only; never contains account data or message content. */
    public val failureReason: String? = null,
    public val presentationReason: String? = null,
    public val callSignalsPending: Boolean = false,
)

public enum class KaliumNSEStatus { SUCCESS, FAILED }
public enum class KaliumNSEContentType { MESSAGE, CONVERSATION_DELETED, CONNECTION_REQUEST }

public enum class KaliumNSECallType { INCOMING, MISSED, CLOSED }

/** Call presentation only; the host owns CallKit and its account identifier. */
public data class KaliumNSECall(
    public val type: KaliumNSECallType,
    public val conversationValue: String,
    public val conversationDomain: String,
    public val callerValue: String,
    public val callerDomain: String,
    public val callerClientId: String?,
    public val messageTimeSeconds: Long,
    public val shouldRing: Boolean,
    public val isVideoCall: Boolean,
    public val closeReason: Int?,
    /** Null when the host privacy preference or verification state suppresses names. */
    public val callerName: String?,
    public val handoffId: String? = null,
)

/** Bounded prepared-account receiving. Unsupported earlier work stops the attempt and preserves its processed marker. */
public class KaliumNSEReceiver(private val configuration: KaliumNSEConfiguration) {
    @Throws(CancellationException::class)
    public suspend fun process(request: KaliumNSERequest): KaliumNSEResult {
        if (request.budgetMilliseconds !in 1..MAX_BUDGET_MS || request.notificationId.isBlank()) return failure("INVALID_REQUEST")
        return withTimeoutOrNull(request.budgetMilliseconds) {
            val result = AppleAccountRuntime.use(
                configuration.rootPath,
                UserId(request.userValue, request.userDomain),
                ApplePersistenceConfig(
                    configuration.keychainService, configuration.keychainAccessGroup, configuration.keychainAccessibility.toApple()
                ),
                configuration.userAgent,
                configuration.certificatePins,
            ) { runtime ->
                runtime.notificationProcessor(
                    com.wire.kalium.logic.sync.receiving.ReceivingOptions(
                        guestRoomLink = configuration.guestRoomLink,
                        selfDeletingMessages = configuration.selfDeletingMessages,
                        drivePermissionsEnabled = configuration.drivePermissionsEnabled,
                        linkPreviewEnabled = configuration.linkPreviewEnabled,
                        fileRestrictionState = configuration.allowedFileExtensions?.let {
                            com.wire.kalium.logic.featureFlags.BuildFileRestrictionState.AllowSome(it)
                        } ?: com.wire.kalium.logic.featureFlags.BuildFileRestrictionState.NoRestriction
                    )
                ).process(request.notificationId)
            }
            when (result) {
                is Either.Left -> failure(result.value.name)
                is Either.Right -> success(result.value, request.showMessageContent)
            }
        } ?: failure("TIMEOUT")
    }

    @Suppress("CyclomaticComplexMethod") // Maps optional receiving fields to the public result.
    private fun success(result: com.wire.kalium.logic.sync.receiving.ReceivedNotificationResult, showContent: Boolean): KaliumNSEResult {
        val message = result.message
        val canPreview = showContent && message?.shouldNotify == true && message.mayPreview
        val contentType = when (message?.type) {
            com.wire.kalium.logic.sync.receiving.ReceivedNotificationType.CONVERSATION_DELETED -> KaliumNSEContentType.CONVERSATION_DELETED
            com.wire.kalium.logic.sync.receiving.ReceivedNotificationType.CONNECTION_REQUEST -> KaliumNSEContentType.CONNECTION_REQUEST
            com.wire.kalium.logic.sync.receiving.ReceivedNotificationType.MESSAGE, null -> KaliumNSEContentType.MESSAGE
        }
        return KaliumNSEResult(
            KaliumNSEStatus.SUCCESS, message?.shouldNotify == true, message?.messageId,
            message?.conversationValue, message?.conversationDomain, message?.text?.takeIf { canPreview },
            callSignalsPending = result.callSignalsPending,
            presentationReason = result.presentationReason ?: when {
                message == null -> "NO_MESSAGE_CANDIDATE"
                !message.shouldNotify -> message.suppressionReason ?: "NOT_ELIGIBLE"
                !message.mayPreview -> message.previewPolicyReason ?: "PREVIEW_POLICY"
                message.text.isNullOrEmpty() -> "NO_TEXT"
                else -> "TEXT"
            },
            contentType = contentType,
            conversationName = message?.conversationName?.takeIf { canPreview },
            senderName = message?.senderName?.takeIf { canPreview },
            calls = result.calls.map { call ->
                KaliumNSECall(
                    type = when (call.type) {
                        com.wire.kalium.logic.sync.receiving.ReceivedCallType.INCOMING -> KaliumNSECallType.INCOMING
                        com.wire.kalium.logic.sync.receiving.ReceivedCallType.MISSED -> KaliumNSECallType.MISSED
                        com.wire.kalium.logic.sync.receiving.ReceivedCallType.CLOSED -> KaliumNSECallType.CLOSED
                    },
                    conversationValue = call.conversationValue,
                    conversationDomain = call.conversationDomain,
                    callerValue = call.callerValue,
                    callerDomain = call.callerDomain,
                    callerClientId = call.callerClientId,
                    messageTimeSeconds = call.messageTimeSeconds,
                    shouldRing = call.shouldRing,
                    isVideoCall = call.isVideoCall,
                    closeReason = call.closeReason,
                    callerName = call.callerName?.takeIf { showContent },
                    handoffId = call.handoffId,
                )
            },
        )
    }

    private fun KaliumNSEKeychainAccessibility.toApple(): AppleKeychainAccessibility = when (this) {
        KaliumNSEKeychainAccessibility.WHEN_UNLOCKED -> AppleKeychainAccessibility.WHEN_UNLOCKED
        KaliumNSEKeychainAccessibility.AFTER_FIRST_UNLOCK -> AppleKeychainAccessibility.AFTER_FIRST_UNLOCK
        KaliumNSEKeychainAccessibility.AFTER_FIRST_UNLOCK_THIS_DEVICE_ONLY -> AppleKeychainAccessibility.AFTER_FIRST_UNLOCK_THIS_DEVICE_ONLY
    }

    private fun failure(reason: String) = KaliumNSEResult(
        KaliumNSEStatus.FAILED,
        false,
        null,
        null,
        null,
        null,
        failureReason = reason
    )

    private companion object {
        const val MAX_BUDGET_MS = 25_000L
    }
}
