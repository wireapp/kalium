package com.wire.kalium.logic.di

import com.wire.kalium.logic.AuthenticatedDataSourceSet
import com.wire.kalium.logic.data.user.UserId


interface AuthenticatedDataSourceSetProvider {
    fun get(userId: UserId): AuthenticatedDataSourceSet?
    fun add(userId: UserId, authenticatedDataSourceSet: AuthenticatedDataSourceSet)
    fun delete(userId: UserId)
}

internal object AuthenticatedDataSourceSetProviderImpl: AuthenticatedDataSourceSetProvider {
    private val userScopeStorage: HashMap<UserId, AuthenticatedDataSourceSet>  by lazy {
        hashMapOf()
    }

    override fun get(userId: UserId): AuthenticatedDataSourceSet? = userScopeStorage[userId]

    override fun add(userId: UserId, authenticatedDataSourceSet: AuthenticatedDataSourceSet) {
        userScopeStorage[userId] = authenticatedDataSourceSet
    }

    override fun delete(userId: UserId) {
        userScopeStorage.remove(userId)
    }
}
