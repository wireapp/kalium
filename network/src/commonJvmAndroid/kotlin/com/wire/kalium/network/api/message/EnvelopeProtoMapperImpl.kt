package com.wire.kalium.network.api.message

import com.wire.messages.Otr

class EnvelopeProtoMapperImpl: EnvelopeProtoMapper {

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
            //TODO(messaging): Handle different report types, remote push, etc.
            .setReportAll(Otr.ClientMismatchStrategy.ReportAll.newBuilder().build())
            .build()
            .toByteArray()
    }
}

actual fun provideEnvelopeProtoMapper(): EnvelopeProtoMapper = EnvelopeProtoMapperImpl()
