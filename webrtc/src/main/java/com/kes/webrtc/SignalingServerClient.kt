package com.kes.webrtc

import android.util.Log
import io.socket.client.IO
import io.socket.client.Socket
import org.json.JSONObject
import org.webrtc.IceCandidate
import org.webrtc.SessionDescription
import java.net.URISyntaxException

internal class SignalingServerClient(private val enableLogging: Boolean = false) {
    private lateinit var socket: Socket
    private lateinit var eventListener: SignalServerEvents
    private val TAG = "SignalingServerClient"

    companion object {
        private const val EVENT_CREATED = "created"
        private const val EVENT_FULL = "full"
        private const val EVENT_JOIN = "join"
        private const val EVENT_JOINED = "joined"
        private const val EVENT_LOG = "log"
        private const val EVENT_MESSAGE = "message"
        private const val EVENT_CLOSE = "close"

        private const val TYPE_SEND_OFFER = "send_offer"
        private const val TYPE_SEND_ANSWER = "send_answer"
        private const val TYPE_SEND_CANDIDATE = "send_candidate"
    }

    internal fun init(signalServerAddress: String, roomName: String, events: SignalServerEvents) {
        try {
            this.eventListener = events
            socket = IO.socket(signalServerAddress)
            attachSocketListeners(roomName)
            socket.connect()
        } catch (e: URISyntaxException) {
            e.printStackTrace()
        }
    }


    private fun attachSocketListeners(roomName: String) {
        socket
            .on(Socket.EVENT_CONNECT) {
                socket.emit("create or join", roomName)
            }
            .on(EVENT_CREATED) {
                eventListener?.onRoomCreated()
            }
            .on(EVENT_JOIN) {
                eventListener?.onParticipantConnected()
            }
            .on(EVENT_MESSAGE) { args ->
                val message = args.firstOrNull() as JSONObject
                when (message.getString("type")) {
                    TYPE_SEND_OFFER -> {
                        eventListener?.onParticipantReceiveOffer(
                            SessionDescription(
                                SessionDescription.Type.OFFER,
                                message.getString("sdp")
                            )
                        )
                    }
                    TYPE_SEND_ANSWER -> {
                        eventListener?.onRoomReceiveAnswer(
                            SessionDescription(
                                SessionDescription.Type.ANSWER,
                                message.getString("sdp")
                            )
                        )
                    }
                    TYPE_SEND_CANDIDATE -> {
                        eventListener?.onExchangeCandidate(
                            IceCandidate(
                                message.getString("id"),
                                message.getInt("label"),
                                message.getString("candidate")
                            )
                        )
                    }
                }
            }
            .on(EVENT_CLOSE) {
                eventListener?.onClose()
            }
            .on(EVENT_LOG) { args ->
                if (enableLogging) {
                    args.forEach { Log.d(TAG, "Socket client server observe $it") }
                }
            }
            .on(Socket.EVENT_DISCONNECT) {
                Log.d(TAG, "Signal socket disconnect")
            }
    }


    internal fun sendOffer(sdp: SessionDescription?) {
        with(JSONObject()) {
            put("type", TYPE_SEND_OFFER)
            put("sdp", sdp?.description)
            sendMessage(this)
        }
    }

    internal fun sendAnswer(sdp: SessionDescription?) {
        with(JSONObject()) {
            put("type", TYPE_SEND_ANSWER)
            put("sdp", sdp?.description)
        }.apply {
            sendMessage(this)
        }
    }

    internal fun sendIceCandidate(iceCandidate: IceCandidate) {
        with(JSONObject()) {
            put("type", TYPE_SEND_CANDIDATE)
            put("label", iceCandidate.sdpMLineIndex)
            put("id", iceCandidate.sdpMid)
            put("candidate", iceCandidate.sdp)
        }.apply {
            Log.d(TAG, "onIceCandidate: sending candidate $this")
            sendMessage(this)
        }
    }

    internal fun disconnect() {
        socket.emit("leave")
        socket.off()
        socket.disconnect()
    }

    /**
     * benefit of socket io to deal with socket
     */
    private fun sendMessage(message: Any) {
        socket.emit("message", message)
    }

    internal interface SignalServerEvents {
        fun onRoomCreated()
        fun onParticipantConnected()
        fun onParticipantReceiveOffer(sdp: SessionDescription)
        fun onRoomReceiveAnswer(sdp: SessionDescription)
        fun onExchangeCandidate(iceCandidate: IceCandidate)
        fun onClose()
    }
}