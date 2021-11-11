package com.wire.kalium

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.prompt
import com.github.ajalt.clikt.parameters.options.required

class CliApplication: CliktCommand() {
    val convid: String by option(help="conversation id").required()
    val message: String by option(help="message").required()

    val email: String by option(help = "wire account email").prompt(text = "email: ")
    val password: String by option(help = "wire account password").prompt(text = "password: ")
    override fun run() {
        // login stuff here
        echo("$convid, $message")
    }
}

fun main(args: Array<String>) = CliApplication().main(args)
