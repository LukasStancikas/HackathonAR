package com.example.augmentedimagetoview

import android.os.Bundle
import android.view.View
import com.google.ar.core.Frame
import com.google.ar.sceneform.FrameTime
import com.google.ar.sceneform.ux.ArFragment

class MyArFragment : ArFragment() {

    var updateListener: UpdateListener? = null
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        planeDiscoveryController.hide()
        planeDiscoveryController.setInstructionView(null)
        arSceneView.planeRenderer.isEnabled = false
    }

    override fun onUpdate(frameTime: FrameTime?) {
        super.onUpdate(frameTime)
        frameTime?.let { arSceneView.arFrame }?.let {
            updateListener?.onFrameUpdate(it)
        }
    }

    interface UpdateListener {
        fun onFrameUpdate(frame: Frame)
    }
}