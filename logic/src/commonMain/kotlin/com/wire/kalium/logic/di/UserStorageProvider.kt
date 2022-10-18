package com.wire.kalium.logic.di

import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.persistence.db.UserDatabaseBuilder
import com.wire.kalium.persistence.kmmSettings.UserPrefBuilder
import io.ktor.util.collections.ConcurrentMap

data class UserStorage(val database: UserDatabaseBuilder, val preferences: UserPrefBuilder)
abstract class UserStorageProvider {
    private val inMemoryUserStorage: ConcurrentMap<UserId, UserStorage> = ConcurrentMap()
    fun getOrCreate(
        userId: UserId,
        platformUserStorageProperties: PlatformUserStorageProperties,
        shouldEncryptData: Boolean = true
    ): UserStorage = inMemoryUserStorage.computeIfAbsent(userId) {
        create(userId, shouldEncryptData, platformUserStorageProperties)
    }

    protected abstract fun create(
        userId: UserId,
        shouldEncryptData: Boolean,
        platformProperties: PlatformUserStorageProperties
    ): UserStorage

    fun clearInMemoryUserStorage(userId: UserId) = inMemoryUserStorage.remove(userId)
}

internal expect class PlatformUserStorageProvider constructor() : UserStorageProvider
expect class PlatformUserStorageProperties
