package com.wire.kalium.persistence.model

// TODO: good place to use Either
sealed class PreferencesResult<out T> {
    data class Success<out T>(val data: T): PreferencesResult<T>()
    object DataNotFound : PreferencesResult<Nothing>()
}
