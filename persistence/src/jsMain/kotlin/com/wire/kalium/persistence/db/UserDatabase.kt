@file:Suppress("MatchingDeclarationName")
package com.wire.kalium.persistence.db

import com.wire.kalium.persistence.dao.UserIDEntity
import kotlinx.coroutines.CoroutineDispatcher

actual class PlatformDatabaseData

actual fun userDatabaseBuilder(
    platformDatabaseData: PlatformDatabaseData,
    userId: UserIDEntity,
    passphrase: UserDBSecret?,
    dispatcher: CoroutineDispatcher,
    enableWAL: Boolean
): UserDatabaseBuilder = TODO("Not yet implemented")

internal actual fun nuke(
    userId: UserIDEntity,
    platformDatabaseData: PlatformDatabaseData
): Boolean = TODO()

internal actual fun getDatabaseAbsoluteFileLocation(
    platformDatabaseData: PlatformDatabaseData,
    userId: UserIDEntity
): String? = TODO()
