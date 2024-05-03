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

@file:JvmName("CryptoUtilsJvm")
package com.wire.kalium.cryptography.utils

import okio.Sink
import okio.Source

actual fun encryptDataWithAES256(data: PlainData, key: AES256Key): EncryptedData = AESEncrypt().encryptData(data, key)

actual fun encryptFileWithAES256(source: Source, key: AES256Key, sink: Sink) =
    AESEncrypt().encryptFile(source, key, sink)

actual fun decryptDataWithAES256(data: EncryptedData, secretKey: AES256Key): PlainData = AESDecrypt(secretKey).decryptData(data)

actual fun decryptFileWithAES256(source: Source, sink: Sink, secretKey: AES256Key) =
    AESDecrypt(secretKey).decryptFile(source, sink)

actual fun generateRandomAES256Key(): AES256Key = AESEncrypt().generateRandomAES256Key()
