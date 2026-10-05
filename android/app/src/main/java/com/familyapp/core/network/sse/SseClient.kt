package com.familyapp.core.network.sse

import com.familyapp.core.network.NetworkClient
import com.google.gson.Gson
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import okhttp3.Request
import okhttp3.Response
import okhttp3.sse.EventSource
import okhttp3.sse.EventSourceListener
import okhttp3.sse.EventSources

data class SseEvent(
    val type: String,
    val data: String
)

class SseClient {
    private val gson = Gson()

    fun listenToEvents(token: String?): Flow<SseEvent> = callbackFlow {
        val streamUrl = buildString {
            append(NetworkClient.baseUrl.removeSuffix("/"))
            append("/api/events/stream")
            if (!token.isNullOrBlank()) {
                append("?token=").append(token)
            }
        }

        val request = Request.Builder()
            .url(streamUrl)
            .addHeader("Accept", "text/event-stream")
            .build()

        val factory = EventSources.createFactory(NetworkClient.sseOkHttpClient)
        val listener = object : EventSourceListener() {
            override fun onOpen(eventSource: EventSource, response: Response) {
                android.util.Log.d("SseClient", "SSE stream opened successfully")
                trySend(SseEvent(type = "connected", data = "{}"))
            }

            override fun onEvent(eventSource: EventSource, id: String?, type: String?, data: String) {
                android.util.Log.d("SseClient", "SSE event received -> type: $type | data: $data")
                trySend(SseEvent(type = type ?: "message", data = data))
            }

            override fun onFailure(eventSource: EventSource, t: Throwable?, response: Response?) {
                android.util.Log.w("SseClient", "SSE failure -> code: ${response?.code}, error: ${t?.message}")
                if (response?.code in listOf(401, 403, 404)) {
                    channel.close()
                }
            }

            override fun onClosed(eventSource: EventSource) {
                android.util.Log.d("SseClient", "SSE stream closed")
                channel.close()
            }
        }

        val eventSource = factory.newEventSource(request, listener)

        awaitClose {
            eventSource.cancel()
        }
    }
}
