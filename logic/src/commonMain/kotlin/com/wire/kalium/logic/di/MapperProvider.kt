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

package com.wire.kalium.logic.di

import com.wire.kalium.logic.configuration.server.ApiVersionMapper
import com.wire.kalium.logic.configuration.server.ApiVersionMapperImpl
import com.wire.kalium.logic.configuration.server.ServerConfigMapper
import com.wire.kalium.logic.configuration.server.ServerConfigMapperImpl
import com.wire.kalium.logic.data.asset.AssetMapper
import com.wire.kalium.logic.data.asset.AssetMapperImpl
import com.wire.kalium.logic.data.call.mapper.CallMapper
import com.wire.kalium.logic.data.call.mapper.CallMapperImpl
import com.wire.kalium.logic.data.client.ClientMapper
import com.wire.kalium.logic.data.connection.ConnectionMapper
import com.wire.kalium.logic.data.connection.ConnectionMapperImpl
import com.wire.kalium.logic.data.connection.ConnectionStatusMapper
import com.wire.kalium.logic.data.connection.ConnectionStatusMapperImpl
import com.wire.kalium.logic.data.conversation.ConversationMapper
import com.wire.kalium.logic.data.conversation.ConversationMapperImpl
import com.wire.kalium.logic.data.conversation.ConversationRoleMapper
import com.wire.kalium.logic.data.conversation.ConversationRoleMapperImpl
import com.wire.kalium.logic.data.conversation.ConversationStatusMapper
import com.wire.kalium.logic.data.conversation.ConversationStatusMapperImpl
import com.wire.kalium.logic.data.conversation.LegalHoldStatusMapper
import com.wire.kalium.logic.data.conversation.LegalHoldStatusMapperImpl
import com.wire.kalium.logic.data.conversation.MLSCommitBundleMapper
import com.wire.kalium.logic.data.conversation.MLSCommitBundleMapperImpl
import com.wire.kalium.logic.data.conversation.MemberMapper
import com.wire.kalium.logic.data.conversation.MemberMapperImpl
import com.wire.kalium.logic.data.conversation.ProtocolInfoMapper
import com.wire.kalium.logic.data.conversation.ProtocolInfoMapperImpl
import com.wire.kalium.logic.data.conversation.ReceiptModeMapper
import com.wire.kalium.logic.data.conversation.ReceiptModeMapperImpl
import com.wire.kalium.logic.data.e2ei.AcmeMapper
import com.wire.kalium.logic.data.e2ei.AcmeMapperImpl
import com.wire.kalium.logic.data.event.EventMapper
import com.wire.kalium.logic.data.featureConfig.FeatureConfigMapper
import com.wire.kalium.logic.data.featureConfig.FeatureConfigMapperImpl
import com.wire.kalium.logic.data.id.FederatedIdMapper
import com.wire.kalium.logic.data.id.FederatedIdMapperImpl
import com.wire.kalium.logic.data.id.IdMapper
import com.wire.kalium.logic.data.id.IdMapperImpl
import com.wire.kalium.logic.data.id.QualifiedIdMapper
import com.wire.kalium.logic.data.id.QualifiedIdMapperImpl
import com.wire.kalium.logic.data.message.EncryptionAlgorithmMapper
import com.wire.kalium.logic.data.message.MessageMapper
import com.wire.kalium.logic.data.message.MessageMapperImpl
import com.wire.kalium.logic.data.message.ProtoContentMapper
import com.wire.kalium.logic.data.message.ProtoContentMapperImpl
import com.wire.kalium.logic.data.message.SendMessageFailureMapper
import com.wire.kalium.logic.data.message.SendMessageFailureMapperImpl
import com.wire.kalium.logic.data.message.SendMessagePartialFailureMapper
import com.wire.kalium.logic.data.message.SendMessagePartialFailureMapperImpl
import com.wire.kalium.logic.data.message.linkpreview.LinkPreviewMapper
import com.wire.kalium.logic.data.message.linkpreview.LinkPreviewMapperImpl
import com.wire.kalium.logic.data.message.mention.MessageMentionMapper
import com.wire.kalium.logic.data.message.mention.MessageMentionMapperImpl
import com.wire.kalium.logic.data.message.reaction.ReactionsMapper
import com.wire.kalium.logic.data.message.reaction.ReactionsMapperImpl
import com.wire.kalium.logic.data.message.receipt.ReceiptsMapper
import com.wire.kalium.logic.data.message.receipt.ReceiptsMapperImpl
import com.wire.kalium.logic.data.mlspublickeys.MLSPublicKeysMapper
import com.wire.kalium.logic.data.mlspublickeys.MLSPublicKeysMapperImpl
import com.wire.kalium.logic.data.notification.LocalNotificationMessageMapper
import com.wire.kalium.logic.data.notification.LocalNotificationMessageMapperImpl
import com.wire.kalium.logic.data.prekey.PreKeyListMapper
import com.wire.kalium.logic.data.prekey.PreKeyMapper
import com.wire.kalium.logic.data.prekey.PreKeyMapperImpl
import com.wire.kalium.logic.data.service.ServiceMapper
import com.wire.kalium.logic.data.session.SessionMapper
import com.wire.kalium.logic.data.session.SessionMapperImpl
import com.wire.kalium.logic.data.session.SessionRepository
import com.wire.kalium.logic.data.team.TeamMapper
import com.wire.kalium.logic.data.team.TeamMapperImpl
import com.wire.kalium.logic.data.user.AvailabilityStatusMapper
import com.wire.kalium.logic.data.user.AvailabilityStatusMapperImpl
import com.wire.kalium.logic.data.user.ConnectionStateMapper
import com.wire.kalium.logic.data.user.ConnectionStateMapperImpl
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.data.user.UserMapper
import com.wire.kalium.logic.data.user.UserMapperImpl
import com.wire.kalium.logic.data.user.type.DomainUserTypeMapper
import com.wire.kalium.logic.data.user.type.DomainUserTypeMapperImpl
import com.wire.kalium.logic.data.user.type.UserEntityTypeMapper
import com.wire.kalium.logic.data.user.type.UserEntityTypeMapperImpl
import com.wire.kalium.logic.feature.e2ei.CertificateStatusMapper
import com.wire.kalium.logic.feature.e2ei.CertificateStatusMapperImpl

@Suppress("TooManyFunctions")
internal object MapperProvider {
    fun apiVersionMapper(): ApiVersionMapper = ApiVersionMapperImpl()
    fun idMapper(): IdMapper = IdMapperImpl()
    fun serverConfigMapper(): ServerConfigMapper = ServerConfigMapperImpl(apiVersionMapper())
    fun sessionMapper(): SessionMapper = SessionMapperImpl()
    fun availabilityStatusMapper(): AvailabilityStatusMapper = AvailabilityStatusMapperImpl()
    fun connectionStateMapper(): ConnectionStateMapper = ConnectionStateMapperImpl()
    fun userMapper(): UserMapper = UserMapperImpl()

    fun userTypeMapper(): DomainUserTypeMapper = DomainUserTypeMapperImpl()
    fun reactionsMapper(): ReactionsMapper = ReactionsMapperImpl(domainUserTypeMapper = userTypeMapper())
    fun receiptsMapper(): ReceiptsMapper = ReceiptsMapperImpl(domainUserTypeMapper = userTypeMapper())
    fun teamMapper(): TeamMapper = TeamMapperImpl()
    fun messageMapper(selfUserId: UserId): MessageMapper = MessageMapperImpl(
        selfUserId = selfUserId
    )

    fun memberMapper(): MemberMapper = MemberMapperImpl(idMapper(), conversationRoleMapper())
    fun conversationMapper(selfUserId: UserId): ConversationMapper =
        ConversationMapperImpl(
            selfUserId,
            idMapper(),
            ConversationStatusMapperImpl(idMapper()),
            ProtocolInfoMapperImpl(),
            AvailabilityStatusMapperImpl(),
            DomainUserTypeMapperImpl(),
            ConnectionStatusMapperImpl(),
            ConversationRoleMapperImpl()
        )

    fun conversationRoleMapper(): ConversationRoleMapper = ConversationRoleMapperImpl()
    fun sendMessageFailureMapper(): SendMessageFailureMapper = SendMessageFailureMapperImpl()
    fun assetMapper(): AssetMapper = AssetMapperImpl()
    fun encryptionAlgorithmMapper(): EncryptionAlgorithmMapper = EncryptionAlgorithmMapper()
    fun eventMapper(selfUserId: UserId): EventMapper = EventMapper(
        memberMapper(),
        connectionMapper(),
        featureConfigMapper(),
        conversationRoleMapper(),
        selfUserId,
        receiptModeMapper(),
    )

    fun linkPreviewMapper(): LinkPreviewMapper = LinkPreviewMapperImpl(encryptionAlgorithmMapper())
    fun messageMentionMapper(selfUserId: UserId): MessageMentionMapper = MessageMentionMapperImpl(idMapper(), selfUserId)

    fun preyKeyMapper(): PreKeyMapper = PreKeyMapperImpl()
    fun preKeyListMapper(): PreKeyListMapper = PreKeyListMapper(preyKeyMapper())
    fun clientMapper(): ClientMapper = ClientMapper(preyKeyMapper())
    fun conversationStatusMapper(): ConversationStatusMapper = ConversationStatusMapperImpl(idMapper())
    fun protoContentMapper(selfUserId: UserId): ProtoContentMapper = ProtoContentMapperImpl(selfUserId = selfUserId)
    fun qualifiedIdMapper(selfUserId: UserId): QualifiedIdMapper = QualifiedIdMapperImpl(selfUserId)
    fun callMapper(selfUserId: UserId): CallMapper = CallMapperImpl(qualifiedIdMapper(selfUserId))
    fun connectionStatusMapper(): ConnectionStatusMapper = ConnectionStatusMapperImpl()
    fun featureConfigMapper(): FeatureConfigMapper = FeatureConfigMapperImpl()
    fun localNotificationMessageMapper(): LocalNotificationMessageMapper = LocalNotificationMessageMapperImpl()
    fun connectionMapper(): ConnectionMapper = ConnectionMapperImpl()
    fun userTypeEntityMapper(): UserEntityTypeMapper = UserEntityTypeMapperImpl()
    fun federatedIdMapper(
        userId: UserId,
        qualifiedIdMapper: QualifiedIdMapper,
        sessionRepository: SessionRepository
    ): FederatedIdMapper = FederatedIdMapperImpl(userId, qualifiedIdMapper, sessionRepository)

    fun mlsPublicKeyMapper(): MLSPublicKeysMapper = MLSPublicKeysMapperImpl()

    fun mlsCommitBundleMapper(): MLSCommitBundleMapper = MLSCommitBundleMapperImpl()

    fun protocolInfoMapper(): ProtocolInfoMapper = ProtocolInfoMapperImpl()
    fun receiptModeMapper(): ReceiptModeMapper = ReceiptModeMapperImpl()
    fun sendMessagePartialFailureMapper(): SendMessagePartialFailureMapper = SendMessagePartialFailureMapperImpl()
    fun serviceMapper(): ServiceMapper = ServiceMapper()
    fun legalHoldStatusMapper(): LegalHoldStatusMapper = LegalHoldStatusMapperImpl
    fun acmeMapper(): AcmeMapper = AcmeMapperImpl()
    fun certificateStatusMapper(): CertificateStatusMapper = CertificateStatusMapperImpl()
}
