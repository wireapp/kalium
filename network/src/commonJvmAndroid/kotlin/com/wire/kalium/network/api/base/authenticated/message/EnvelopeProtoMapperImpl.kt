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

package com.wire.kalium.network.api.base.authenticated.message

import com.wire.kalium.network.kaliumLogger
import com.wire.kalium.protobuf.otr.ClientMismatchStrategy
import com.wire.kalium.protobuf.otr.QualifiedNewOtrMessage
import com.wire.kalium.protobuf.otr.QualifiedUserEntry
import com.wire.kalium.protobuf.otr.UserEntry
import pbandk.ByteArr
import pbandk.encodeToByteArray

class EnvelopeProtoMapperImpl : EnvelopeProtoMapper {

    private val otrClientEntryMapper = OtrClientEntryMapper()
    private val otrUserIdMapper = OtrUserIdMapper()
    private val otrClientIdMapper = OtrClientIdMapper()

    override fun encodeToProtobuf(envelopeParameters: MessageApi.Parameters.QualifiedDefaultParameters): ByteArray {
        val qualifiedEntries = envelopeParameters.recipients.entries.groupBy({ it.key.domain }) { userEntry ->
            val clientEntries = userEntry.value.entries.map(otrClientEntryMapper::toOtrClientEntry)
            UserEntry(
                user = otrUserIdMapper.toOtrUserId(userEntry.key.value),
                clients = clientEntries
            )
        }.entries.map { (domain, userEntries) ->
            QualifiedUserEntry(
                domain = domain,
                entries = userEntries
            )
        }

        // TODO(messaging): Handle different report types, etc.
        val strategy = when (envelopeParameters.messageOption) {
            is MessageApi.QualifiedMessageOption.IgnoreAll -> {
                QualifiedNewOtrMessage.ClientMismatchStrategy.IgnoreAll(ClientMismatchStrategy.IgnoreAll())
            }
            is MessageApi.QualifiedMessageOption.ReportAll -> {
                QualifiedNewOtrMessage.ClientMismatchStrategy.ReportAll(ClientMismatchStrategy.ReportAll())
            }
            else -> {
                kaliumLogger.w("[EnvelopeProtoMapper] - Other types not being handled yet.")
                QualifiedNewOtrMessage.ClientMismatchStrategy.ReportAll(ClientMismatchStrategy.ReportAll())
            }
        }
        return QualifiedNewOtrMessage(
            recipients = qualifiedEntries,
            sender = otrClientIdMapper.toOtrClientId(envelopeParameters.sender),
            blob = envelopeParameters.externalBlob?.let { ByteArr(it) },
            clientMismatchStrategy = strategy,
            nativePush = envelopeParameters.nativePush,
            transient = envelopeParameters.transient
        ).encodeToByteArray()
    }
}

actual fun provideEnvelopeProtoMapper(): EnvelopeProtoMapper = EnvelopeProtoMapperImpl()
