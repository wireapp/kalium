package com.wire.kalium.monkeys.server.routes

import com.wire.kalium.logic.CoreLogic
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.monkeys.conversation.Monkey
import com.wire.kalium.monkeys.server.model.AddMonkeysRequest
import com.wire.kalium.monkeys.server.model.CreateConversationRequest
import com.wire.kalium.monkeys.server.model.RemoveMonkeyRequest
import com.wire.kalium.monkeys.server.model.SendDMRequest
import com.wire.kalium.monkeys.server.model.SendMessageRequest
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.call
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing

fun Application.configureRoutes(monkey: Monkey, core: CoreLogic) {
    install(ContentNegotiation) {
        json()
    }
    routing {
        get("/$IS_SESSION_ACTIVE") {
            call.respond(HttpStatusCode.OK, monkey.isSessionActive())
        }
        get("/$CONNECTED_MONKEYS") {
            call.respond(HttpStatusCode.OK, monkey.connectedMonkeys())
        }
        get("/$PENDING_CONNECTIONS") {
            call.respond(HttpStatusCode.OK, monkey.pendingConnectionRequests())
        }
        post("/$LOGIN") {
            monkey.login(core) {}
            call.respond(HttpStatusCode.OK)
        }
        post("/$LOGOUT") {
            monkey.logout {}
            call.respond(HttpStatusCode.OK)
        }
        post("/$WARM_UP") {
            monkey.warmUp(core)
            call.respond(HttpStatusCode.OK)
        }
        post("/$SEND_REQUEST") {
            val request = call.receive<UserId>()
            monkey.sendRequest(Monkey.external(request))
            call.respond(HttpStatusCode.OK)
        }
        post("/$ACCEPT_REQUEST") {
            val request = call.receive<UserId>()
            monkey.acceptRequest(Monkey.external(request))
            call.respond(HttpStatusCode.OK)
        }
        post("/$REJECT_REQUEST") {
            val request = call.receive<UserId>()
            monkey.rejectRequest(Monkey.external(request))
            call.respond(HttpStatusCode.OK)
        }
        post("/$CREATE_CONVERSATION") {
            val request = call.receive<CreateConversationRequest>()
            val result =
                monkey.createConversation(request.name, request.monkeys.map(Monkey::external), request.protocol, request.isDestroyable)
            call.respond(HttpStatusCode.OK, result)
        }
        post("/$LEAVE_CONVERSATION") {
            val request = call.receive<ConversationId>()
            monkey.leaveConversation(request)
            call.respond(HttpStatusCode.OK)
        }
        post("/$DESTROY_CONVERSATION") {
            val request = call.receive<ConversationId>()
            monkey.destroyConversation(request)
            call.respond(HttpStatusCode.OK)
        }
        post("/$ADD_MONKEY_TO_CONVERSATION") {
            val request = call.receive<AddMonkeysRequest>()
            monkey.addMonkeysToConversation(request.conversationId, request.monkeys.map(Monkey::external))
            call.respond(HttpStatusCode.OK)
        }
        post("/$REMOVE_MONKEY_FROM_CONVERSATION") {
            val request = call.receive<RemoveMonkeyRequest>()
            monkey.removeMonkeyFromConversation(request.conversationId, Monkey.external(request.monkey))
            call.respond(HttpStatusCode.OK)
        }
        post("/$SEND_DM") {
            val request = call.receive<SendDMRequest>()
            monkey.sendDirectMessageTo(Monkey.external(request.monkey), request.message)
            call.respond(HttpStatusCode.OK)
        }
        post("/$SEND_MESSAGE") {
            val request = call.receive<SendMessageRequest>()
            monkey.sendMessageTo(request.conversationId, request.message)
            call.respond(HttpStatusCode.OK)
        }
    }
}

const val IS_SESSION_ACTIVE = "session-active"
const val LOGIN = "login"
const val LOGOUT = "logout"
const val WARM_UP = "warmup"
const val CONNECTED_MONKEYS = "connected-monkeys"
const val SEND_REQUEST = "request/send"
const val ACCEPT_REQUEST = "request/accept"
const val REJECT_REQUEST = "request/reject"
const val PENDING_CONNECTIONS = "request/pending"
const val CREATE_CONVERSATION = "conversation/create"
const val LEAVE_CONVERSATION = "conversation/leave"
const val DESTROY_CONVERSATION = "conversation/destroy"
const val ADD_MONKEY_TO_CONVERSATION = "conversation/add"
const val REMOVE_MONKEY_FROM_CONVERSATION = "conversation/remove"
const val SEND_DM = "message/dm"
const val SEND_MESSAGE = "message/conversation"
