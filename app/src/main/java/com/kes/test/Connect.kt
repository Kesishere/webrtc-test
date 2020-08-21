package com.kes.test

import android.app.Activity
import android.content.Context
import android.media.AudioManager
import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import com.kes.test.databinding.ActivityConnectBinding
import com.kes.webrtc.WebRTCManager
import com.kes.webrtc.model.MediaViewRender
import io.socket.client.IO
import io.socket.client.Socket
import io.socket.client.Socket.EVENT_CONNECT
import io.socket.client.Socket.EVENT_DISCONNECT
import org.json.JSONException
import org.json.JSONObject
import org.webrtc.*
import org.webrtc.PeerConnection.*
import org.webrtc.PeerConnection.Observer
import java.net.URISyntaxException
import java.util.*
import kotlin.properties.Delegates

class Connect : AppCompatActivity(), WebRTCManager.WebRTCManagerListener {

    companion object {
        const val TAG = "CompleteActivity"

    }

    private lateinit var binding: ActivityConnectBinding
    private lateinit var videoCapture: VideoCapturer
    private lateinit var socket: Socket
    private lateinit var peerConnection: PeerConnection
    private lateinit var localVideoTrack: VideoTrack
    private var webRTCManager: WebRTCManager by Delegates.notNull()


    private var isInitiator = false
    private var isChannelReady = false
    private var isStarted = false
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityConnectBinding.inflate(layoutInflater)
        setContentView(binding.root)

        webRTCManager = WebRTCManager(applicationContext)

        webRTCManager.startCall("Room25", this)

        val audiomanager = getSystemService(Context.AUDIO_SERVICE) as AudioManager

        audiomanager.mode = AudioManager.MODE_IN_COMMUNICATION
        audiomanager.isSpeakerphoneOn = true

        binding.hangout.setOnClickListener { webRTCManager.hangup() }


//        PeerConnectionFactory.initialize(
//            PeerConnectionFactory.InitializationOptions.builder(
//                applicationContext
//            ).createInitializationOptions()
//        )
//        // Create video renderers.
//        val rootEglBase = create()
//        binding.localVideo.init(rootEglBase.eglBaseContext, null)
//        binding.localVideo.setZOrderMediaOverlay(true)
//        binding.localVideo.setEnableHardwareScaler(true)
//
//        binding.remoteVideo.init(rootEglBase.eglBaseContext, null)
//        binding.remoteVideo.setZOrderMediaOverlay(true)
//        binding.remoteVideo.setEnableHardwareScaler(true)
//
//        binding.btnSwitch.setOnClickListener {
//            if (videoCapture is CameraVideoCapturer) {
//                val cvc = videoCapture as CameraVideoCapturer
//
//                cvc.switchCamera(null)
//
//
//            }
//        }
//        val factory = PeerConnectionFactory.builder().createPeerConnectionFactory()
//
//
//        connectToSignallingServer()
//        initializePeerConnections(factory)
//        createVideoTrackFromCameraAndShowIt(factory, rootEglBase)
//        initializePeerConnections(factory)
//        startStreamingVideo(factory)
        // If capturing format is not specified for screencapture, use screen resolution.
    }


    private fun startStreamingVideo(factory: PeerConnectionFactory) {
        val mediaStream: MediaStream = factory.createLocalMediaStream("ARDAMS")
        mediaStream.addTrack(localVideoTrack)
        peerConnection.addStream(mediaStream)
        sendMessage("got user media")
    }

    private fun createVideoTrackFromCameraAndShowIt(
        factory: PeerConnectionFactory,
        rootEglBase: EglBase
    ) {
        videoCapture = createVideoCapturer()!!
        val videoSource = factory.createVideoSource(true)


        val helper = SurfaceTextureHelper.create("CAMERA", rootEglBase.eglBaseContext)
        videoCapture.initialize(helper, this, videoSource.capturerObserver)
        videoCapture.startCapture(
            1280, 720, 30
        )

        localVideoTrack = factory.createVideoTrack("1", videoSource)
        localVideoTrack.setEnabled(true)
        localVideoTrack.addSink(binding.localVideo)

    }


    private fun initializePeerConnections(factory: PeerConnectionFactory) {
        peerConnection = createPeerConnection(factory)!!
    }


    private fun sendMessage(message: Any) {
        socket.emit("message", message)
    }

    private fun createPeerConnection(factory: PeerConnectionFactory): PeerConnection? {
        val iceServers = ArrayList<IceServer>()
        iceServers.add(IceServer("stun:stun.l.google.com:19302"))
        val rtcConfig = RTCConfiguration(iceServers)
        val pcConstraints = MediaConstraints()
        val pcObserver: Observer = object : Observer {
            override fun onSignalingChange(signalingState: SignalingState) {
                Log.d(
                    TAG,
                    "onSignalingChange: "
                )
            }

            override fun onIceConnectionChange(iceConnectionState: IceConnectionState) {
                Log.d(
                    TAG,
                    "onIceConnectionChange: "
                )
            }

            override fun onIceConnectionReceivingChange(b: Boolean) {
                Log.d(
                    TAG,
                    "onIceConnectionReceivingChange: "
                )
            }

            override fun onIceGatheringChange(iceGatheringState: IceGatheringState) {
                Log.d(
                    TAG,
                    "onIceGatheringChange: "
                )
            }

            override fun onIceCandidate(iceCandidate: IceCandidate) {
                Log.d(
                    TAG,
                    "onIceCandidate: "
                )
                val message = JSONObject()
                try {
                    message.put("type", "candidate")
                    message.put("label", iceCandidate.sdpMLineIndex)
                    message.put("id", iceCandidate.sdpMid)
                    message.put("candidate", iceCandidate.sdp)
                    Log.d(
                        TAG,
                        "onIceCandidate: sending candidate $message"
                    )
                    sendMessage(message)
                } catch (e: JSONException) {
                    e.printStackTrace()
                }
            }

            override fun onIceCandidatesRemoved(iceCandidates: Array<IceCandidate>) {
                Log.d(
                    TAG,
                    "onIceCandidatesRemoved: "
                )
            }

            override fun onAddStream(mediaStream: MediaStream) {
                Log.d(
                    TAG,
                    "onAddStream: " + mediaStream.videoTracks.size
                )
                val remoteVideoTrack = mediaStream.videoTracks[0]
                remoteVideoTrack.setEnabled(true)
                remoteVideoTrack.addSink(binding.remoteVideo)
            }

            override fun onRemoveStream(mediaStream: MediaStream) {
                Log.d(
                    TAG,
                    "onRemoveStream: "
                )
            }

            override fun onDataChannel(dataChannel: DataChannel) {
                Log.d(
                    TAG,
                    "onDataChannel: "
                )
            }

            override fun onRenegotiationNeeded() {
                Log.d(
                    TAG,
                    "onRenegotiationNeeded: "
                )
            }

            override fun onAddTrack(p0: RtpReceiver?, p1: Array<out MediaStream>?) {
            }
        }
        return factory.createPeerConnection(rtcConfig, pcObserver)
    }


    private fun createVideoCapturer(): VideoCapturer? {
        val videoCapturer: VideoCapturer?
        videoCapturer = if (useCamera2()) {
            createCameraCapturer(Camera2Enumerator(this))
        } else {
            createCameraCapturer(Camera1Enumerator(true))
        }
        return videoCapturer
    }

    private fun createCameraCapturer(enumerator: CameraEnumerator): VideoCapturer? {
        val deviceNames = enumerator.deviceNames


        // First, try to find front facing camera
        for (deviceName in deviceNames) {
            if (enumerator.isFrontFacing(deviceName)) {
                val videoCapturer: VideoCapturer? = enumerator.createCapturer(deviceName, null)
                if (videoCapturer != null) {
                    return videoCapturer
                }
            }
        }

        // Front facing camera not found, try something else
        for (deviceName in deviceNames) {
            if (!enumerator.isFrontFacing(deviceName)) {
                val videoCapturer: VideoCapturer? = enumerator.createCapturer(deviceName, null)
                if (videoCapturer != null) {
                    return videoCapturer
                }
            }
        }
        return null
    }

    private fun useCamera2(): Boolean {
        return Camera2Enumerator.isSupported(this)
    }


    private fun maybeStart() {
        Log.d(
            TAG,
            "maybeStart: $isStarted $isChannelReady"
        )
        if (!isStarted && isChannelReady) {
            isStarted = true
            if (isInitiator) {
                doCall()
            } else doAnswer()
        }
    }


    private fun doCall() {
        val sdpMediaConstraints = MediaConstraints()
        peerConnection.createOffer(object : SimpleSdpObserver() {
            override fun onCreateSuccess(sessionDescription: SessionDescription) {
                Log.d(
                    TAG,
                    "onCreateSuccess: "
                )
                peerConnection.setLocalDescription(SimpleSdpObserver(), sessionDescription)
                val message = JSONObject()
                try {
                    message.put("type", "offer")
                    message.put("sdp", sessionDescription.description)
                    sendMessage(message)
                } catch (e: JSONException) {
                    e.printStackTrace()
                }
            }
        }, sdpMediaConstraints)
    }


    private fun doAnswer() {
        peerConnection.createAnswer(object : SimpleSdpObserver() {
            override fun onCreateSuccess(sessionDescription: SessionDescription) {
                peerConnection.setLocalDescription(SimpleSdpObserver(), sessionDescription)
                val message = JSONObject()
                try {
                    message.put("type", "answer")
                    message.put("sdp", sessionDescription.description)
                    sendMessage(message)
                } catch (e: JSONException) {
                    e.printStackTrace()
                }
            }
        }, MediaConstraints())
    }

    private fun connectToSignallingServer() {
        try {
            socket = IO.socket("https://salty-sea-26559.herokuapp.com/")
            socket.on(EVENT_CONNECT, { args ->

                socket.emit("create or join", "foo")
            }).on(
                "ipaddr",
                { args ->

                }).on("created", { args ->
                isInitiator = true
            }).on(
                "full",
                { args ->
                    Log.d(
                        TAG,
                        "connectToSignallingServer: full"
                    )
                }).on("join", { args ->
                Log.d(
                    TAG,
                    "connectToSignallingServer: join"
                )
                Log.d(
                    TAG,
                    "connectToSignallingServer: Another peer made a request to join room"
                )
                Log.d(
                    TAG,
                    "connectToSignallingServer: This peer is the initiator of room"
                )
                isChannelReady = true
            }).on("joined", { args ->
                Log.d(
                    TAG,
                    "connectToSignallingServer: joined"
                )
                isChannelReady = true
            }).on("log", { args ->
                for (arg in args) {
                    Log.d(
                        TAG,
                        "connectToSignallingServer: $arg"
                    )
                }
            }).on(
                "message",
                { args ->
                    Log.d(
                        TAG,
                        "connectToSignallingServer: got a message"
                    )
                }).on("message", { args ->
                try {
                    if (args.get(0) is String) {
                        val message = args.get(0) as String
                        if (message == "got user media") {
                            maybeStart()
                        }
                    } else {
                        val message = args.get(0) as JSONObject
                        Log.d(
                            TAG,
                            "connectToSignallingServer: got message $message"
                        )
                        if (message.getString("type") == "offer") {
                            Log.d(
                                TAG,
                                "connectToSignallingServer: received an offer $isInitiator $isStarted"
                            )
                            if (!isInitiator && !isStarted) {
                                maybeStart()
                            }
                            peerConnection.setRemoteDescription(
                                SimpleSdpObserver(),
                                SessionDescription(
                                    SessionDescription.Type.OFFER,
                                    message.getString("sdp")
                                )
                            )
                            doAnswer()
                        } else if (message.getString("type") == "answer" && isStarted) {
                            peerConnection.setRemoteDescription(
                                SimpleSdpObserver(),
                                SessionDescription(
                                    SessionDescription.Type.ANSWER,
                                    message.getString("sdp")
                                )
                            )
                        } else if (message.getString("type") == "candidate" && isStarted) {
                            Log.d(
                                TAG,
                                "connectToSignallingServer: receiving candidates"
                            )
                            val candidate = IceCandidate(
                                message.getString("id"),
                                message.getInt("label"),
                                message.getString("candidate")
                            )
                            peerConnection.addIceCandidate(candidate)
                        }
                        /*else if (message === 'bye' && isStarted) {
                        handleRemoteHangup();
                    }*/
                    }
                } catch (e: JSONException) {
                    e.printStackTrace()
                }
            }).on(
                EVENT_DISCONNECT,
                { args ->
                    Log.d(
                        TAG,
                        "connectToSignallingServer: disconnect"
                    )
                })
            socket.connect()
        } catch (e: URISyntaxException) {
            e.printStackTrace()
        }
    }

    override fun retrieveMediaViewRender(): MediaViewRender {
        return MediaViewRender(binding.localVideo, binding.remoteVideo)
    }

    override fun retrieveCallActivity(): Activity {
        return this
    }

    override fun hangup() {
        finish()
    }

}