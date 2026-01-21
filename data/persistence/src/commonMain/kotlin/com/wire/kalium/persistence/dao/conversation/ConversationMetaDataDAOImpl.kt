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
import com.wire.kalium.persistence.db.ReadDispatcher
import com.wire.kalium.persistence.db.WriteDispatcher
import kotlinx.coroutines.withContext

class ConversationMetaDataDAOImpl internal constructor(
    private val conversationMetadataQueries: ConversationMetadataQueries,
    private val readDispatcher: ReadDispatcher,
    private val writeDispatcher: WriteDispatcher,
    private val conversationMapper: ConversationMapper = ConversationMapper,
) : ConversationMetaDataDAO {
    override suspend fun isInformedAboutDegradedMLSVerification(
        conversationId: QualifiedIDEntity
    ): Boolean = withContext(readDispatcher.value) {
        conversationMetadataQueries.isInformedAboutDegradedMLSVerification(conversationId).executeAsOne()
    }

    override suspend fun setInformedAboutDegradedMLSVerificationFlag(conversationId: QualifiedIDEntity, isInformed: Boolean) {
        withContext(writeDispatcher.value) {
            conversationMetadataQueries.updateInformedAboutDegradedMLSVerification(isInformed, conversationId)
        }
    }

    override suspend fun typeAndProtocolInfo(
        conversationId: QualifiedIDEntity
    ): ConversationTypeAndProtocolInfo? = withContext(readDispatcher.value) {
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
