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

import com.wire.kalium.common.error.NetworkFailure
import com.wire.kalium.common.functional.Either
import com.wire.kalium.common.functional.flatMap
import com.wire.kalium.common.functional.map
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.id.toDao
import com.wire.kalium.logic.data.message.CellAssetContent
import com.wire.kalium.logic.featureFlags.BuildFileRestrictionState
import com.wire.kalium.logic.sync.receiving.AppleAccountRuntime
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

/**
 * Checks [files] against the conversation and the account's rules, then uploads them. Sending checks the
 * conversation again after catching up; checking now avoids uploading for nothing.
 */
internal suspend fun AppleAccountRuntime.uploadSharedFiles(
    conversationId: ConversationId,
    files: List<ShareFile>,
    restriction: BuildFileRestrictionState,
    progress: (uploaded: Long, total: Long) -> Unit,
): Either<ShareOutcome, List<ShareItem>> =
    planSharedUploads(listOf(conversationId), files, restriction).flatMap { plan ->
        plan.blocked[conversationId]?.let { Either.Left(it) }
            ?: uploadSharedFiles(plan, files, progress).map { it.getValue(conversationId) }
    }

/** Where a share's files go: conversations that cannot take them now are [blocked], the others upload to [targets]. */
internal data class SharePlan(
    val conversations: List<ConversationId>,
    val blocked: Map<ConversationId, ShareOutcome>,
    val targets: List<UploadTarget>,
)

/** One upload of a share's files: assets that [Assets.conversations] all send, or a Cells conversation's cell. */
internal sealed interface UploadTarget {
    data class Assets(val conversations: List<ConversationId>) : UploadTarget
    data class Cell(val conversation: ConversationId, val cellName: String) : UploadTarget
}

/**
 * Checks [files] against each conversation and the account's rules, and plans their uploads. Sending checks the
 * conversations again after catching up; checking now avoids uploading for nothing.
 */
@Suppress("ReturnCount")
internal suspend fun AppleAccountRuntime.planSharedUploads(
    conversationIds: List<ConversationId>,
    files: List<ShareFile>,
    restriction: BuildFileRestrictionState,
): Either<ShareOutcome, SharePlan> {
    if (files.isEmpty()) return Either.Right(SharePlan(conversationIds, emptyMap(), emptyList()))
    val blocked = mutableMapOf<ConversationId, ShareOutcome>()
    val cells = mutableMapOf<ConversationId, String>()
    for (conversationId in conversationIds) {
        val conversation = database.conversationDAO.getConversationDetailsById(conversationId.toDao())
        val block = if (conversation == null) ShareBlock.UNAVAILABLE else database.shareBlock(conversation)
        val cellName = if (block == null && conversation?.wireCell != null && database.cellsConfigured()) {
            database.conversationDAO.getCellName(conversationId.toDao())
        } else null
        when {
            block != null -> blocked[conversationId] = ShareOutcome(ShareStatus.OPEN_APP, block.name)
            conversation?.wireCell == null -> Unit
            cellName == null -> blocked[conversationId] = ShareOutcome(ShareStatus.OPEN_APP, ShareBlock.UNAVAILABLE.name)
            else -> cells[conversationId] = cellName
        }
    }
    val open = conversationIds.filterNot { it in blocked }
    if (open.isEmpty()) return Either.Right(SharePlan(conversationIds, blocked, emptyList()))
    val policy = database.sharePolicy(prepared.userId, restriction)
    files.firstNotNullOfOrNull { policy.refusal(it.name, it.mimeType, it.sizeInBytes) }?.let {
        return Either.Left(ShareOutcome(ShareStatus.FAILED, it.name))
    }
    val regular = open.filterNot { it in cells }
    return Either.Right(SharePlan(conversationIds, blocked, uploadTargets(regular, cells, assetAuditLog())))
}

/**
 * Regular conversations send the same assets, so each file is encrypted and uploaded once for all of them, unless
 * the backend audits uploads, which name their conversation. A Cells conversation uploads to its own cell.
 */
internal fun uploadTargets(regular: List<ConversationId>, cells: Map<ConversationId, String>, auditLog: Boolean): List<UploadTarget> {
    val assets = if (auditLog) regular.map { listOf(it) } else listOf(regular).filter { it.isNotEmpty() }
    return assets.map(UploadTarget::Assets) + cells.map { (conversation, cellName) -> UploadTarget.Cell(conversation, cellName) }
}

/** Uploads [files] to each target of [plan]; see [uploadToTargets]. */
internal suspend fun AppleAccountRuntime.uploadSharedFiles(
    plan: SharePlan,
    files: List<ShareFile>,
    progress: (uploaded: Long, total: Long) -> Unit,
): Either<ShareOutcome, Map<ConversationId, List<ShareItem>>> {
    val auditLog = assetAuditLog()
    return uploadToTargets(
        plan,
        files,
        progress,
        assets = { conversationId, sent -> ShareUploads(network.assetApi, auditLog).upload(files, conversationId, sent) },
        cell = { cellName, sent -> uploadToCell(cellName, files, sent) },
        discard = ::discardSharedDrafts,
    )
}

/**
 * Uploads [files] once per target of [plan], reporting [progress] over every upload, and gives each conversation
 * that takes them its items; conversations of one target share its upload. The first failed upload ends the share
 * and discards the cells' uploads before it: nothing is stored before every upload succeeded.
 */
internal suspend fun uploadToTargets(
    plan: SharePlan,
    files: List<ShareFile>,
    progress: (uploaded: Long, total: Long) -> Unit,
    assets: suspend (ConversationId, (uploaded: Long, total: Long) -> Unit) -> Either<ShareOutcome, List<UploadedFile>>,
    cell: suspend (cellName: String, (uploaded: Long, total: Long) -> Unit) -> Either<ShareOutcome, List<CellAssetContent>>,
    discard: suspend (List<ShareItem>) -> Unit,
): Either<ShareOutcome, Map<ConversationId, List<ShareItem>>> {
    val size = files.sumOf { it.sizeInBytes }
    val total = size * plan.targets.size
    val items = plan.conversations.filterNot { it in plan.blocked }.associateWith { emptyList<ShareItem>() }.toMutableMap()
    val cells = mutableListOf<ShareItem>()
    var delivered = false
    try {
        for ((index, target) in plan.targets.withIndex()) {
            val sent = { uploaded: Long, _: Long -> progress(index * size + uploaded, total) }
            when (val uploaded = target.upload(files, sent, assets, cell)) {
                is Either.Left -> return uploaded
                is Either.Right -> items += uploaded.value
            }
            if (target is UploadTarget.Cell) cells += items.getValue(target.conversation)
        }
        delivered = true
        return Either.Right(items)
    } finally {
        if (!delivered) withContext(NonCancellable) { discard(cells) }
    }
}

private suspend fun UploadTarget.upload(
    files: List<ShareFile>,
    progress: (uploaded: Long, total: Long) -> Unit,
    assets: suspend (ConversationId, (uploaded: Long, total: Long) -> Unit) -> Either<ShareOutcome, List<UploadedFile>>,
    cell: suspend (cellName: String, (uploaded: Long, total: Long) -> Unit) -> Either<ShareOutcome, List<CellAssetContent>>,
): Either<ShareOutcome, Map<ConversationId, List<ShareItem>>> = when (this) {
    is UploadTarget.Assets -> assets(conversations.first(), progress).map { uploads ->
        conversations.associateWith { uploads.map(ShareItem::File) }
    }
    is UploadTarget.Cell -> cell(cellName, progress).map { attachments ->
        mapOf(conversation to listOf(ShareItem.Cells(files, attachments, cellName)))
    }
}

private suspend fun AppleAccountRuntime.uploadToCell(
    cellName: String,
    files: List<ShareFile>,
    progress: (uploaded: Long, total: Long) -> Unit,
): Either<ShareOutcome, List<CellAssetContent>> {
    val sharing = cellFileSharing()
    return try {
        when (val result = sharing.upload(cellName, files.map { it.cellFile() }, progress)) {
            is Either.Left -> Either.Left(
                ShareOutcome(
                    if (result.value is NetworkFailure.NoNetworkConnection) ShareStatus.OFFLINE else ShareStatus.FAILED,
                    "UPLOAD_FAILED"
                )
            )
            is Either.Right -> result
        }
    } finally {
        sharing.close()
    }
}
