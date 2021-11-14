package com.wire.kalium

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import com.wire.kalium.crypto.CryptoFile
import com.wire.kalium.models.outbound.MessageText
import com.wire.kalium.tools.Logger
import org.glassfish.jersey.logging.LoggingFeature
import java.util.*
import java.util.logging.Level
import javax.ws.rs.client.Client
import javax.ws.rs.client.ClientBuilder

class CliApplication: CliktCommand() {
    val convid: String by option(help="conversation id").required()
    val message: String by option(help="message").required()

    //val email: String by option(help = "wire account email").prompt(text = "email ")
    //val password: String by option(help = "wire account password").prompt(text = "password ")
    override fun run() {
        // login stuff here
        val conversationId = UUID.fromString(convid)
        val httpClient: Client = ClientBuilder
                .newClient()
        val loggingFeature = LoggingFeature(Logger.getLOGGER(), Level.INFO, null, null)
        httpClient.register(loggingFeature)

        val crypto = CryptoFile("/tmp/joker")
        val app = Application("dejan+joker@wire.com", "12345678")
                .addClient(httpClient)
                .addCrypto(crypto)

        // Login, create device if needed, setup token refresh timer, pull missed messages and more
        app.start()

        // Create WireClient for this conversationId
        val wireClient = app.getWireClient(conversationId)

        // Send text
        wireClient.send(MessageText("Hi there from Kotlin!"))

        app.stop()
    }
}

fun main(args: Array<String>) = CliApplication().main(args)
