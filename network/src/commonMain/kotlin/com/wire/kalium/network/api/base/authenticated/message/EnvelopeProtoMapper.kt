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

import com.wire.kalium.protobuf.otr.ClientId
import com.benasher44.uuid.bytes
import com.benasher44.uuid.uuidFrom
import com.wire.kalium.network.api.authenticated.message.Parameters
import com.wire.kalium.network.api.authenticated.message.QualifiedMessageOption
import com.wire.kalium.protobuf.otr.ClientMismatchStrategy
import com.wire.kalium.protobuf.otr.QualifiedNewOtrMessage
import com.wire.kalium.protobuf.otr.QualifiedUserEntry
import com.wire.kalium.protobuf.otr.QualifiedUserId
import com.wire.kalium.protobuf.otr.UserEntry
import com.wire.kalium.protobuf.otr.UserId
import pbandk.ByteArr
import pbandk.encodeToByteArray

interface EnvelopeProtoMapper {
    fun encodeToProtobuf(envelopeParameters: Parameters.QualifiedDefaultParameters): ByteArray
}

internal class EnvelopeProtoMapperImpl : EnvelopeProtoMapper {

    private val otrClientEntryMapper = OtrClientEntryMapper()

    @OptIn(ExperimentalStdlibApi::class)
    override fun encodeToProtobuf(envelopeParameters: Parameters.QualifiedDefaultParameters): ByteArray {
        val qualifiedEntries = envelopeParameters.recipients.entries.groupBy({ it.key.domain }) { userEntry ->
            val clientEntries = userEntry.value.entries.map(otrClientEntryMapper::toOtrClientEntry)
            UserEntry(
                user = UserId(ByteArr(uuidFrom(userEntry.key.value).bytes)),
                clients = clientEntries
            )
        }.entries.map { (domain, userEntries) ->
            QualifiedUserEntry(
                domain = domain,
                entries = userEntries
            )
        }

        val strategy = when (envelopeParameters.messageOption) {
            is QualifiedMessageOption.IgnoreAll -> {
                QualifiedNewOtrMessage.ClientMismatchStrategy.IgnoreAll(ClientMismatchStrategy.IgnoreAll())
            }

            is QualifiedMessageOption.ReportAll -> {
                QualifiedNewOtrMessage.ClientMismatchStrategy.ReportAll(ClientMismatchStrategy.ReportAll())
            }

            is QualifiedMessageOption.IgnoreSome -> {
                QualifiedNewOtrMessage.ClientMismatchStrategy.IgnoreOnly(
                    ClientMismatchStrategy.IgnoreOnly(
                        (envelopeParameters.messageOption as QualifiedMessageOption.IgnoreSome).userIDs.map { // TODO KBX
                            QualifiedUserId(it.value, it.domain)
                        }
                    )
                )
            }

            is QualifiedMessageOption.ReportSome -> {
                QualifiedNewOtrMessage.ClientMismatchStrategy.ReportOnly(
                    ClientMismatchStrategy.ReportOnly(
                        (envelopeParameters.messageOption as QualifiedMessageOption.ReportSome).userIDs.map {
                            QualifiedUserId(it.value, it.domain)
                        }
                    )
                )
            }
        }

        return QualifiedNewOtrMessage(
            recipients = qualifiedEntries,
            sender = ClientId(envelopeParameters.sender.hexToLong()),
            blob = envelopeParameters.externalBlob?.let { ByteArr(it) },
            clientMismatchStrategy = strategy,
            nativePush = envelopeParameters.nativePush,
            transient = envelopeParameters.transient
        ).encodeToByteArray()
    }
}
