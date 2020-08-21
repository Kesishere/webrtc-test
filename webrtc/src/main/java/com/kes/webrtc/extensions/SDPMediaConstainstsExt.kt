package com.kes.webrtc.extensions

import org.webrtc.MediaConstraints


internal data class SDPMediaConstraintsModel(
    val audio: MediaConstraints.KeyValuePair = MediaConstraints.KeyValuePair(
        "OfferToReceiveAudio",
        "true"
    ),
    val video: MediaConstraints.KeyValuePair = MediaConstraints.KeyValuePair(
        "OfferToReceiveVideo",
        "true"
    )
)

internal fun SDPMediaConstraintsModel.toConstraints(): MediaConstraints {
    return MediaConstraints().apply {
        mandatory.add(audio)
        mandatory.add(video)
    }
}

internal fun MediaConstraints.addIceRestart() {
    mandatory.add(MediaConstraints.KeyValuePair("IceRestart", "true"))
}