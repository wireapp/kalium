package com.wire.kalium.persistence.model

// TODO: good place to use Either
sealed class DataStoreResult<out T> {
    data class Success<out T>(val data: T): DataStoreResult<T>()
    class DataNotFound<out T>: DataStoreResult<T>()
}
