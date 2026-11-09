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
import com.wire.kalium.common.error.NetworkFailure
import com.wire.kalium.common.functional.Either
import com.wire.kalium.common.functional.map
import com.wire.kalium.logic.configuration.server.ServerConfig
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.id.toDao
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.featureFlags.BuildFileRestrictionState
import com.wire.kalium.network.networkContainer.AuthenticatedNetworkContainer
import com.wire.kalium.network.session.SessionManager
import com.wire.kalium.persistence.db.UserDatabaseBuilder
import io.ktor.http.Url

/** Where a share's files go. Reading it needs the account; uploading there needs neither its database nor CoreCrypto. */
internal sealed interface UploadTarget {
    /** Regular assets; with [auditLog], the backend learns the conversation, the file and its type. */
    data class Assets(val auditLog: Boolean) : UploadTarget

    /** Drafts in the conversation's cell, on the account's Cells backend. */
    data class Cells(val cellName: String, val cellsUrl: String) : UploadTarget
}

/**
 * Checks [files] against the conversation and the account's rules, and reads where they go. Sending checks the
 * conversation again after catching up; checking now avoids uploading for nothing.
 */
@Suppress("ReturnCount")
internal suspend fun UserDatabaseBuilder.shareUploadTarget(
    selfUserId: UserId,
    links: ServerConfig.Links,
    conversationId: ConversationId,
    files: List<ShareFile>,
    restriction: BuildFileRestrictionState,
): Either<ShareOutcome, UploadTarget> {
    val conversation = conversationDAO.getConversationDetailsById(conversationId.toDao())
        ?: return Either.Left(ShareOutcome(ShareStatus.OPEN_APP, ShareBlock.UNAVAILABLE.name))
    shareBlock(conversation)?.let { return Either.Left(ShareOutcome(ShareStatus.OPEN_APP, it.name)) }
    val policy = sharePolicy(selfUserId, restriction)
    files.firstNotNullOfOrNull { policy.refusal(it.name, it.mimeType, it.sizeInBytes) }
        ?.let { return Either.Left(ShareOutcome(ShareStatus.FAILED, it.name)) }
    if (conversation.wireCell == null) return Either.Right(UploadTarget.Assets(assetAuditLog(links)))
    val cellsUrl = userConfigDAO.getWireCellsConfig()?.backendUrl?.takeIf { it.isNotBlank() }
    val cellName = conversationDAO.getCellName(conversationId.toDao())
    return if (cellsUrl == null || cellName == null) Either.Left(ShareOutcome(ShareStatus.OPEN_APP, ShareBlock.UNAVAILABLE.name))
    else Either.Right(UploadTarget.Cells(cellName, cellsUrl))
}

/** Uploads [files] over [network]; it reads and writes neither the account's database nor CoreCrypto. */
internal suspend fun UploadTarget.upload(
    network: AuthenticatedNetworkContainer,
    session: SessionManager,
    conversationId: ConversationId,
    files: List<ShareFile>,
    progress: (uploaded: Long, total: Long) -> Unit,
): Either<ShareOutcome, List<ShareItem>> = when (this) {
    is UploadTarget.Assets -> ShareUploads(network.assetApi, auditLog).upload(files, conversationId, progress)
        .map { uploads -> uploads.map { ShareItem.File(it) } }
    is UploadTarget.Cells -> {
        val sharing = CellFileSharing.create(network.cellsHttpClient, session, network.accessTokenApi, cellsUrl)
        try {
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
}

/** The app names the conversation, the file and its type to an auditing on-premises backend. */
private suspend fun UserDatabaseBuilder.assetAuditLog(links: ServerConfig.Links): Boolean =
    userConfigDAO.isAssetAuditLogEnabled() && links.isOnPremises && !ServerConfig.PRODUCTION.api.contains(Url(links.api).host)
