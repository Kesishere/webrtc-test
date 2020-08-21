package com.kes.webrtc

import android.app.Activity
import android.app.Application
import android.content.Context
import android.os.Bundle
import android.util.Log
import com.kes.webrtc.extensions.*
import com.kes.webrtc.model.MediaViewRender
import org.webrtc.*
import org.webrtc.audio.JavaAudioDeviceModule

class PeerConnectionClient(private val context: Context, mediaViewRender: MediaViewRender?) {
    private val TAG = "PeerConnectionClient"
    private val rootEglBase: EglBase by lazy { EglBase.create() }
    private val mediaConstraints: MediaConstraints by lazy { SDPMediaConstraintsModel().toConstraints() }
    private var peerConnectionFactory: PeerConnectionFactory? = null
    private var peerConnection: PeerConnection? = null

    private var localViewRenderer: SurfaceViewRenderer? = mediaViewRender?.localViewRender

    private var remoteViewRenderer: SurfaceViewRenderer? = mediaViewRender?.remoteViewRenderer
    private lateinit var surfaceTextureHelper: SurfaceTextureHelper

    private var localAudioSource: AudioSource? = null

    private lateinit var videoCapture: VideoCapturer
    private lateinit var localVideoTrack: VideoTrack
    private var localVideoSource: VideoSource? = null
    private var remoteVideoTrack: VideoTrack? = null
    private var remoteAudioTrack: AudioTrack? = null

    init {
        setSurfaceViewRender()
    }

    internal fun initPeerConnectionFactory() {
        PeerConnectionFactory.initialize(
            PeerConnectionFactory.InitializationOptions.builder(context)
                .setEnableInternalTracer(true)
                .createInitializationOptions()
        )

        val options = PeerConnectionFactory.Options()
        val decoderVideoFactory = DefaultVideoDecoderFactory(rootEglBase.eglBaseContext)
        val encoderFactory = DefaultVideoEncoderFactory(rootEglBase.eglBaseContext, true, true)
        val audioDeviceModule = createJavaAudioDevice(context)

        peerConnectionFactory = PeerConnectionFactory
            .builder()
            .setOptions(options)
            .setAudioDeviceModule(audioDeviceModule)
            .setVideoDecoderFactory(decoderVideoFactory)
            .setVideoEncoderFactory(encoderFactory)
            .createPeerConnectionFactory()
    }

    internal fun createLocalPeer(
        observer: PeerConnection.Observer,
        iceServers: List<PeerConnection.IceServer>
    ) {
        val config = PeerConnection.RTCConfiguration(iceServers)
        peerConnection = peerConnectionFactory?.createPeerConnection(config, observer)
    }


    internal fun createOffer(sdpObserver: SdpObserver) {
        peerConnection?.createOffer(sdpObserver, mediaConstraints)
    }

    internal fun setLocalSdp(sdpObserver: SdpObserver, sdp: SessionDescription?) {
        peerConnection?.setLocalDescription(sdpObserver, sdp)
    }

    internal fun setRemoteSdp(sdpObserver: SdpObserver, sdp: SessionDescription?) {
        peerConnection?.setRemoteDescription(sdpObserver, sdp)
    }

    internal fun createAnswer(sdpObserver: SdpObserver) {
        peerConnection?.createAnswer(sdpObserver, mediaConstraints)
    }

    internal fun addIceCandidate(iceCandidate: IceCandidate) {
        peerConnection?.addIceCandidate(iceCandidate)
    }

    private fun setSurfaceViewRender() {
        localViewRenderer?.apply {
            init(rootEglBase.eglBaseContext, null)
            setMirror(true)
            setEnableHardwareScaler(true)
            setZOrderMediaOverlay(true)
            setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FIT)
        }

        remoteViewRenderer?.apply {
            init(rootEglBase.eglBaseContext, null)
            setMirror(true)
            setEnableHardwareScaler(true)
            setZOrderMediaOverlay(true)
            setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FIT)
        }
    }


    internal fun setUpLocalVideoTrack(retrieveCallActivity: Activity) {
        videoCapture = createVideoCapturer()!!
        localVideoSource = peerConnectionFactory?.createVideoSource(true)


        surfaceTextureHelper = SurfaceTextureHelper.create("CAMERA", rootEglBase.eglBaseContext)
        videoCapture.initialize(surfaceTextureHelper, context, localVideoSource?.capturerObserver)
        startCapture()

        localVideoTrack = peerConnectionFactory?.createVideoTrack("1", localVideoSource)!!
        localVideoTrack.setEnabled(true)
        localVideoTrack.addSink(localViewRenderer)
        registerResumedActivityListener(retrieveCallActivity)
    }

    private fun startCapture() {
        videoCapture.startCapture(
            1280, 720, 30
        )
    }

    internal fun setupLocalMediaStream() {
        val localStream = peerConnectionFactory?.createLocalMediaStream("stream")
        localAudioSource = createAudioSource(peerConnectionFactory!!)
        val localAudioTrack =
            createAudioTrack(peerConnectionFactory!!, audioSource = localAudioSource!!)
        localStream?.apply {
            addTrack(localVideoTrack)
            addTrack(localAudioTrack)
        }
        peerConnection?.addStream(localStream)
    }

    internal fun setRemoteStream(mediaStream: MediaStream?) {
        remoteVideoTrack = mediaStream?.videoTracks?.firstOrNull()!!
        remoteAudioTrack = mediaStream?.audioTracks?.firstOrNull()!!
        remoteVideoTrack?.addSink(remoteViewRenderer)
    }

    private fun createVideoCapturer(): VideoCapturer? {
        val videoCapturer: VideoCapturer?
        videoCapturer = if (useCamera2()) {
            createCameraCapturer(Camera2Enumerator(context))
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
        return Camera2Enumerator.isSupported(context)
    }

    private fun stopCapture() {
        try {
            videoCapture.stopCapture()
        } catch (e: InterruptedException) {
            e.printStackTrace()
        }
    }


    private fun registerResumedActivityListener(callActivity: Activity) {
        callActivity.application.registerActivityLifecycleCallbacks(object :
            Application.ActivityLifecycleCallbacks {
            override fun onActivityResumed(activity: Activity) {
                if (callActivity == activity) {
                    startCapture()
                }
                Log.d(TAG, "registerActivityLifecycleCallbacks Resumed")
            }

            override fun onActivityPaused(activity: Activity) {
                if (callActivity == activity) {
                    stopCapture()
                    Log.d(TAG, "registerActivityLifecycleCallbacks Paused")
                }
            }

            override fun onActivityDestroyed(activity: Activity) {
                if (callActivity == activity) {
                    activity.application.unregisterActivityLifecycleCallbacks(this)
                    Log.d(TAG, "registerActivityLifecycleCallbacks Destroyed")
                }
            }

            override fun onActivityStarted(activity: Activity) {}

            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}

            override fun onActivityStopped(activity: Activity) {}

            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}
        })
    }


    internal fun restartIce(action: (MediaConstraints) -> Unit) {
        mediaConstraints.addIceRestart()
        action(mediaConstraints)
    }


    internal fun dispose() {
        localViewRenderer?.release()
        localViewRenderer = null
        remoteViewRenderer?.release()
        remoteViewRenderer = null

        stopCapture()
        videoCapture.dispose()
        surfaceTextureHelper.dispose()
        localAudioSource?.dispose()
        localAudioSource = null
        localVideoSource?.dispose()
        localVideoSource = null
        remoteVideoTrack = null
        remoteAudioTrack = null
        peerConnection?.dispose()
        peerConnection = null
        rootEglBase.release()
        peerConnectionFactory?.dispose()
        peerConnectionFactory = null
        PeerConnectionFactory.stopInternalTracingCapture()
        PeerConnectionFactory.shutdownInternalTracer()
    }
}