package com.wire.kalium.network.api.message

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
        return QualifiedNewOtrMessage(
            recipients = qualifiedEntries,
            sender = otrClientIdMapper.toOtrClientId(envelopeParameters.sender),
            blob = envelopeParameters.data?.let { ByteArr(it) },
            //TODO(messaging): Handle different report types, etc.
            clientMismatchStrategy = QualifiedNewOtrMessage.ClientMismatchStrategy.ReportAll(ClientMismatchStrategy.ReportAll()),
            nativePush = envelopeParameters.nativePush,
            transient = envelopeParameters.transient
        ).encodeToByteArray()
    }
}

actual fun provideEnvelopeProtoMapper(): EnvelopeProtoMapper = EnvelopeProtoMapperImpl()
