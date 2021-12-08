package com.wire.kalium.cli

import com.waz.model.Messages
import com.wire.kalium.base.ConversationsApplication
import com.wire.kalium.network.api.message.MessageApi
import com.wire.kalium.network.api.message.MessagePriority
import com.wire.kalium.network.api.message.SendMessageResponse
import java.io.File
import kotlinx.coroutines.flow.collect
import java.util.Base64

class CliReceiveApplication : ConversationsApplication() {


    override suspend fun onAppRunning() {
        super.onAppRunning()
        val flow = networkModule.eventApi.listenToLiveEvent(clientId)
        flow.collect {
            for (payload in it.payload!!) {
                if (payload.conversation == conversationId.value) {
                    val message = crypto.decrypt(
                        userId = payload.qualifiedFrom.value,
                        clientId = payload.data?.sender!!,
                        cypher = payload.data!!.text
                    )
                    val test = Base64.getDecoder().decode(message)
                    val genericMessage = Messages.GenericMessage.parseFrom(test)
                    echo(genericMessage)
                    if (genericMessage.hasText()) {
                        println("----------------------")
                    } else if (genericMessage.hasAsset() && genericMessage.asset.hasUploaded()) {
                        saveImage(genericMessage.asset.uploaded.assetId, genericMessage.asset.uploaded.otrKey.toByteArray())
                    }

                }
            }
        }

    }

    private suspend fun saveImage(assetId: String, otrKey: ByteArray) {
        val byteArray = networkModule.assetApi.downloadAsset(assetId, null).resultBody
        val image = Util.decrypt(encrypted = byteArray, key = otrKey)
        File("./data/images/${assetId}").writeBytes(image)
    }
}

fun main(args: Array<String>) = CliReceiveApplication().main(args)

