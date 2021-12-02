package com.wire.kalium.api.websocket

import com.wire.kalium.api.notification.EventResponse
import com.wire.kalium.tools.KtxSerializer
import io.ktor.client.HttpClient
import io.ktor.client.features.websocket.webSocket
import io.ktor.client.request.parameter
import io.ktor.http.HttpMethod
import io.ktor.http.cio.websocket.Frame
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.decodeFromStream

class EventApi(private val httpClient: HttpClient) {
    /**
     * returns a flow of Events
     * @param clientId
     */
    @ExperimentalSerializationApi
    suspend fun listenToLiveEvent(clientId: String): Flow<EventResponse> = flow {
        httpClient.webSocket(
            method = HttpMethod.Get,
            path = PATH_AWAIT,
            request = { parameter(QUERY_CLIENT, clientId) }
        ) {
            while (true) {
                when (val frame = incoming.receive()) {
                    is Frame.Binary -> {
                        val event = KtxSerializer.json.decodeFromStream<EventResponse>(frame.data.inputStream())
                        emit(event)
                    }
                }
            }
        }
    }

    private companion object {
        const val PATH_AWAIT = "/await"
        const val QUERY_CLIENT = "client"
    }
}
