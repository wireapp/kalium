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

package com.wire.kalium.share

import com.wire.kalium.cells.CellFileSharing
import com.wire.kalium.cells.CellSharedFile
import com.wire.kalium.common.functional.map
import com.wire.kalium.common.error.NetworkFailure
import com.wire.kalium.common.error.wrapApiRequest
import com.wire.kalium.common.functional.Either
import com.wire.kalium.common.functional.getOrNull
import com.wire.kalium.common.logger.kaliumLogger
import com.wire.kalium.cryptography.utils.AES256Key
import com.wire.kalium.cryptography.utils.SHA256Key
import com.wire.kalium.cryptography.utils.encryptFileWithAES256
import com.wire.kalium.cryptography.utils.generateRandomAES256Key
import com.wire.kalium.logic.configuration.FileSharingStatus
import com.wire.kalium.logic.configuration.UserConfigDataSource
import com.wire.kalium.logic.configuration.server.ServerConfig
import com.wire.kalium.logic.data.asset.AssetMapperImpl
import com.wire.kalium.logic.data.asset.UploadedAssetId
import com.wire.kalium.logic.data.asset.isAudioMimeType
import com.wire.kalium.logic.data.asset.isDisplayableImageMimeType
import com.wire.kalium.logic.data.asset.isVideoMimeType
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.id.toApi
import com.wire.kalium.logic.data.id.toDao
import com.wire.kalium.logic.data.message.AssetContent
import com.wire.kalium.logic.data.message.MessageContent
import com.wire.kalium.logic.data.message.MessageEncryptionAlgorithm
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.feature.asset.ValidateAssetFileTypeUseCaseImpl
import com.wire.kalium.logic.featureFlags.BuildFileRestrictionState
import com.wire.kalium.logic.sync.receiving.AppleAccountRuntime
import com.wire.kalium.network.api.authenticated.asset.AssetMetadataRequest
import com.wire.kalium.network.api.base.authenticated.asset.AssetApi
import com.wire.kalium.network.api.model.AssetRetentionType
import com.wire.kalium.persistence.db.UserDatabaseBuilder
import io.ktor.http.Url
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import okio.Buffer
import okio.FileSystem
import okio.ForwardingSource
import okio.HashingSink
import okio.Path
import okio.Path.Companion.toPath
import okio.Source
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/** A file the host prepared to share. Kalium reads [path] and never moves or deletes it. */
internal data class ShareFile(
    val path: String,
    val name: String,
    val mimeType: String,
    val sizeInBytes: Long,
    val width: Int? = null,
    val height: Int? = null,
    val durationMs: Long? = null,
)

/** A [ShareFile] the backend stored, encrypted with [otrKey]. */
internal data class UploadedFile(
    val file: ShareFile,
    val otrKey: AES256Key,
    val sha256: SHA256Key,
    val assetId: UploadedAssetId,
    val encryptedSize: Long,
)

/** Why the extension refuses a file. Opening the app would not help: the account's rules refuse it there too. */
internal enum class FileRefusal { FILE_SHARING_DISABLED, FILE_TYPE_RESTRICTED, FILE_TOO_LARGE }

/** What an account may share as files, read as the app reads it. */
@Serializable
internal data class SharePolicy(
    /** Null allows every file type. */
    @SerialName("allowedExtensions") val allowedExtensions: List<String>?,
    @SerialName("fileSizeLimitBytes") val fileSizeLimitBytes: Long,
) {
    val imageSizeLimitBytes: Long get() = IMAGE_SIZE_LIMIT_BYTES

    /** The app's limits: images have their own, whatever the team. */
    fun sizeLimit(mimeType: String): Long =
        if (isDisplayableImageMimeType(mimeType)) IMAGE_SIZE_LIMIT_BYTES else fileSizeLimitBytes

    companion object {
        const val IMAGE_SIZE_LIMIT_BYTES = 15L * 1024 * 1024
        const val PERSONAL_SIZE_LIMIT_BYTES = 25L * 1024 * 1024
        const val TEAM_SIZE_LIMIT_BYTES = 100L * 1024 * 1024
    }
}

/** Null when the team disabled file sharing or the setting cannot be read, which the app treats as disabled. */
internal suspend fun UserDatabaseBuilder.sharePolicy(selfUserId: UserId, restriction: BuildFileRestrictionState): SharePolicy? {
    val status = UserConfigDataSource(userPrefsDAO, userConfigDAO, lazy { restriction }).isFileSharingEnabled()
    val allowed = when (val state = status.getOrNull()?.state) {
        null, FileSharingStatus.Value.Disabled -> return null
        FileSharingStatus.Value.EnabledAll -> null
        is FileSharingStatus.Value.EnabledSome -> state.allowedType
    }
    val team = userDAO.getTeamIdByQualifiedID(selfUserId.toDao()) != null
    return SharePolicy(allowed, if (team) SharePolicy.TEAM_SIZE_LIMIT_BYTES else SharePolicy.PERSONAL_SIZE_LIMIT_BYTES)
}

internal fun SharePolicy?.refusal(name: String, mimeType: String, sizeInBytes: Long): FileRefusal? = when {
    this == null -> FileRefusal.FILE_SHARING_DISABLED
    allowedExtensions != null && !ValidateAssetFileTypeUseCaseImpl()(name, mimeType, allowedExtensions) ->
        FileRefusal.FILE_TYPE_RESTRICTED
    sizeInBytes > sizeLimit(mimeType) -> FileRefusal.FILE_TOO_LARGE
    else -> null
}

/**
 * Checks [files] against the conversation and the account's rules, then uploads them. Sending checks the
 * conversation again after catching up; checking now avoids uploading for nothing.
 */
@Suppress("ReturnCount")
internal suspend fun AppleAccountRuntime.uploadSharedFiles(
    conversationId: ConversationId,
    files: List<ShareFile>,
    restriction: BuildFileRestrictionState,
    progress: (uploaded: Long, total: Long) -> Unit,
): Either<ShareOutcome, List<ShareItem>> {
    if (files.isEmpty()) return Either.Right(emptyList())
    val conversation = database.conversationDAO.getConversationDetailsById(conversationId.toDao())
    val block = when {
        conversation == null -> ShareBlock.UNAVAILABLE
        else -> database.shareBlock(conversation)
    }
    val policy = database.sharePolicy(prepared.userId, restriction)
    val refused = files.firstNotNullOfOrNull { policy.refusal(it.name, it.mimeType, it.sizeInBytes) }
    return when {
        block != null -> Either.Left(ShareOutcome(ShareStatus.OPEN_APP, block.name))
        refused != null -> Either.Left(ShareOutcome(ShareStatus.FAILED, refused.name))
        conversation?.wireCell != null && !database.cellsConfigured() ->
            Either.Left(ShareOutcome(ShareStatus.OPEN_APP, ShareBlock.UNAVAILABLE.name))
        conversation?.wireCell != null -> {
            val sharing = CellFileSharing.create(
                network.cellsHttpClient,
                authenticatedSession,
                network.accessTokenApi,
                database.userConfigDAO,
                database.publicLinks
            )
            try {
                val cellName = database.conversationDAO.getCellName(conversationId.toDao())
                    ?: return Either.Left(ShareOutcome(ShareStatus.OPEN_APP, ShareBlock.UNAVAILABLE.name))
                when (val result = sharing.upload(cellName, files.map { it.cellFile() }, progress)) {
                    is Either.Left -> Either.Left(
                        ShareOutcome(
                            if (result.value is NetworkFailure.NoNetworkConnection) ShareStatus.OFFLINE else ShareStatus.FAILED,
                            "UPLOAD_FAILED"
                        )
                    )
                    is Either.Right -> Either.Right(listOf(ShareItem.Cells(files, result.value, cellName)))
                }
            } finally {
                sharing.close()
            }
        }
        else -> ShareUploads(network.assetApi, assetAuditLog()).upload(files, conversationId, progress)
            .map { uploads -> uploads.map { ShareItem.File(it) } }
    }
}

/**
 * Runs [upload] while the extension holds the account for it, which keeps the account from the app and the NSE
 * (ADR 12). Uploads end with `OPEN_APP`, for the host to hand the share to the app, when [accountWanted] completes
 * or [budget] ends. Nothing is stored before every upload finished, so stopping them stores nothing.
 */
internal suspend fun <T> yieldingUploads(
    budget: Duration,
    accountWanted: Job,
    discard: suspend (T) -> Unit = {},
    upload: suspend () -> Either<ShareOutcome, T>,
): Either<ShareOutcome, T> = coroutineScope {
    var completed: Either<ShareOutcome, T>? = null
    var delivered = false
    val uploads = async {
        withTimeoutOrNull(budget) { upload().also { completed = it } }
            ?: Either.Left(ShareOutcome(ShareStatus.OPEN_APP, UPLOAD_TIMEOUT))
    }
    try {
        select {
            uploads.onAwait {
                delivered = it is Either.Right
                it
            }
            accountWanted.onJoin { Either.Left(ShareOutcome(ShareStatus.OPEN_APP, ACCOUNT_WANTED)) }
        }
    } finally {
        withContext(NonCancellable) {
            uploads.cancelAndJoin()
            if (!delivered) (completed as? Either.Right)?.value?.let { discard(it) }
        }
    }
}

/**
 * How long uploads of [totalBytes] may hold the account: enough at about 1 Mbit/s, and at most five minutes. A
 * slower share goes to the app, which uploads it while it owns the account anyway.
 */
internal fun uploadBudget(totalBytes: Long): Duration =
    (UPLOAD_BUDGET_BASE + (totalBytes / MIN_UPLOAD_BYTES_PER_SECOND).seconds).coerceAtMost(MAX_UPLOAD_BUDGET)

/** Another process wants the account the extension holds; see [yieldingUploads]. */
internal const val ACCOUNT_WANTED = "ACCOUNT_WANTED"
private const val UPLOAD_TIMEOUT = "UPLOAD_TIMEOUT"
private const val MIN_UPLOAD_BYTES_PER_SECOND = 128L * 1024
private val UPLOAD_BUDGET_BASE = 30.seconds
private val MAX_UPLOAD_BUDGET = 5.minutes

/** The app names the conversation, the file and its type to an auditing on-premises backend. */
private suspend fun AppleAccountRuntime.assetAuditLog(): Boolean {
    val links = prepared.serverConfig.links
    return database.userConfigDAO.isAssetAuditLogEnabled() && links.isOnPremises &&
        !ServerConfig.PRODUCTION.api.contains(Url(links.api).host)
}

internal fun ShareFile.metadata(): AssetContent.AssetMetadata? =
    sharedFileMetadata(mimeType, width, height, durationMs)

@com.wire.kalium.util.InternalKaliumApi
public fun ShareOutboxFile.metadata(): AssetContent.AssetMetadata? =
    sharedFileMetadata(mimeType, width, height, durationMs)

private fun sharedFileMetadata(mimeType: String, width: Int?, height: Int?, durationMs: Long?): AssetContent.AssetMetadata? = when {
    isDisplayableImageMimeType(mimeType) && width != null && height != null && width > 0 && height > 0 ->
        AssetContent.AssetMetadata.Image(width, height)
    isVideoMimeType(mimeType) -> AssetContent.AssetMetadata.Video(width, height, durationMs)
    isAudioMimeType(mimeType) -> AssetContent.AssetMetadata.Audio(durationMs, normalizedLoudness = null)
    else -> null
}

internal fun ShareFile.cellFile() = CellSharedFile(path.toPath(), name, mimeType, sizeInBytes, metadata())

/** The asset content the app sends once an upload finished; metadata follows the app's mime type rules. */
internal fun UploadedFile.content() = MessageContent.Asset(
    AssetContent(
        sizeInBytes = file.sizeInBytes,
        name = file.name,
        mimeType = file.mimeType,
        metadata = file.metadata(),
        remoteData = AssetContent.RemoteData(
            otrKey = otrKey.data,
            sha256 = sha256.data,
            assetId = assetId.key,
            assetToken = assetId.assetToken,
            assetDomain = assetId.domain,
            encryptionAlgorithm = MessageEncryptionAlgorithm.AES_CBC,
        ),
    )
)

/**
 * Keeps a copy where the app stores the assets it sent, so it shows the file without downloading it.
 * A failed copy only costs that download.
 */
@Suppress("TooGenericExceptionCaught")
internal suspend fun UploadedFile.keepLocalCopy(
    database: UserDatabaseBuilder,
    assetDirectory: String,
    fileSystem: FileSystem = FileSystem.SYSTEM,
) {
    try {
        val directory = assetDirectory.toPath()
        val target = directory / (file.name.fileExtension()?.let { "${assetId.key}.$it" } ?: assetId.key)
        fileSystem.createDirectories(directory)
        fileSystem.copy(file.path.toPath(), target)
        // Like the app, which stores the uploaded size.
        database.assetDAO.insertAsset(AssetMapperImpl().fromUserAssetToDaoModel(assetId.key, assetId.domain, target, encryptedSize))
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (failure: Exception) {
        kaliumLogger.w("Share could not keep a local copy: ${failure::class.simpleName}")
    }
}

/** The app's file extension rule: the last part after a dot, unless the name is only a leading dot and a word. */
internal fun String.fileExtension(): String? {
    val parts = split(".")
    val startsWithADot = parts.first().isEmpty()
    return parts.last().takeIf { if (startsWithADot) parts.size > 2 else parts.size > 1 }
}

/**
 * Encrypts and uploads shared files the way the app's asset repository does: private, persistent, AES-256-CBC
 * with the IV prefixed. It streams each file and hashes the ciphertext as it writes it, so an extension never
 * holds a file in memory.
 */
@OptIn(ExperimentalUuidApi::class)
internal class ShareUploads(
    private val assetApi: AssetApi,
    /** Whether the backend audits uploads; the app then names the conversation, the file and its type. */
    private val auditLog: Boolean,
    private val temporaryDirectory: Path = FileSystem.SYSTEM_TEMPORARY_DIRECTORY,
    private val fileSystem: FileSystem = FileSystem.SYSTEM,
    private val newKey: () -> AES256Key = ::generateRandomAES256Key,
) {

    /** Uploads [files] in order; the first failure ends the upload. [progress] reports bytes of the plain files. */
    suspend fun upload(
        files: List<ShareFile>,
        conversationId: ConversationId,
        progress: (uploaded: Long, total: Long) -> Unit,
    ): Either<ShareOutcome, List<UploadedFile>> {
        val total = files.sumOf { it.sizeInBytes }
        val reporter = ProgressReporter(total, progress)
        val uploaded = mutableListOf<UploadedFile>()
        var done = 0L
        for (file in files) {
            when (val result = upload(file, conversationId) { reporter.report(done + it) }) {
                is Either.Left -> return result
                is Either.Right -> uploaded += result.value
            }
            done += file.sizeInBytes
            // A file is uploaded once the backend answered, not once its last byte was read.
            reporter.report(done, force = true)
        }
        return Either.Right(uploaded)
    }

    @Suppress("TooGenericExceptionCaught")
    private suspend fun upload(file: ShareFile, conversationId: ConversationId, sent: (Long) -> Unit): Either<ShareOutcome, UploadedFile> {
        val encrypted = temporaryDirectory / "share-${Uuid.random()}.aes"
        return try {
            val key = newKey()
            val plain = fileSystem.source(file.path.toPath())
            val md5 = try {
                HashingSink.md5(fileSystem.sink(encrypted))
            } catch (failure: Exception) {
                plain.close()
                throw failure
            }
            val sha256 = HashingSink.sha256(md5)
            // Closes the plain file and both hashing sinks.
            val size = encryptFileWithAES256(plain, key, sha256)
            val metadata = AssetMetadataRequest(
                mimeType = file.mimeType,
                public = false,
                retentionType = AssetRetentionType.PERSISTENT,
                md5 = md5.hash.base64(),
                conversationId = conversationId.toApi().takeIf { auditLog },
                filename = file.name.takeIf { auditLog },
                filetype = file.mimeType.takeIf { auditLog },
            )
            val response = wrapApiRequest {
                // Authentication retries reopen the file, and the transport closes each attempt's source.
                assetApi.uploadAsset(metadata, { CountingSource(fileSystem.source(encrypted), file.sizeInBytes, sent) }, size)
            }
            when (response) {
                is Either.Left -> Either.Left(response.value.outcome())
                is Either.Right -> Either.Right(
                    UploadedFile(
                        file = file,
                        otrKey = key,
                        sha256 = SHA256Key(sha256.hash.toByteArray()),
                        assetId = UploadedAssetId(response.value.key, response.value.domain, response.value.token),
                        encryptedSize = size,
                    )
                )
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            // Reading the file or encrypting it failed; Apple encryption throws CryptographyException.
            kaliumLogger.w("Share upload failed: ${failure::class.simpleName}")
            Either.Left(ShareOutcome(ShareStatus.FAILED, UPLOAD_FAILED))
        } finally {
            fileSystem.delete(encrypted, mustExist = false)
        }
    }

    private fun NetworkFailure.outcome() = when (this) {
        is NetworkFailure.NoNetworkConnection -> ShareOutcome(ShareStatus.OFFLINE, UPLOAD_FAILED)
        else -> ShareOutcome(ShareStatus.FAILED, UPLOAD_FAILED)
    }

    /**
     * Counts the ciphertext read, held below the plain size: the file is done only once the backend answers,
     * and an authentication retry reads it again.
     */
    private class CountingSource(delegate: Source, private val plainSize: Long, private val sent: (Long) -> Unit) :
        ForwardingSource(delegate) {
        private var read = 0L

        override fun read(sink: Buffer, byteCount: Long): Long = super.read(sink, byteCount).also {
            if (it > 0) {
                read += it
                sent(minOf(read, maxOf(plainSize - 1, 0)))
            }
        }
    }

    /** Reports each whole percent once, so the host is not called for every chunk, and each finished file. */
    private class ProgressReporter(private val total: Long, private val progress: (Long, Long) -> Unit) {
        private var percent = -1L

        fun report(uploaded: Long, force: Boolean = false) {
            val current = if (total > 0) uploaded * PERCENT / total else PERCENT
            if (current > percent || force) {
                percent = maxOf(percent, current)
                progress(uploaded, total)
            }
        }
    }

    private companion object {
        const val PERCENT = 100L
        const val UPLOAD_FAILED = "UPLOAD_FAILED"
    }
}
