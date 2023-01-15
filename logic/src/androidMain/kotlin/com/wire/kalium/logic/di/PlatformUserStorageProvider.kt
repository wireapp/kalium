package com.wire.kalium.logic.di

import com.wire.kalium.logic.data.id.toDao
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.persistence.db.userDatabaseBuilder
import com.wire.kalium.persistence.kmmSettings.UserPrefBuilder
import com.wire.kalium.util.KaliumDispatcherImpl

internal actual class PlatformUserStorageProvider : UserStorageProvider() {
    override fun create(userId: UserId, shouldEncryptData: Boolean, platformProperties: PlatformUserStorageProperties): UserStorage {
        val userIdEntity = userId.toDao()
        val pref = UserPrefBuilder(userIdEntity, platformProperties.applicationContext, shouldEncryptData)
        val database = userDatabaseBuilder(
            platformProperties.applicationContext,
            userIdEntity,
            platformProperties.securityHelper.userDBSecret(userId),
            shouldEncryptData,
            KaliumDispatcherImpl.io
        )
        return UserStorage(database, pref)
    }
}
