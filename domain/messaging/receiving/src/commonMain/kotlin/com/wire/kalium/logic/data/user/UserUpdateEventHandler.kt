/*
 * Wire
 * Copyright (C) 2026 Wire Swiss GmbH
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.wire.kalium.logic.data.user

import com.wire.kalium.common.error.wrapStorageRequest
import com.wire.kalium.logic.data.event.Event
import com.wire.kalium.logic.data.id.toDao
import com.wire.kalium.persistence.dao.PartialUserEntity
import com.wire.kalium.persistence.dao.QualifiedIDEntity
import com.wire.kalium.persistence.dao.UserDAO

/** The app's partial update preserves fields absent from the event; unknown users remain absent. */
class UserUpdateEventHandler(private val userDAO: UserDAO) {
    suspend fun handle(event: Event.User.Update) = wrapStorageRequest {
        userDAO.updateUser(event.toPartialUserEntity())
    }
}

fun Event.User.Update.toPartialUserEntity(): PartialUserEntity =
        PartialUserEntity(
            email = email,
            name = name,
            handle = handle,
            accentId = accentId,
            previewAssetId = previewAssetId?.let { QualifiedIDEntity(it, userId.domain) },
            completeAssetId = completeAssetId?.let { QualifiedIDEntity(it, userId.domain) },
            supportedProtocols = supportedProtocols?.toDao(),
            id = userId.toDao()
        )

suspend fun updateUserAvailability(userDAO: UserDAO, userId: UserId, status: UserAvailabilityStatus) {
    userDAO.updateUserAvailabilityStatus(userId.toDao(), AvailabilityStatusMapperImpl().fromModelAvailabilityStatusToDao(status))
}
