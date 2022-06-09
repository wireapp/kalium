package com.wire.kalium.logic.configuration.notification

import com.wire.kalium.logic.StorageFailure
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.wrapStorageRequest
import com.wire.kalium.persistence.client.LastRetrievedNotificationEventStorage

interface LastNotificationEventRepository {
    fun persistLastNotificationEventId(id: String): Either<StorageFailure, Unit>
    fun getLastNotificationEventId(): Either<StorageFailure, String>
}

class LastNotificationEventDataSource(
    private val lastNotificationEventStorage: LastRetrievedNotificationEventStorage
) : LastNotificationEventRepository {

    override fun persistLastNotificationEventId(eventId: String): Either<StorageFailure, Unit> =
        wrapStorageRequest { lastNotificationEventStorage.saveEvent(eventId) }

    override fun getLastNotificationEventId(): Either<StorageFailure, String> =
        wrapStorageRequest { lastNotificationEventStorage.getLastEventId() }
}
