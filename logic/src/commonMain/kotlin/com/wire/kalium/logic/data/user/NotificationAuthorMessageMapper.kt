package com.wire.kalium.logic.data.user

import com.wire.kalium.logic.data.notification.LocalNotificationMessageAuthor
import com.wire.kalium.logic.data.user.other.model.OtherUser

interface NotificationAuthorMessageMapper {
    fun fromOtherUserToLocalNotificationMessageAuthor(author: OtherUser?): LocalNotificationMessageAuthor
}

class NotificationAuthorMessageMapperImpl : NotificationAuthorMessageMapper {

    override fun fromOtherUserToLocalNotificationMessageAuthor(author: OtherUser?) =
        LocalNotificationMessageAuthor(author?.name ?: "", null)

}
