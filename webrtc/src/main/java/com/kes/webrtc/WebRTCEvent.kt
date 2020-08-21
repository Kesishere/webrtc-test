package com.kes.webrtc

import org.webrtc.SessionDescription

sealed class WebRTCEvent {
    object ColdStart : WebRTCEvent()
    object Connecting : WebRTCEvent()

    sealed class ParticipantEvent : WebRTCEvent() {
        object CreateOffer : ParticipantEvent()
        data class SetLocalSdp(val sdp: SessionDescription?) : ParticipantEvent()
        data class SendOfferToParticipant(val sdp: SessionDescription?) : ParticipantEvent()
        data class SetRemoteSdp(val sdp: SessionDescription?) : ParticipantEvent()
        data class SendAnswer(val sdp: SessionDescription?) : ParticipantEvent()
    }

    override fun toString(): String {
        return this.javaClass.simpleName
    }
}