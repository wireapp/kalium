package com.wire.kalium.network.api.messages

import com.wire.kalium.network.api.message.EnvelopeProtoMapper
import com.wire.kalium.network.api.message.MessageApi
import com.wire.messages.Otr

actual class EnvelopeProtoMapperImpl : EnvelopeProtoMapper {

    private val otrClientEntryMapper = OtrClientEntryMapper()
    private val otrUserIdMapper = OtrUserIdMapper()
    private val otrClientIdMapper = OtrClientIdMapper()

    override fun encodeToProtobuf(envelopeParameters: MessageApi.Parameters.QualifiedDefaultParameters): ByteArray {

        val qualifiedEntries = envelopeParameters.recipients.entries.groupBy({ it.key.domain }) { userEntry ->
            val clientEntries = userEntry.value.entries.map(otrClientEntryMapper::toOtrClientEntry)
            Otr.UserEntry.newBuilder()
                .setUser(otrUserIdMapper.toOtrUserId(userEntry.key.value))
                .addAllClients(clientEntries)
                .build()
        }.entries.map { (domain, userEntries) ->
            Otr.QualifiedUserEntry.newBuilder()
                .setDomain(domain)
                .addAllEntries(userEntries)
                .build()
        }
        return Otr.QualifiedNewOtrMessage.newBuilder()
            .addAllRecipients(qualifiedEntries)
            .setSender(otrClientIdMapper.toOtrClientId(envelopeParameters.sender))
            //TODO Handle different report types, remote push, etc.
            .setReportAll(Otr.ClientMismatchStrategy.ReportAll.newBuilder().build())
            .build()
            .toByteArray()
    }

}
