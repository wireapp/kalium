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
package com.wire.kalium.persistence.dao.conversation

import com.wire.kalium.persistence.ConversationMetadataQueries
import com.wire.kalium.persistence.dao.QualifiedIDEntity
import kotlinx.coroutines.withContext
import kotlin.coroutines.CoroutineContext

class ConversationMetaDataDAOImpl internal constructor(
    private val conversationMetadataQueries: ConversationMetadataQueries,
    private val coroutineContext: CoroutineContext,
    private val conversationMapper: ConversationMapper = ConversationMapper,
) : ConversationMetaDataDAO {
    override suspend fun isInformedAboutDegradedMLSVerification(
        conversationId: QualifiedIDEntity
    ): Boolean = withContext(coroutineContext) {
        conversationMetadataQueries.isInformedAboutDegradedMLSVerification(conversationId).executeAsOne()
    }

    override suspend fun setInformedAboutDegradedMLSVerificationFlag(conversationId: QualifiedIDEntity, isInformed: Boolean) {
        withContext(coroutineContext) {
            conversationMetadataQueries.updateInformedAboutDegradedMLSVerification(isInformed, conversationId)
        }
    }

    override suspend fun typeAndProtocolInfo(
        conversationId: QualifiedIDEntity
    ): ConversationTypeAndProtocolInfo? = withContext(coroutineContext) {
        conversationMetadataQueries.typeAndProtocolInfo(conversationId).executeAsOneOrNull()?.let {
            ConversationTypeAndProtocolInfo(
                type = it.type,
                isChannel = it.is_channel,
                protocolInfo = conversationMapper.mapProtocolInfo(
                    protocol = it.protocol,
                    mlsGroupId = it.mls_group_id,
                    mlsGroupState = it.mls_group_state,
                    mlsEpoch = it.mls_epoch,
                    mlsLastKeyingMaterialUpdate = it.mls_last_keying_material_update_date,
                    mlsCipherSuite = it.mls_cipher_suite
                )
            )
        }
    }
}

data class ConversationTypeAndProtocolInfo(
    val type: ConversationEntity.Type,
    val isChannel: Boolean,
    val protocolInfo: ConversationEntity.ProtocolInfo,
)
