/*
 * Wire
 * Copyright (C) 2023 Wire Swiss GmbH
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

package com.wire.kalium.persistence.db

import app.cash.sqldelight.EnumColumnAdapter
import app.cash.sqldelight.adapter.primitive.IntColumnAdapter
import com.wire.kalium.persistence.Call
import com.wire.kalium.persistence.Client
import com.wire.kalium.persistence.Connection
import com.wire.kalium.persistence.Conversation
import com.wire.kalium.persistence.Member
import com.wire.kalium.persistence.Message
import com.wire.kalium.persistence.MessageAssetContent
import com.wire.kalium.persistence.MessageConversationChangedContent
import com.wire.kalium.persistence.MessageConversationReceiptModeChangedContent
import com.wire.kalium.persistence.MessageConversationTimerChangedContent
import com.wire.kalium.persistence.MessageFailedToDecryptContent
import com.wire.kalium.persistence.MessageMemberChangeContent
import com.wire.kalium.persistence.MessageMention
import com.wire.kalium.persistence.MessageMissedCallContent
import com.wire.kalium.persistence.MessageNewConversationReceiptModeContent
import com.wire.kalium.persistence.MessageRestrictedAssetContent
import com.wire.kalium.persistence.MessageTextContent
import com.wire.kalium.persistence.MessageUnknownContent
import com.wire.kalium.persistence.Reaction
import com.wire.kalium.persistence.Receipt
import com.wire.kalium.persistence.SelfUser
import com.wire.kalium.persistence.Service
import com.wire.kalium.persistence.UnreadEvent
import com.wire.kalium.persistence.User
import com.wire.kalium.persistence.adapter.ContentTypeAdapter
import com.wire.kalium.persistence.adapter.ConversationAccessListAdapter
import com.wire.kalium.persistence.adapter.ConversationAccessRoleListAdapter
import com.wire.kalium.persistence.adapter.InstantTypeAdapter
import com.wire.kalium.persistence.adapter.MemberRoleAdapter
import com.wire.kalium.persistence.adapter.QualifiedIDAdapter
import com.wire.kalium.persistence.adapter.QualifiedIDListAdapter
import com.wire.kalium.persistence.adapter.BotServiceAdapter
import com.wire.kalium.persistence.adapter.ServiceTagListAdapter

internal object TableMapper {
    val callAdapter = Call.Adapter(
        conversation_idAdapter = QualifiedIDAdapter,
        statusAdapter = EnumColumnAdapter(),
        conversation_typeAdapter = EnumColumnAdapter(),
        typeAdapter = EnumColumnAdapter()
    )
    val clientAdapter = Client.Adapter(
        user_idAdapter = QualifiedIDAdapter,
        device_typeAdapter = EnumColumnAdapter(),
        client_typeAdapter = EnumColumnAdapter(),
        registration_dateAdapter = InstantTypeAdapter
    )
    val connectionAdapter = Connection.Adapter(
        qualified_conversationAdapter = QualifiedIDAdapter,
        qualified_toAdapter = QualifiedIDAdapter,
        statusAdapter = EnumColumnAdapter(),
        last_update_dateAdapter = InstantTypeAdapter,
    )
    val conversationAdapter = Conversation.Adapter(
        qualified_idAdapter = QualifiedIDAdapter,
        typeAdapter = EnumColumnAdapter(),
        mls_group_stateAdapter = EnumColumnAdapter(),
        protocolAdapter = EnumColumnAdapter(),
        muted_statusAdapter = EnumColumnAdapter(),
        access_listAdapter = ConversationAccessListAdapter(),
        access_role_listAdapter = ConversationAccessRoleListAdapter(),
        mls_cipher_suiteAdapter = EnumColumnAdapter(),
        receipt_modeAdapter = EnumColumnAdapter(),
        last_read_dateAdapter = InstantTypeAdapter,
        last_modified_dateAdapter = InstantTypeAdapter,
        last_notified_dateAdapter = InstantTypeAdapter,
        mls_last_keying_material_update_dateAdapter = InstantTypeAdapter,
    )
    val memberAdapter = Member.Adapter(
        userAdapter = QualifiedIDAdapter,
        conversationAdapter = QualifiedIDAdapter,
        roleAdapter = MemberRoleAdapter
    )
    val messageAdapter = Message.Adapter(
        conversation_idAdapter = QualifiedIDAdapter,
        sender_user_idAdapter = QualifiedIDAdapter,
        statusAdapter = EnumColumnAdapter(),
        content_typeAdapter = ContentTypeAdapter,
        visibilityAdapter = EnumColumnAdapter(),
        creation_dateAdapter = InstantTypeAdapter,
        last_edit_dateAdapter = InstantTypeAdapter,
        self_deletion_start_dateAdapter = InstantTypeAdapter
    )
    val messageAssetContentAdapter = MessageAssetContent.Adapter(
        conversation_idAdapter = QualifiedIDAdapter,
        asset_widthAdapter = IntColumnAdapter,
        asset_heightAdapter = IntColumnAdapter,
        asset_upload_statusAdapter = EnumColumnAdapter(),
        asset_download_statusAdapter = EnumColumnAdapter(),
    )
    val messageConversationChangedContentAdapter = MessageConversationChangedContent.Adapter(
        conversation_idAdapter = QualifiedIDAdapter
    )
    val messageFailedToDecryptContentAdapter = MessageFailedToDecryptContent.Adapter(
        conversation_idAdapter = QualifiedIDAdapter
    )
    val messageMemberChangeContentAdapter = MessageMemberChangeContent.Adapter(
        conversation_idAdapter = QualifiedIDAdapter,
        member_change_listAdapter = QualifiedIDListAdapter,
        member_change_typeAdapter = EnumColumnAdapter()
    )
    val messageMentionAdapter = MessageMention.Adapter(
        conversation_idAdapter = QualifiedIDAdapter,
        user_idAdapter = QualifiedIDAdapter,
        startAdapter = IntColumnAdapter,
        lengthAdapter = IntColumnAdapter
    )
    val messageMissedCallContentAdapter = MessageMissedCallContent.Adapter(
        conversation_idAdapter = QualifiedIDAdapter,
        caller_idAdapter = QualifiedIDAdapter
    )
    val messageRestrictedAssetContentAdapter = MessageRestrictedAssetContent.Adapter(
        conversation_idAdapter = QualifiedIDAdapter
    )
    val messageTextContentAdapter = MessageTextContent.Adapter(
        conversation_idAdapter = QualifiedIDAdapter
    )
    val messageUnknownContentAdapter = MessageUnknownContent.Adapter(
        conversation_idAdapter = QualifiedIDAdapter
    )
    val reactionAdapter = Reaction.Adapter(
        conversation_idAdapter = QualifiedIDAdapter,
        sender_idAdapter = QualifiedIDAdapter
    )
    val receiptAdapter = Receipt.Adapter(
        conversation_idAdapter = QualifiedIDAdapter,
        user_idAdapter = QualifiedIDAdapter,
        typeAdapter = EnumColumnAdapter()
    )
    val selfUserAdapter = SelfUser.Adapter(
        idAdapter = QualifiedIDAdapter
    )
    val userAdapter = User.Adapter(
        qualified_idAdapter = QualifiedIDAdapter,
        accent_idAdapter = IntColumnAdapter,
        connection_statusAdapter = EnumColumnAdapter(),
        user_availability_statusAdapter = EnumColumnAdapter(),
        preview_asset_idAdapter = QualifiedIDAdapter,
        complete_asset_idAdapter = QualifiedIDAdapter,
        user_typeAdapter = EnumColumnAdapter(),
        bot_serviceAdapter = BotServiceAdapter()
    )
    val messageNewConversationReceiptModeContentAdapter = MessageNewConversationReceiptModeContent.Adapter(
        conversation_idAdapter = QualifiedIDAdapter
    )
    val messageConversationReceiptModeChangedContentAdapter = MessageConversationReceiptModeChangedContent.Adapter(
        conversation_idAdapter = QualifiedIDAdapter
    )
    val messageConversationTimerChangedContentAdapter = MessageConversationTimerChangedContent.Adapter(
        conversation_idAdapter = QualifiedIDAdapter
    )

    val unreadEventAdapter = UnreadEvent.Adapter(
        conversation_idAdapter = QualifiedIDAdapter,
        typeAdapter = EnumColumnAdapter(),
        creation_dateAdapter = InstantTypeAdapter,
    )

    val serviceAdapter = Service.Adapter(
        idAdapter = BotServiceAdapter(),
        tagsAdapter = ServiceTagListAdapter,
        preview_asset_idAdapter = QualifiedIDAdapter,
        complete_asset_idAdapter = QualifiedIDAdapter
    )
}
