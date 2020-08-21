package com.kes.webrtc

import android.app.Activity
import android.content.Context
import android.util.Log
import com.kes.webrtc.extensions.SimpleObserver
import com.kes.webrtc.extensions.sdpObserver
import com.kes.webrtc.model.MediaViewRender
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.webrtc.*
import org.webrtc.PeerConnection.IceConnectionState.*
import kotlin.coroutines.CoroutineContext
import kotlin.properties.Delegates

class WebRTCManager(private val context: Context) : SignalingServerClient.SignalServerEvents,
    PeerConnection.Observer, CoroutineScope {
    private val job = Job()
    private val TAG = "WebRTCManager"
    private var signalClientClient: SignalingServerClient? = null
    private var peerConnectionClient: PeerConnectionClient? = null
    private var managerListener: WebRTCManagerListener? = null
    private var roomName: String by Delegates.notNull()
    private var webRtcEvent: WebRTCEvent = WebRTCEvent.ColdStart
        set(value) {
            Log.d(TAG, "STATE: $value")
            observeRtcEvents(value)
        }


    fun startCall(roomId: String, listener: WebRTCManagerListener){
        roomName = roomId
        managerListener = listener
        signalClientClient = SignalingServerClient()
        peerConnectionClient = PeerConnectionClient(context, managerListener?.retrieveMediaViewRender())
        webRtcEvent = WebRTCEvent.Connecting
    }

    private val defaultStunServer: PeerConnection.IceServer by lazy {
        PeerConnection.IceServer
            .builder("stun:stun.l.google.com:19302")
            .createIceServer()
    }

    private fun observeRtcEvents(rtcEvent: WebRTCEvent) {
        when (rtcEvent) {
            is WebRTCEvent.ColdStart -> Log.d(TAG, "Manager Reset Initial State")
            is WebRTCEvent.Connecting -> initialize()
            is WebRTCEvent.ParticipantEvent.CreateOffer -> createLocalOffer()
            is WebRTCEvent.ParticipantEvent.SendOfferToParticipant -> launch {
                signalClientClient?.sendOffer(
                    rtcEvent.sdp
                )
            }
            is WebRTCEvent.ParticipantEvent.SetRemoteSdp -> createRemoteAnswer(rtcEvent.sdp)
            is WebRTCEvent.ParticipantEvent.SendAnswer -> launch {
                signalClientClient?.sendAnswer(
                    rtcEvent.sdp
                )
            }
            is WebRTCEvent.ParticipantEvent.SetLocalSdp -> setLocalSdp(rtcEvent.sdp)
        }
    }

    private fun initialize() {
        launch {
            signalClientClient?.init("https://web-rtc-signaling-hint.herokuapp.com/", "Room25", this@WebRTCManager)
            peerConnectionClient?.initPeerConnectionFactory()
            peerConnectionClient?.createLocalPeer(this@WebRTCManager, listOf(defaultStunServer))
            peerConnectionClient?.setUpLocalVideoTrack(managerListener?.retrieveCallActivity()!!)
            peerConnectionClient?.setupLocalMediaStream()
        }
    }

    fun hangup() {
        launch { onClose() }
    }

    private fun createLocalOffer() {
        launch {
            peerConnectionClient?.createOffer(
                sdpObserver(SimpleObserver.Source.LOCAL_OFFER) {
                    onCreateSuccess { sdp ->
                        peerConnectionClient?.setLocalSdp(
                            SimpleObserver(SimpleObserver.Source.CALL_LOCAL),
                            sdp
                        )
                        webRtcEvent = WebRTCEvent.ParticipantEvent.SendOfferToParticipant(sdp)
                    }
                }
            )
        }
    }

    private fun createRemoteAnswer(remoteSdp: SessionDescription?) {
        launch {
            peerConnectionClient?.setRemoteSdp(
                SimpleObserver(SimpleObserver.Source.RECEIVER_REMOTE),
                remoteSdp
            )
            peerConnectionClient?.createAnswer(
                sdpObserver(SimpleObserver.Source.REMOTE_ANSWER) {
                    onCreateSuccess { sdp ->
                        peerConnectionClient?.setLocalSdp(
                            SimpleObserver(
                                SimpleObserver.Source.CALL_REMOTE
                            ),
                            sdp
                        )
                        webRtcEvent = WebRTCEvent.ParticipantEvent.SendAnswer(sdp)
                    }
                }
            )
        }
    }

    private fun setLocalSdp(sdp: SessionDescription?) {
        launch {
            peerConnectionClient?.setRemoteSdp(
                SimpleObserver(SimpleObserver.Source.CALL_REMOTE),
                sdp
            )
        }
    }

    override fun onRoomCreated() {
        Log.d(TAG, "Signaling Socket Created")
    }

    override fun onParticipantConnected() {
        launch { webRtcEvent = WebRTCEvent.ParticipantEvent.CreateOffer }
    }

    override fun onParticipantReceiveOffer(sdp: SessionDescription) {
        launch { webRtcEvent = WebRTCEvent.ParticipantEvent.SetRemoteSdp(sdp) }
    }

    override fun onRoomReceiveAnswer(sdp: SessionDescription) {
        launch { webRtcEvent = WebRTCEvent.ParticipantEvent.SetLocalSdp(sdp) }
    }

    override fun onExchangeCandidate(iceCandidate: IceCandidate) {
        launch { peerConnectionClient?.addIceCandidate(iceCandidate) }
    }

    override fun onClose() {
        job.cancel()
        signalClientClient?.disconnect()
        signalClientClient = null
        peerConnectionClient?.dispose()
        peerConnectionClient = null
        managerListener?.hangup()
        managerListener = null
    }

    override fun onIceCandidate(iceCandidate: IceCandidate) {
        launch { signalClientClient?.sendIceCandidate(iceCandidate) }
    }

    override fun onDataChannel(p0: DataChannel?) {
        Log.w(TAG, "onDataChannel")
    }

    override fun onIceConnectionReceivingChange(p0: Boolean) {
        Log.w(TAG, "onIceConnectionReceivingChange")
    }

    override fun onIceConnectionChange(state: PeerConnection.IceConnectionState?) {
        when (state) {
            NEW,
            CHECKING,
            CONNECTED,
            COMPLETED,
            DISCONNECTED,
            CLOSED -> Log.w(TAG, "onIceConnectionChange $state")
            FAILED -> launch {
                peerConnectionClient?.restartIce {
                    peerConnectionClient?.createOffer(
                        sdpObserver(SimpleObserver.Source.LOCAL_OFFER) {
                            onCreateSuccess { sdp ->
                                peerConnectionClient?.setLocalSdp(
                                    SimpleObserver(SimpleObserver.Source.CALL_LOCAL),
                                    sdp
                                )
                                webRtcEvent =
                                    WebRTCEvent.ParticipantEvent.SendOfferToParticipant(sdp)
                            }
                        }
                    )
                }
            }
        }
    }

    override fun onIceGatheringChange(p0: PeerConnection.IceGatheringState?) {
        Log.w(TAG, "onIceGatheringChange")
    }

    override fun onAddStream(mediaStream: MediaStream?) {
        Log.d(TAG, "onAddStream mediaStream size is ${mediaStream?.videoTracks?.size}")
        launch { peerConnectionClient?.setRemoteStream(mediaStream) }
    }

    override fun onSignalingChange(p0: PeerConnection.SignalingState?) {
        Log.w(TAG, "onSignalingChange")
    }

    override fun onIceCandidatesRemoved(p0: Array<out IceCandidate>?) {
        Log.w(TAG, "onIceCandidatesRemoved")
    }

    override fun onRemoveStream(p0: MediaStream?) {
        Log.w(TAG, "onRemoveStream")
    }

    override fun onRenegotiationNeeded() {
        Log.w(TAG, "onRenegotiationNeeded")
    }

    override fun onAddTrack(p0: RtpReceiver?, p1: Array<out MediaStream>?) {
        Log.w(TAG, " onAddTrack")
    }

    override val coroutineContext: CoroutineContext
        get() = job + Dispatchers.IO

    interface WebRTCManagerListener {
        fun retrieveMediaViewRender(): MediaViewRender
        fun retrieveCallActivity(): Activity
        fun hangup()
    }
}