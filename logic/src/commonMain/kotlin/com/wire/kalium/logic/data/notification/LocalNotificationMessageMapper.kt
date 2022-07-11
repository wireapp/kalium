package com.wire.kalium.logic.data.notification

import com.wire.kalium.logic.data.user.OtherUser

interface LocalNotificationMessageMapper {
    fun fromPublicUserToLocalNotificationMessageAuthor(author: OtherUser?): LocalNotificationMessageAuthor
}

class LocalNotificationMessageMapperImpl : LocalNotificationMessageMapper {

    override fun fromPublicUserToLocalNotificationMessageAuthor(author: OtherUser?) =
        LocalNotificationMessageAuthor(author?.name ?: "", null)

}
