package com.wire.kalium.logic.di

import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.feature.UserSessionScope

interface UserSessionScopeProvider {
    fun get(userId: UserId): UserSessionScope?
    fun add(userId: UserId, authenticatedDataSourceSet: UserSessionScope)
    fun delete(userId: UserId)
}

internal object UserSessionScopeProviderImpl : UserSessionScopeProvider {
    private val userScopeStorage: HashMap<UserId, UserSessionScope> by lazy {
        hashMapOf()
    }

    override fun get(userId: UserId): UserSessionScope? = userScopeStorage[userId]

    override fun add(userId: UserId, authenticatedDataSourceSet: UserSessionScope) {
        userScopeStorage[userId] = authenticatedDataSourceSet
    }

    override fun delete(userId: UserId) {
        userScopeStorage.remove(userId)
    }
}
