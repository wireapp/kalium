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
package com.wire.backup.ingest

import com.wire.backup.data.BackupConversation
import com.wire.backup.data.BackupMessage
import com.wire.backup.data.BackupUser
import com.wire.backup.filesystem.BackupPage
import com.wire.kalium.protobuf.backup.BackupData
import com.wire.kalium.protobuf.decodeFromByteArray
import okio.Closeable
import okio.buffer
import kotlin.js.JsExport

@JsExport
public class BackupImportPager internal constructor(private val entries: List<BackupPage>) : Closeable {

    public val totalPagesCount: Int = entries.size

    public val conversationsPager: ConversationPager by lazy {
        ConversationPager(entries.filter { it.name.startsWith(BackupPage.CONVERSATIONS_PREFIX) })
    }

    public val messagesPager: MessagePager by lazy {
        MessagePager(entries.filter { it.name.startsWith(BackupPage.MESSAGES_PREFIX) })
    }

    public val usersPager: UserPager by lazy {
        UserPager(entries.filter { it.name.startsWith(BackupPage.USERS_PREFIX) })
    }

    override fun close() {
        entries.close()
    }
}

// The abstract / implementation are done this way to avoid having GenericClass<Data>, which can be lost on ObjC/Swift interop.
// Otherwise, it could have been just a single class.
/**
 * Handles paginated backup data import. This class provides functionality
 * to process and consume data from multiple ordered pages with a custom mapping applied to the data.
 *
 * It is guaranteed that the data will be read in the same order it was written during backup exporting.
 *
 * @param T Type of the data elements that are contained in each backup page.
 * @param entries Initial list of {@link BackupPage} representing the available backup pages.
 *                These pages are sorted based on their name, extracting numeric segments for ordering.
 */
@JsExport
public abstract class BackupImportDataPager<T> internal constructor(entries: List<BackupPage>) {
    private var nextPageIndex = 0
    private val pages = entries.sortedBy {
        it.name.filter { char -> char.isDigit() }.toInt()
    }.toMutableList()
    private val mapper = MPBackupMapper()
    public val totalPages: Int = pages.size

    /**
     * @return true if there are more pages to be read through [nextPage]. False otherwise.
     */
    public fun hasMorePages(): Boolean = nextPageIndex < totalPages

    /**
     * Gets the data stored in the next page, **consuming** it in the process.
     * @throws IllegalStateException if there are no further available pages.
     * @see hasMorePages
     */
    public fun nextPage(): Array<T> {
        val page = pages.removeFirstOrNull()
            ?: throw IllegalStateException("No more pages to consume! Check if there are pages before requesting one")
        nextPageIndex++
        val bytes = page.data.buffer().readByteArray()
        return mapPageData(mapper, bytes)
    }

    internal abstract fun mapPageData(mapper: MPBackupMapper, bytes: ByteArray): Array<T>
}

@JsExport
public class ConversationPager internal constructor(entries: List<BackupPage>) : BackupImportDataPager<BackupConversation>(entries) {
    override fun mapPageData(mapper: MPBackupMapper, bytes: ByteArray): Array<BackupConversation> {
        return mapper.fromProtoToBackupModel(BackupData.decodeFromByteArray(bytes)).conversations
    }
}

@JsExport
public class UserPager internal constructor(entries: List<BackupPage>) : BackupImportDataPager<BackupUser>(entries) {
    override fun mapPageData(mapper: MPBackupMapper, bytes: ByteArray): Array<BackupUser> {
        return mapper.fromProtoToBackupModel(BackupData.decodeFromByteArray(bytes)).users
    }
}

@JsExport
public class MessagePager internal constructor(entries: List<BackupPage>) : BackupImportDataPager<BackupMessage>(entries) {
    override fun mapPageData(mapper: MPBackupMapper, bytes: ByteArray): Array<BackupMessage> {
        return mapper.fromProtoToBackupModel(BackupData.decodeFromByteArray(bytes)).messages
    }
}

internal fun List<BackupPage>.close() = forEach { it.data.close() }
