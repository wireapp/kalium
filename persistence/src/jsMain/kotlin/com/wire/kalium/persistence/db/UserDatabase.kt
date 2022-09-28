@file:Suppress("MatchingDeclarationName")
package com.wire.kalium.persistence.db

import com.wire.kalium.persistence.UserDatabase
import com.wire.kalium.persistence.dao.UserIDEntity

internal actual class PlatformDatabaseData

fun UserDatabaseProvider(): UserDatabaseProvider = TODO()

internal actual fun nuke(
    userId: UserIDEntity,
    database: UserDatabase,
    platformDatabaseData: PlatformDatabaseData
): Boolean = TODO()
