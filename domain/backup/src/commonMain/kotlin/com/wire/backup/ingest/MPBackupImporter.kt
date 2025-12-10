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
import com.wire.backup.encryption.DecryptionResult
import com.wire.backup.encryption.EncryptedStream
import com.wire.backup.encryption.XChaChaPoly1305AuthenticationData
import com.wire.backup.envelope.BackupHeader
import com.wire.backup.envelope.BackupHeaderSerializer
import com.wire.backup.envelope.HeaderParseResult
import com.wire.backup.filesystem.BackupPageStorage
import okio.Buffer
import okio.Sink
import okio.Source
import okio.buffer
import kotlin.js.JsExport

/**
 * Entity able to parse backed-up data and returns
 * digestible data in [BackupData] format.
 * @sample samples.backup.BackupSample.commonImport
 */
@JsExport
public expect class MPBackupImporter
