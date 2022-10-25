package com.wire.kalium.cli

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.requireObject
import com.github.ajalt.clikt.output.TermUi
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.int
import com.sun.net.httpserver.HttpServer
import com.wire.kalium.logic.data.call.ConversationType
import com.wire.kalium.logic.data.conversation.Conversation
import com.wire.kalium.logic.feature.UserSessionScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.net.InetSocketAddress
import java.util.Scanner

class ConsoleContext(
    var currentConversation: Conversation?,
    var isMuted: Boolean = false
)

class KeyStroke(
    val key: Char,
    val handler: suspend (userSession: UserSessionScope, context: ConsoleContext) -> Int
) {
    suspend fun exec(userSession: UserSessionScope, context: ConsoleContext) = handler(userSession, context)
}

var strokes: Array<KeyStroke> = arrayOf(
    KeyStroke('l', ::listConversationsHandler),
    KeyStroke('c', ::startCallHandler),
    KeyStroke('a', ::answerCallHandler),
    KeyStroke('e', ::endCallHandler),
    KeyStroke('m', ::muteCallHandler),
    KeyStroke('s', ::selectConversationHandler),
    KeyStroke('q', ::quitApplication)
)

suspend fun executeStroke(userSession: UserSessionScope, context: ConsoleContext, key: Char) {
    for (stroke in strokes) {
        if (stroke.key.equals(key)) {
            stroke.handler(userSession, context)
            return
        }
    }
    TermUi.echo("Unknown stroke: $key")
}

suspend fun listConversationsHandler(userSession: UserSessionScope, context: ConsoleContext): Int {
    listConversations(userSession)
    return 0
}

suspend fun selectConversationHandler(userSession: UserSessionScope, context: ConsoleContext): Int {
    context.currentConversation = selectConversation(userSession)
    return 0
}

suspend fun startCallHandler(userSession: UserSessionScope, context: ConsoleContext): Int {
    val currentConversation = context.currentConversation ?: return -1

    val convType = when (currentConversation.type) {
        Conversation.Type.ONE_ON_ONE -> ConversationType.OneOnOne
        Conversation.Type.GROUP -> ConversationType.Conference
        else -> ConversationType.Unknown
    }

    userSession.calls.startCall.invoke(
        conversationId = currentConversation.id,
        conversationType = convType
    )

    return 0
}

suspend fun answerCallHandler(userSession: UserSessionScope, context: ConsoleContext): Int {
    val currentConversation = context.currentConversation ?: return -1
    userSession.calls.answerCall.invoke(conversationId = currentConversation.id)
    return 0
}

suspend fun endCallHandler(userSession: UserSessionScope, context: ConsoleContext): Int {
    val currentConversation = context.currentConversation ?: return -1
    userSession.calls.endCall.invoke(conversationId = currentConversation.id)
    return 0
}

suspend fun muteCallHandler(userSession: UserSessionScope, context: ConsoleContext): Int {
    val currentConversation = context.currentConversation ?: return -1

    context.isMuted = !context.isMuted

    if (context.isMuted)
        userSession.calls.muteCall(conversationId = currentConversation.id)
    else
        userSession.calls.unMuteCall(conversationId = currentConversation.id)

    return 0
}

suspend fun quitApplication(userSession: UserSessionScope, context: ConsoleContext): Int {
    kotlin.system.exitProcess(0)
    return 0
}

class ConsoleCommand : CliktCommand(name = "console") {
    private val port by option(help = "REST API server port").int().default(0)
    private val avsTest by option("-T").flag(default = false)
    private val avsNoise by option("-N").flag(default = false)

    private val userSession by requireObject<UserSessionScope>()
    private val context = ConsoleContext(null, false)

    override fun run() = runBlocking {
        val conversations = getConversations(userSession)
        context.currentConversation = conversations[0]

        if (port > 0) {
            HttpServer.create(InetSocketAddress(port), 0).apply {
                createContext("/stroke") { http ->
                    val stroke = http.getRequestURI().getQuery()[0]
                    echo("*** REST-stroke=$stroke")
                    val job = GlobalScope.launch(Dispatchers.Default) {
                        executeStroke(userSession, context, stroke)
                    }
                    http.responseHeaders.add("Content-type", "text/plain")
                    http.sendResponseHeaders(OK_STATUS, 0)
                    val os = http.getResponseBody()
                    // We should get the response from the stroke here....
                    // and send it on the os...
                    os.close()
                }
                createContext("/command") { http ->
                    val command = http.getRequestURI().getQuery()
                    echo("*** REST-COMMAND=$command")
                    val job = GlobalScope.launch(Dispatchers.Default) {
                        // executeCommand(userSession, stroke);
                    }
                    http.responseHeaders.add("Content-type", "text/plain")
                    http.sendResponseHeaders(OK_STATUS, 0)
                    val os = http.getResponseBody()
                    // We should get the response from the command here....
                    // and send it on the os...
                    os.close()
                }
                start()
            }
        }

        var avsFlags: Int = 0
        if (avsTest)
            avsFlags = AVS_FLAG_TEST
        if (avsNoise)
            avsFlags = AVS_FLAG_NOISE

        while (true) {
            val scanner = Scanner(System.`in`)
            val stroke = scanner.next().single()

            echo("stroke: $stroke")

            val job = GlobalScope.launch(Dispatchers.Default) {
                executeStroke(userSession, context, stroke)
            }
            job.join()
        }
    }

    companion object {
        const val OK_STATUS = 200
        const val AVS_FLAG_TEST = 2
        const val AVS_FLAG_NOISE = 8
    }
}
