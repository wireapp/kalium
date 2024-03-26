/*
 * Wire
 * Copyright (C) 2024 Wire Swiss GmbH
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see http://www.gnu.org/licenses/.
 */
package com.wire.kalium.logic.data.conversation

import com.wire.kalium.logic.data.id.GroupID
import com.wire.kalium.logic.data.id.toModel

fun com.wire.kalium.cryptography.DecryptedMessageBundle.toModel(groupID: GroupID): DecryptedMessageBundle =
    DecryptedMessageBundle(
        groupID,
        message?.let { message ->
            // We will always have senderClientId together with an application message
            // but CoreCrypto API doesn't express this
            ApplicationMessage(
                message = message,
                senderID = senderClientId!!.toModel().userId,
                senderClientID = senderClientId!!.toModel().clientId
            )
        },
        commitDelay,
        identity?.let { identity ->
            E2EIdentity(
                identity.clientId,
                identity.handle.handle,
                identity.displayName,
                identity.domain,
                identity.certificate,
                identity.status,
                identity.thumbprint
            )
        }
    )
