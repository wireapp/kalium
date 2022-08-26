package com.wire.kalium.logic.feature

import com.wire.kalium.logic.data.user.UserId

interface UserSessionScopeProvider {
    fun get(userId: UserId): UserSessionScope
    fun delete(userId: UserId)
}

abstract class UserSessionScopeProviderCommon: UserSessionScopeProvider {
    private val userScopeStorage: HashMap<UserId, UserSessionScope> by lazy {
        hashMapOf()
    }

    override fun get(userId: UserId): UserSessionScope =
        userScopeStorage.getOrPut(userId) { create(userId) }

    override fun delete(userId: UserId) {
        userScopeStorage.remove(userId)
    }

    abstract fun create(userId: UserId): UserSessionScope
}

expect class UserSessionScopeProviderImpl : UserSessionScopeProviderCommon
