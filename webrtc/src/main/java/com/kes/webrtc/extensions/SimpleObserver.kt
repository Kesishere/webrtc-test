package com.kes.webrtc.extensions

import android.util.Log
import org.webrtc.SdpObserver
import org.webrtc.SessionDescription


typealias DslOnSetFailure = (result: String?) -> Unit
typealias DslOnSetSuccess = () -> Unit
typealias DslOnCreateFailure = (result: String?) -> Unit
typealias DslOnCreateSuccess = (sessionDescription: SessionDescription?) -> Unit

class SimpleObserver(private val source: Source) : SdpObserver {
    private var onSetFailure: DslOnSetFailure? = null
    private var onSetSuccess: DslOnSetSuccess? = null
    private var onCreateFailure: DslOnCreateFailure? = null
    private val TAG = "SimpleObserver"
    private var onCreateSuccess: DslOnCreateSuccess? = null

    enum class Source(val value: String) {
        CALL_LOCAL("Call Local"),
        CALL_REMOTE("Call Remote"),
        RECEIVER_LOCAL("Receiver Local"),
        RECEIVER_REMOTE("Receiver Remote"),
        REMOTE_ANSWER("Remote Answer"),
        LOCAL_OFFER("Local Offer")
    }

    override fun onSetFailure(p0: String?) {
        onSetFailure?.invoke(p0)
        Log.w(TAG, "${source.value}====> onSetFailure  result is $p0")
    }

    fun onSetFailure(func: DslOnCreateFailure) {
        onSetFailure = func
    }

    override fun onSetSuccess() {
        onSetSuccess?.invoke()
        Log.w(TAG, "${source.value}====> onSetSuccess")
    }

    fun onSetSuccess(func: DslOnSetSuccess) {
        onSetSuccess = func
    }

    override fun onCreateSuccess(p0: SessionDescription?) {
        onCreateSuccess?.invoke(p0)
        Log.w(TAG, "${source.value}====> onCreateSuccess SessionDescription is $p0")
    }

    fun onCreateSuccess(func: DslOnCreateSuccess) {
        onCreateSuccess = func
    }

    override fun onCreateFailure(p0: String?) {
        onCreateFailure?.invoke(p0)
        Log.w(TAG, "${source.value}====> onCreateFailure result is $p0")
    }

    fun onCreateFailure(func: DslOnCreateFailure) {
        onCreateFailure = func
    }
}

inline fun sdpObserver(
    source: SimpleObserver.Source,
    observer: SimpleObserver.() -> Unit
): SimpleObserver {
    return SimpleObserver(source).apply(observer)
}