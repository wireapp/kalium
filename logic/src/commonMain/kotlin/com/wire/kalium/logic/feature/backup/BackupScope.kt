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
@file:Suppress("konsist.useCasesShouldNotAccessDaoLayerDirectly")

package com.wire.kalium.logic.feature.backup

import com.wire.kalium.logic.data.asset.KaliumFileSystem
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.data.user.UserRepository
import com.wire.kalium.logic.di.UserStorage
import com.wire.kalium.logic.data.id.CurrentClientIdProvider
import com.wire.kalium.logic.util.SecurityHelperImpl
import com.wire.kalium.persistence.kmmSettings.GlobalPrefProvider
import com.wire.kalium.util.DelicateKaliumApi

@Suppress("LongParameterList")
class BackupScope internal constructor(
    private val userId: UserId,
    private val clientIdProvider: CurrentClientIdProvider,
    private val userRepository: UserRepository,
    private val kaliumFileSystem: KaliumFileSystem,
    private val userStorage: UserStorage,
    val globalPreferences: GlobalPrefProvider,
) {
    val create: CreateBackupUseCase
        get() = CreateBackupUseCaseImpl(
            userId,
            clientIdProvider,
            userRepository,
            kaliumFileSystem,
            userStorage.database.databaseExporter,
            securityHelper = SecurityHelperImpl(globalPreferences.passphraseStorage)
        )

    val verify: VerifyBackupUseCase
        get() = VerifyBackupUseCaseImpl(userId, kaliumFileSystem)

    val restore: RestoreBackupUseCase
        get() = RestoreBackupUseCaseImpl(
            userStorage.database.databaseImporter,
            kaliumFileSystem,
            userId,
            userRepository,
            clientIdProvider,
        )

    @DelicateKaliumApi("this is NOT a backup feature, but a feature to create an unencrypted and obfuscated copy of the database")
    val createUnEncryptedCopy: CreateObfuscatedCopyUseCase
        get() = CreateObfuscatedCopyUseCase(
            userId,
            clientIdProvider,
            userRepository,
            kaliumFileSystem,
            userStorage.database.obfuscatedCopyExporter,
        )
}
