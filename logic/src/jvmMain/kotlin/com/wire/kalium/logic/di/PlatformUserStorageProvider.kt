package com.wire.kalium.logic.di

import com.wire.kalium.logic.data.id.toDao
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.persistence.db.PlatformDatabaseData
import com.wire.kalium.persistence.db.userDatabaseBuilder
import com.wire.kalium.persistence.kmmSettings.UserPrefBuilder
import com.wire.kalium.util.KaliumDispatcherImpl
import java.io.File

internal actual class PlatformUserStorageProvider : UserStorageProvider() {
    override fun create(userId: UserId, shouldEncryptData: Boolean, platformProperties: PlatformUserStorageProperties): UserStorage {
        val userIdEntity = userId.toDao()
        val pref = UserPrefBuilder(userIdEntity, platformProperties.rootPath, shouldEncryptData)
        val database = userDatabaseBuilder(
            platformDatabaseData = PlatformDatabaseData(File(platformProperties.rootStoragePath)),
            userId = userIdEntity,
            passphrase = null,
            dispatcher = KaliumDispatcherImpl.io,
            enableWAL = false
        )
        return UserStorage(database, pref)
    }

}
