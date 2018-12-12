package com.example.augmentedimagetoview

import com.google.ar.sceneform.FrameTime
import com.google.ar.sceneform.math.Quaternion
import com.google.ar.sceneform.math.Vector3
import com.google.ar.sceneform.ux.TransformableNode
import com.google.ar.sceneform.ux.TransformationSystem

class FacingNode(system: TransformationSystem): TransformableNode(system) {
    override fun onUpdate(frameTime: FrameTime?) {
            // Typically, getScene() will never return null because onUpdate() is only called when the node
            // is in the scene.
            // However, if onUpdate is called explicitly or if the node is removed from the scene on a
            // different thread during onUpdate, then getScene may be null.
            if (scene == null) {
                return
            }
            val cameraPosition = scene.camera.worldPosition
            val cardPosition = worldPosition
            val direction = Vector3.subtract(cameraPosition, cardPosition)
            val lookRotation = Quaternion.lookRotation(direction, Vector3.up())
            worldRotation = lookRotation


    }
}