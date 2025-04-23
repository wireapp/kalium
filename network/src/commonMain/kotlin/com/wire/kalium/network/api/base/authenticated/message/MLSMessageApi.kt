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

package com.wire.kalium.network.api.base.authenticated.message

import com.wire.kalium.network.api.authenticated.message.SendMLSMessageResponse
import com.wire.kalium.network.utils.NetworkResponse
import kotlin.jvm.JvmInline

interface MLSMessageApi {

    /**
     * A welcome message allows an MLS client to join an existing MLS group.
     */
    @JvmInline
    value class WelcomeMessage(val value: ByteArray)

    /**
     * An MLS message can either be an application message containing user content or
     * a handshake message which updates the group state (adding, removing clients).
     */
    @JvmInline
    value class Message(val value: ByteArray)

    /**
     * An commitBundle originated CommitBundle object.
     * The CommitBundle object contains a welcome message [can be null], a commit message
     * and the groupInfo.
     */
    @JvmInline
    value class CommitBundle(val value: ByteArray)

    /**
     * Send an MLS message to a MLS group. The destination group is encoded into the message itself.
     *
     * @param message MLS Message
     */
    suspend fun sendMessage(message: ByteArray): NetworkResponse<SendMLSMessageResponse>

    /**
     * Send an MLS commit bundle to a MLS group. The destination group is encoded into the message itself.
     *
     * @param bundle
     */
    suspend fun sendCommitBundle(bundle: CommitBundle): NetworkResponse<SendMLSMessageResponse>

}
