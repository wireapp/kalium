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

import com.wire.backup.data.BackupData
import com.wire.kalium.protobuf.backup.BackupData as ProtoBackupData
import pbandk.decodeFromByteArray
import kotlin.experimental.ExperimentalObjCRefinement
import kotlin.js.JsExport
import kotlin.native.ShouldRefineInSwift

/**
 * Entity able to parse backed-up data and returns
 * digestible data in [BackupData] format.
 */
@OptIn(ExperimentalObjCRefinement::class)
@JsExport
abstract class CommonMPBackupImporter(selfUserDomain: String) {
    private val mapper = MPBackupMapper(selfUserDomain)

    /**
     * Attempts to deserialize backed-up data.
     */
    @OptIn(ExperimentalStdlibApi::class)
    @ShouldRefineInSwift // Function not visible in Swift
    @Suppress("TooGenericExceptionCaught")
    fun importBackup(data: ByteArray): BackupImportResult = try {
        println("XPlatform Backup POC. Imported data bytes: ${data.toHexString()}")
        BackupImportResult.Success(
            mapper.fromProtoToBackupModel(ProtoBackupData.decodeFromByteArray(data))
        )
    } catch (e: Throwable) {
        e.printStackTrace()
        println(e)
        BackupImportResult.ParsingFailure
    }
}

expect class MPBackupImporter : CommonMPBackupImporter
