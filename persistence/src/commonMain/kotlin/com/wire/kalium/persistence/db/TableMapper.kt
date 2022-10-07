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
import com.wire.kalium.persistence.MessageFailedToDecryptContent
import com.wire.kalium.persistence.MessageMemberChangeContent
import com.wire.kalium.persistence.MessageMention
import com.wire.kalium.persistence.MessageMissedCallContent
import com.wire.kalium.persistence.MessageRestrictedAssetContent
import com.wire.kalium.persistence.MessageTextContent
import com.wire.kalium.persistence.MessageUnknownContent
import com.wire.kalium.persistence.Reaction
import com.wire.kalium.persistence.SelfUser
import com.wire.kalium.persistence.User
import com.wire.kalium.persistence.dao.BotServiceAdapter
import com.wire.kalium.persistence.dao.ContentTypeAdapter
import com.wire.kalium.persistence.dao.ConversationAccessListAdapter
import com.wire.kalium.persistence.dao.ConversationAccessRoleListAdapter
import com.wire.kalium.persistence.dao.MemberRoleAdapter
import com.wire.kalium.persistence.dao.QualifiedIDAdapter
import com.wire.kalium.persistence.dao.QualifiedIDListAdapter

internal object TableMapper {
    val callAdapter = Call.Adapter(
        conversation_idAdapter = QualifiedIDAdapter,
        statusAdapter = EnumColumnAdapter(),
        conversation_typeAdapter = EnumColumnAdapter()
    )
    val clientAdapter = Client.Adapter(
        user_idAdapter = QualifiedIDAdapter,
        device_typeAdapter = EnumColumnAdapter()
    )
    val connectionAdapter = Connection.Adapter(
        qualified_conversationAdapter = QualifiedIDAdapter,
        qualified_toAdapter = QualifiedIDAdapter,
        statusAdapter = EnumColumnAdapter()
    )
    val conversationAdapter = Conversation.Adapter(
        qualified_idAdapter = QualifiedIDAdapter,
        typeAdapter = EnumColumnAdapter(),
        mls_group_stateAdapter = EnumColumnAdapter(),
        protocolAdapter = EnumColumnAdapter(),
        muted_statusAdapter = EnumColumnAdapter(),
        access_listAdapter = ConversationAccessListAdapter(),
        access_role_listAdapter = ConversationAccessRoleListAdapter(),
        mls_cipher_suiteAdapter = EnumColumnAdapter()
    )
    val memberAdapter = Member.Adapter(
        userAdapter = QualifiedIDAdapter,
        conversationAdapter = QualifiedIDAdapter,
        roleAdapter = MemberRoleAdapter()
    )
    val messageAdapter = Message.Adapter(
        conversation_idAdapter = QualifiedIDAdapter,
        sender_user_idAdapter = QualifiedIDAdapter,
        statusAdapter = EnumColumnAdapter(),
        content_typeAdapter = ContentTypeAdapter(),
        visibilityAdapter = EnumColumnAdapter(),
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
        member_change_listAdapter = QualifiedIDListAdapter(),
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
}
