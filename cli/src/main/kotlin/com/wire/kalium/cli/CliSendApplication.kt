package com.wire.kalium.cli

import com.wire.kalium.base.ConversationsApplication
import com.wire.kalium.network.api.message.UserIdToClientMap
import kotlinx.coroutines.runBlocking


class CliSendApplicationBase() : ConversationsApplication() {

    private lateinit var recipients: UserIdToClientMap

    override suspend fun onAppRunning() {
        super.onAppRunning()

        while (true) {
            val message = readLine()!!
            sendTextMessage(message = message)
        }
    }

    private suspend fun sendTextMessage(message: String) {
        TODO("not yet implemented")
        /*
        val msg = MessageText(message)
        val content = msg.createGenericMsg().toByteArray()
        val encryptedMessage = crypto.encrypt(recipients, content)
        val param = MessageApi.Parameters.DefaultParameters(
            sender = clientId,
            priority = MessagePriority.Low,
            nativePush = false,
            recipients = encryptedMessage,
            transient = false,
        )
        val messageResult =
            messageApi.sendMessage(conversationId = conversationId, option = MessageApi.MessageOption.ReportAll, parameters = param)
        when (messageResult.resultBody) {
            is SendMessageResponse.MessageSent -> {}
            is SendMessageResponse.MissingDevicesResponse -> {
                getConvRecipients()
                sendTextMessage(message)
            }
        }
        */
    }
}

fun main(args: Array<String>) = CliSendApplicationBase().main(args)
