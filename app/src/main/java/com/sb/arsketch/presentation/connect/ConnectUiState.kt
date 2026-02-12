package com.sb.arsketch.presentation.connect

import com.sb.arsketch.domain.model.RoomRole

data class ConnectUiState(
    val serverUrl: String = DEFAULT_SERVER_URL,
    val token: String = DEFAULT_TOKEN,
    val role: RoomRole = RoomRole.HOST,
    val error: String? = null
) {
    companion object {
        const val DEFAULT_SERVER_URL = "wss://ardrawing-xabqpgun.livekit.cloud"
        const val DEFAULT_TOKEN = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJleHAiOjE3NzE0MDAxMzYsImlkZW50aXR5IjoiYW5kcm9pZC1ob3N0IiwiaXNzIjoiQVBJb3dMNkNRdjM4M21BIiwibmFtZSI6IkFuZHJvaWQgSG9zdCIsIm5iZiI6MTc3MDc5NTMzNiwic3ViIjoiYW5kcm9pZC1ob3N0IiwidmlkZW8iOnsiY2FuUHVibGlzaCI6dHJ1ZSwiY2FuUHVibGlzaERhdGEiOnRydWUsImNhblN1YnNjcmliZSI6dHJ1ZSwicm9vbSI6ImFyc2tldGNoIiwicm9vbUpvaW4iOnRydWV9fQ.4bHkPDiq6eajimMcdgr1eo9awL7m88-IUhvKXvTF1tw"
    }
}
