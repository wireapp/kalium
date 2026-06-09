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
package com.wire.kalium.logic.feature.backup

import com.wire.kalium.common.error.CoreFailure
import com.wire.kalium.common.functional.Either
import com.wire.kalium.logic.cache.SelfConversationIdProvider
import com.wire.kalium.logic.data.backup.OnlineBackupRepository
import com.wire.kalium.logic.data.conversation.ClientId
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.id.CurrentClientIdProvider
import com.wire.kalium.logic.data.message.Message
import com.wire.kalium.logic.data.message.MessageContent
import com.wire.kalium.logic.data.conversation.Recipient
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.messaging.sending.MessageSender
import com.wire.kalium.messaging.sending.MessageTarget
import com.wire.kalium.util.DateTimeUtil
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.datetime.Instant
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.uuid.Uuid

/**
 * Requests the backup root key from another compatible self client when no local key is stored.
 */
public interface SyncBackupRootKeyUseCase {
    public suspend operator fun invoke(): SyncBackupRootKeyResult
}

/**
 * Sends a known backup root key to other self clients through the encrypted self conversation.
 */
public interface PushBackupRootKeyUseCase {
    public suspend operator fun invoke(backupRootKey: BackupRootKey): PushBackupRootKeyResult
}

/**
 * Returns the local backup root key, fetches it from another self client, or generates and pushes a new one.
 */
public interface GetOrCreateSyncedBackupRootKeyUseCase {
    public suspend operator fun invoke(): GetOrCreateSyncedBackupRootKeyResult
}

/**
 * Generates a new backup root key, overwrites the local key, and pushes it to other self clients.
 */
public interface GenerateAndForcePushBackupRootKeyUseCase {
    public suspend operator fun invoke(): GenerateAndForcePushBackupRootKeyResult
}

/**
 * Synchronizes the backup root key during login only when online backup metadata already exists.
 */
public interface SyncBackupRootKeyIfOnlineBackupExistsUseCase {
    public suspend operator fun invoke(): SyncBackupRootKeyIfOnlineBackupExistsResult
}

public interface ObservePendingBackupRootKeyRequestsUseCase {
    public operator fun invoke(): StateFlow<List<PendingBackupRootKeyRequest>>
}

public interface ApproveBackupRootKeyRequestUseCase {
    public suspend operator fun invoke(requestId: String, requesterClientId: ClientId): BackupRootKeyRequestDecisionResult
}

public interface DeclineBackupRootKeyRequestUseCase {
    public suspend operator fun invoke(requestId: String, requesterClientId: ClientId): BackupRootKeyRequestDecisionResult
}

public data class PendingBackupRootKeyRequest(
    val requestId: String,
    val requesterClientId: ClientId,
    val requestedAt: Instant,
    val backupRootKeyInfo: BackupRootKeyInfo,
)

public sealed interface SyncBackupRootKeyResult {
    public data class Found(val backupRootKey: BackupRootKey) : SyncBackupRootKeyResult
    public data object LocalKeyExists : SyncBackupRootKeyResult
    public data object Unavailable : SyncBackupRootKeyResult
    public data class Failure(val cause: Throwable) : SyncBackupRootKeyResult
}

public sealed interface PushBackupRootKeyResult {
    public data object Success : PushBackupRootKeyResult
    public data class PartialFailure(val cause: CoreFailure) : PushBackupRootKeyResult
    public data class Failure(val cause: Throwable) : PushBackupRootKeyResult
}

public sealed interface GetOrCreateSyncedBackupRootKeyResult {
    public data class Success(val backupRootKey: BackupRootKey, val source: Source) : GetOrCreateSyncedBackupRootKeyResult
    public data class Failure(val cause: Throwable) : GetOrCreateSyncedBackupRootKeyResult

    public enum class Source {
        LOCAL,
        SYNCED,
        GENERATED,
    }
}

public sealed interface GenerateAndForcePushBackupRootKeyResult {
    public data class Success(
        val backupRootKey: BackupRootKey,
        val pushResult: PushBackupRootKeyResult,
    ) : GenerateAndForcePushBackupRootKeyResult

    public data class Failure(val cause: GenerateBackupRootKeyResult.Failure) : GenerateAndForcePushBackupRootKeyResult
}

public sealed interface SyncBackupRootKeyIfOnlineBackupExistsResult {
    public data object NoOnlineBackups : SyncBackupRootKeyIfOnlineBackupExistsResult
    public data object LocalKeyExists : SyncBackupRootKeyIfOnlineBackupExistsResult
    public data class Synced(val backupRootKey: BackupRootKey) : SyncBackupRootKeyIfOnlineBackupExistsResult
    public data object KeyUnavailable : SyncBackupRootKeyIfOnlineBackupExistsResult
    public data class Failure(val cause: Throwable) : SyncBackupRootKeyIfOnlineBackupExistsResult
}

public sealed interface BackupRootKeyRequestDecisionResult {
    public data object Success : BackupRootKeyRequestDecisionResult
    public data object RequestNotFound : BackupRootKeyRequestDecisionResult
    public data class Failure(val cause: Throwable) : BackupRootKeyRequestDecisionResult
}

internal class SyncBackupRootKeyUseCaseImpl(
    private val selfUserId: UserId,
    private val currentClientIdProvider: CurrentClientIdProvider,
    private val backupRootKeyRepository: BackupRootKeyRepository,
    private val selfConversationIdProvider: SelfConversationIdProvider,
    private val messageSender: MessageSender,
    private val coordinator: BackupRootKeySyncCoordinator = BackupRootKeySyncCoordinator,
    private val timeout: Duration = DEFAULT_BACKUP_ROOT_KEY_SYNC_TIMEOUT,
    private val requestIdProvider: () -> String = { Uuid.random().toString() },
) : SyncBackupRootKeyUseCase {

    override suspend fun invoke(): SyncBackupRootKeyResult =
        try {
            if (backupRootKeyRepository.getBackupRootKey() != null) {
                return SyncBackupRootKeyResult.LocalKeyExists
            }

            val requestId = requestIdProvider()
            val syncedKey = coordinator.awaitEnvelope(requestId, timeout) {
                sendToSelfConversations(MessageContent.BackupRootKeySync.Request(requestId))
            }
            if (syncedKey == null) {
                SyncBackupRootKeyResult.Unavailable
            } else {
                backupRootKeyRepository.setBackupRootKey(syncedKey)
                SyncBackupRootKeyResult.Found(syncedKey)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            SyncBackupRootKeyResult.Failure(e)
        }

    private suspend fun sendToSelfConversations(content: MessageContent.BackupRootKeySync): Either<CoreFailure, Unit> {
        val clientId = when (val result = currentClientIdProvider()) {
            is Either.Left -> return Either.Left(result.value)
            is Either.Right -> result.value
        }
        val conversationIds = when (val result = selfConversationIdProvider()) {
            is Either.Left -> return Either.Left(result.value)
            is Either.Right -> result.value
        }
        conversationIds.forEach { conversationId ->
            val result = messageSender.sendMessage(
                signalingMessage(conversationId, clientId, content),
            )
            if (result is Either.Left) return result
        }
        return Either.Right(Unit)
    }

    private fun signalingMessage(
        conversationId: ConversationId,
        clientId: ClientId,
        content: MessageContent.BackupRootKeySync,
    ): Message.Signaling =
        Message.Signaling(
            id = Uuid.random().toString(),
            content = content,
            conversationId = conversationId,
            date = DateTimeUtil.currentInstant(),
            senderUserId = selfUserId,
            senderClientId = clientId,
            status = Message.Status.Pending,
            isSelfMessage = true,
            expirationData = null,
        )
}

internal val DEFAULT_BACKUP_ROOT_KEY_SYNC_TIMEOUT: Duration = 10.minutes

internal class ObservePendingBackupRootKeyRequestsUseCaseImpl(
    private val pendingRequestStore: BackupRootKeyPendingRequestStore,
) : ObservePendingBackupRootKeyRequestsUseCase {
    override fun invoke(): StateFlow<List<PendingBackupRootKeyRequest>> = pendingRequestStore.observePendingRequests()
}

internal class ApproveBackupRootKeyRequestUseCaseImpl(
    private val selfUserId: UserId,
    private val currentClientIdProvider: CurrentClientIdProvider,
    private val messageSender: MessageSender,
    private val pendingRequestStore: BackupRootKeyPendingRequestStore,
) : ApproveBackupRootKeyRequestUseCase {

    override suspend fun invoke(requestId: String, requesterClientId: ClientId): BackupRootKeyRequestDecisionResult =
        try {
            val pendingRequest = pendingRequestStore.remove(requestId, requesterClientId)
                ?: return BackupRootKeyRequestDecisionResult.RequestNotFound
            val currentClientId = when (val result = currentClientIdProvider()) {
                is Either.Left -> return BackupRootKeyRequestDecisionResult.Failure(IllegalStateException(result.value.toString()))
                is Either.Right -> result.value
            }
            val message = Message.Signaling(
                id = Uuid.random().toString(),
                content = pendingRequest.backupRootKey.toEnvelope(requestId),
                conversationId = pendingRequest.conversationId,
                date = DateTimeUtil.currentInstant(),
                senderUserId = selfUserId,
                senderClientId = currentClientId,
                status = Message.Status.Pending,
                isSelfMessage = true,
                expirationData = null,
            )
            val target = MessageTarget.Client(listOf(Recipient(selfUserId, listOf(requesterClientId))))
            when (val result = messageSender.sendMessage(message, target)) {
                is Either.Left -> BackupRootKeyRequestDecisionResult.Failure(IllegalStateException(result.value.toString()))
                is Either.Right -> BackupRootKeyRequestDecisionResult.Success
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            BackupRootKeyRequestDecisionResult.Failure(e)
        }

    private fun BackupRootKey.toEnvelope(requestId: String): MessageContent.BackupRootKeySync.Envelope =
        MessageContent.BackupRootKeySync.Envelope(
            requestId = requestId,
            keyId = id,
            keyMaterial = keyMaterial,
            createdAt = createdAt,
            createdByClientId = createdByClientId,
            version = version,
        )
}

internal class DeclineBackupRootKeyRequestUseCaseImpl(
    private val pendingRequestStore: BackupRootKeyPendingRequestStore,
) : DeclineBackupRootKeyRequestUseCase {
    override suspend fun invoke(requestId: String, requesterClientId: ClientId): BackupRootKeyRequestDecisionResult =
        if (pendingRequestStore.remove(requestId, requesterClientId) == null) {
            BackupRootKeyRequestDecisionResult.RequestNotFound
        } else {
            BackupRootKeyRequestDecisionResult.Success
        }
}

internal class PushBackupRootKeyUseCaseImpl(
    private val selfUserId: UserId,
    private val currentClientIdProvider: CurrentClientIdProvider,
    private val selfConversationIdProvider: SelfConversationIdProvider,
    private val messageSender: MessageSender,
    private val requestIdProvider: () -> String = { Uuid.random().toString() },
) : PushBackupRootKeyUseCase {

    override suspend fun invoke(backupRootKey: BackupRootKey): PushBackupRootKeyResult =
        try {
            val clientId = when (val result = currentClientIdProvider()) {
                is Either.Left -> return PushBackupRootKeyResult.PartialFailure(result.value)
                is Either.Right -> result.value
            }
            val conversationIds = when (val result = selfConversationIdProvider()) {
                is Either.Left -> return PushBackupRootKeyResult.PartialFailure(result.value)
                is Either.Right -> result.value
            }
            val content = backupRootKey.toEnvelope(requestIdProvider())
            conversationIds.forEach { conversationId ->
                val result = messageSender.sendMessage(
                    Message.Signaling(
                        id = Uuid.random().toString(),
                        content = content,
                        conversationId = conversationId,
                        date = DateTimeUtil.currentInstant(),
                        senderUserId = selfUserId,
                        senderClientId = clientId,
                        status = Message.Status.Pending,
                        isSelfMessage = true,
                        expirationData = null,
                    ),
                )
                if (result is Either.Left) return PushBackupRootKeyResult.PartialFailure(result.value)
            }
            PushBackupRootKeyResult.Success
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            PushBackupRootKeyResult.Failure(e)
        }

    private fun BackupRootKey.toEnvelope(requestId: String): MessageContent.BackupRootKeySync.Envelope =
        MessageContent.BackupRootKeySync.Envelope(
            requestId = requestId,
            keyId = id,
            keyMaterial = keyMaterial,
            createdAt = createdAt,
            createdByClientId = createdByClientId,
            version = version,
        )
}

internal class GetOrCreateSyncedBackupRootKeyUseCaseImpl(
    private val backupRootKeyRepository: BackupRootKeyRepository,
    private val syncBackupRootKey: SyncBackupRootKeyUseCase,
    private val generateBackupRootKey: GenerateBackupRootKeyUseCase,
    private val pushBackupRootKey: PushBackupRootKeyUseCase,
) : GetOrCreateSyncedBackupRootKeyUseCase {

    override suspend fun invoke(): GetOrCreateSyncedBackupRootKeyResult =
        try {
            backupRootKeyRepository.getBackupRootKey()?.let {
                return GetOrCreateSyncedBackupRootKeyResult.Success(it, GetOrCreateSyncedBackupRootKeyResult.Source.LOCAL)
            }

            when (val syncResult = syncBackupRootKey()) {
                is SyncBackupRootKeyResult.Found ->
                    return GetOrCreateSyncedBackupRootKeyResult.Success(
                        syncResult.backupRootKey,
                        GetOrCreateSyncedBackupRootKeyResult.Source.SYNCED,
                    )

                SyncBackupRootKeyResult.LocalKeyExists ->
                    backupRootKeyRepository.getBackupRootKey()?.let {
                        return GetOrCreateSyncedBackupRootKeyResult.Success(it, GetOrCreateSyncedBackupRootKeyResult.Source.LOCAL)
                    }

                SyncBackupRootKeyResult.Unavailable -> Unit
                is SyncBackupRootKeyResult.Failure -> Unit
            }

            when (val generated = generateBackupRootKey()) {
                is GenerateBackupRootKeyResult.Success -> {
                    pushBackupRootKey(generated.backupRootKey)
                    GetOrCreateSyncedBackupRootKeyResult.Success(
                        generated.backupRootKey,
                        GetOrCreateSyncedBackupRootKeyResult.Source.GENERATED,
                    )
                }

                is GenerateBackupRootKeyResult.Failure ->
                    GetOrCreateSyncedBackupRootKeyResult.Failure(IllegalStateException(generated.toString()))
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            GetOrCreateSyncedBackupRootKeyResult.Failure(e)
        }
}

internal class GenerateAndForcePushBackupRootKeyUseCaseImpl(
    private val generateBackupRootKey: GenerateBackupRootKeyUseCase,
    private val pushBackupRootKey: PushBackupRootKeyUseCase,
) : GenerateAndForcePushBackupRootKeyUseCase {

    override suspend fun invoke(): GenerateAndForcePushBackupRootKeyResult =
        when (val generated = generateBackupRootKey()) {
            is GenerateBackupRootKeyResult.Success ->
                GenerateAndForcePushBackupRootKeyResult.Success(generated.backupRootKey, pushBackupRootKey(generated.backupRootKey))

            is GenerateBackupRootKeyResult.Failure ->
                GenerateAndForcePushBackupRootKeyResult.Failure(generated)
        }
}

internal class SyncBackupRootKeyIfOnlineBackupExistsUseCaseImpl(
    private val onlineBackupRepository: OnlineBackupRepository,
    private val syncBackupRootKey: SyncBackupRootKeyUseCase,
) : SyncBackupRootKeyIfOnlineBackupExistsUseCase {

    override suspend fun invoke(): SyncBackupRootKeyIfOnlineBackupExistsResult =
        try {
            val backups = when (val result = onlineBackupRepository.listBackups()) {
                is Either.Left -> return SyncBackupRootKeyIfOnlineBackupExistsResult.Failure(IllegalStateException(result.value.toString()))
                is Either.Right -> result.value
            }
            if (backups.isEmpty()) return SyncBackupRootKeyIfOnlineBackupExistsResult.NoOnlineBackups

            when (val syncResult = syncBackupRootKey()) {
                is SyncBackupRootKeyResult.Found -> SyncBackupRootKeyIfOnlineBackupExistsResult.Synced(syncResult.backupRootKey)
                SyncBackupRootKeyResult.LocalKeyExists -> SyncBackupRootKeyIfOnlineBackupExistsResult.LocalKeyExists
                SyncBackupRootKeyResult.Unavailable -> SyncBackupRootKeyIfOnlineBackupExistsResult.KeyUnavailable
                is SyncBackupRootKeyResult.Failure -> SyncBackupRootKeyIfOnlineBackupExistsResult.Failure(syncResult.cause)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            SyncBackupRootKeyIfOnlineBackupExistsResult.Failure(e)
        }
}

internal object BackupRootKeySyncValidator {
    fun validate(content: MessageContent.BackupRootKeySync.Envelope): BackupRootKey? {
        if (content.keyId.isBlank()) return null
        if (content.keyMaterial.size != KEY_MATERIAL_LENGTH) return null
        if (content.version != BACKUP_ROOT_KEY_VERSION) return null
        return BackupRootKey(
            id = content.keyId,
            keyMaterial = content.keyMaterial,
            createdAt = content.createdAt,
            createdByClientId = content.createdByClientId,
            version = content.version,
        )
    }

    private const val KEY_MATERIAL_LENGTH = 32
    private const val BACKUP_ROOT_KEY_VERSION = 1
}

internal object BackupRootKeySyncCoordinator {
    private val mutex = Mutex()
    private val pendingRequests = mutableMapOf<String, CompletableDeferred<BackupRootKey>>()

    suspend fun awaitEnvelope(
        requestId: String,
        timeout: Duration,
        sendRequest: suspend () -> Either<CoreFailure, Unit>,
    ): BackupRootKey? {
        val deferred = CompletableDeferred<BackupRootKey>()
        mutex.withLock {
            pendingRequests[requestId] = deferred
        }
        return try {
            val sendResult = sendRequest()
            if (sendResult is Either.Left) return null
            withTimeoutOrNull(timeout) { deferred.await() }
        } finally {
            mutex.withLock {
                pendingRequests.remove(requestId)
            }
        }
    }

    suspend fun onEnvelope(requestId: String, backupRootKey: BackupRootKey) {
        mutex.withLock {
            pendingRequests[requestId]
        }?.complete(backupRootKey)
    }
}

internal class BackupRootKeyPendingRequestStore {
    private val mutex = Mutex()
    private val pendingRequests = mutableMapOf<BackupRootKeyPendingRequestKey, BackupRootKeyPendingRequestEntry>()
    private val pendingRequestsFlow = MutableStateFlow<List<PendingBackupRootKeyRequest>>(emptyList())

    fun observePendingRequests(): StateFlow<List<PendingBackupRootKeyRequest>> = pendingRequestsFlow.asStateFlow()

    suspend fun add(entry: BackupRootKeyPendingRequestEntry) {
        mutex.withLock {
            pendingRequests[entry.key] = entry
            emitLocked()
        }
    }

    suspend fun remove(requestId: String, requesterClientId: ClientId): BackupRootKeyPendingRequestEntry? =
        mutex.withLock {
            pendingRequests.remove(BackupRootKeyPendingRequestKey(requestId, requesterClientId)).also {
                emitLocked()
            }
        }

    suspend fun clear() {
        mutex.withLock {
            pendingRequests.clear()
            emitLocked()
        }
    }

    private fun emitLocked() {
        pendingRequestsFlow.value = pendingRequests.values
            .sortedBy { it.request.requestedAt }
            .map { it.request }
    }
}

internal data class BackupRootKeyPendingRequestEntry(
    val request: PendingBackupRootKeyRequest,
    val conversationId: ConversationId,
    val backupRootKey: BackupRootKey,
) {
    val key: BackupRootKeyPendingRequestKey = BackupRootKeyPendingRequestKey(
        requestId = request.requestId,
        requesterClientId = request.requesterClientId,
    )
}

internal data class BackupRootKeyPendingRequestKey(
    val requestId: String,
    val requesterClientId: ClientId,
)
