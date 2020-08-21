package com.kes.webrtc.model

import org.webrtc.SurfaceViewRenderer

data class MediaViewRender(
    val localViewRender: SurfaceViewRenderer,
    val remoteViewRenderer: SurfaceViewRenderer
)
