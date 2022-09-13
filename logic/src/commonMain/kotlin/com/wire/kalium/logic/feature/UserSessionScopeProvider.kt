package com.wire.kalium.logic.feature

import com.wire.kalium.logic.data.user.UserId
import io.ktor.util.collections.ConcurrentMap

interface UserSessionScopeProvider {
    fun get(userId: UserId): UserSessionScope?
    fun getOrCreate(userId: UserId): UserSessionScope
    fun delete(userId: UserId)
}

abstract class UserSessionScopeProviderCommon : UserSessionScopeProvider {

    private val userScopeStorage: ConcurrentMap<UserId, UserSessionScope> by lazy {
        ConcurrentMap()
    }

    override fun getOrCreate(userId: UserId): UserSessionScope =
        userScopeStorage.computeIfAbsent(userId) { create(userId) }

    override fun get(userId: UserId): UserSessionScope? = userScopeStorage.get(userId)

    override fun delete(userId: UserId) {
        userScopeStorage.remove(userId)
    }

    abstract fun create(userId: UserId): UserSessionScope
}

expect class UserSessionScopeProviderImpl : UserSessionScopeProviderCommon
