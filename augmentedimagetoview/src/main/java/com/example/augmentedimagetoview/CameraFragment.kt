package com.example.augmentedimagetoview

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Build
import android.os.Bundle
import android.support.annotation.RequiresApi
import android.support.v4.app.Fragment
import android.support.v4.content.ContextCompat
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.google.ar.core.*
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.rxkotlin.addTo
import io.reactivex.Observable
import com.google.ar.sceneform.AnchorNode
import com.google.ar.sceneform.rendering.DpToMetersViewSizer
import com.google.ar.sceneform.rendering.Renderable
import com.google.ar.sceneform.rendering.ViewRenderable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.schedulers.Schedulers
import kotlinx.android.synthetic.main.fragment_camera.*
import kotlinx.android.synthetic.main.item_image.view.*


class CameraFragment : Fragment() {

    private val arFragment = MyArFragment()
    private val disposable = CompositeDisposable()
    private val imageMap = hashMapOf<Int, ViewForImage>()
    private val queue = mutableListOf<ImageInQueue>()

    private val ready
        get() = arFragment.isAdded && arFragment.arSceneView.scene != null


    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        arFragment.updateListener = newListener
        return inflater.inflate(R.layout.fragment_camera, container, false)
    }

    @SuppressLint("CheckResult")
    override fun onResume() {
        super.onResume()
        if (ContextCompat.checkSelfPermission(activity!!, Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED
        ) {
            showSimpleDialog(R.string.need_permission) {
                activity?.onBackPressed()
            }
        } else {
            launchArFragment()

        }
    }

    private fun launchArFragment() {
        childFragmentManager.beginTransaction()
                .replace(R.id.cameraContainer, arFragment)
                .commit()
    }


    override fun onStop() {
        super.onStop()
        disposable.clear()
    }


    fun addImageToDb(bitmap: Bitmap, view: View?, dpsForMeter: Int) {
        queue.add(ImageInQueue(bitmap, view, dpsForMeter, false))
        tryToAddImagesFromQueueToDb()
    }

    @Synchronized
    private fun tryToAddImagesFromQueueToDb() {
        if (ready && queue.any { !it.isAdding }) {
            cameraProgress.visibility = View.VISIBLE
            addImagesToDbObservable(queue.filter { !it.isAdding })
                    .doOnTerminate { cameraProgress.visibility = View.GONE }
                    .subscribeOn(Schedulers.io())
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe({
                        queue.removeAll(it)
                    }, Throwable::printStackTrace)
                    .addTo(disposable)
        }
    }


    private fun addImagesToDbObservable(items: List<ImageInQueue>): Observable<List<ImageInQueue>> {
        return Observable.create { emitter ->
            val db = arFragment.arSceneView.session.config.augmentedImageDatabase
            items.forEach {
                it.isAdding = true
                val index = db.addImage("image", it.bitmap)
                imageMap[index] = ViewForImage(it.view, it.dpsForMeter, false)
            }
            val config = arFragment.arSceneView.session.config
            config.updateMode = Config.UpdateMode.LATEST_CAMERA_IMAGE
            config.augmentedImageDatabase = db
            config.focusMode = Config.FocusMode.AUTO
            arFragment.arSceneView.session.configure(config)

            emitter.onNext(items)
            emitter.onComplete()
        }
    }

    private val newListener = object : MyArFragment.UpdateListener {
        override fun onFrameUpdate(frame: Frame) {
            tryToAddImagesFromQueueToDb()
            val updatedAugmentedImages = frame.getUpdatedTrackables(AugmentedImage::class.java)
            Log.e("AUGMENTED IMAGE SIZE", updatedAugmentedImages.size.toString())
            for (img in updatedAugmentedImages) {

                if (img.trackingState == TrackingState.TRACKING) {
                    val item = imageMap[img.index]
                    item?.takeIf { !it.viewAddedToScene }?.let { viewForImage ->
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                            imageMap[img.index]?.viewAddedToScene = true
                            loadViewForImage(viewForImage)
                                    .subscribe({
                                        val anchorNode = AnchorNode(img.createAnchor(img.centerPose))
                                        val transformableNode =
                                                FacingNode(arFragment.transformationSystem)
                                        transformableNode.renderable = it
                                        transformableNode.setParent(anchorNode)
                                        arFragment.arSceneView.scene.addChild(anchorNode)
                                    }, Throwable::printStackTrace)
                                    .addTo(disposable)
                        }
                    }
                }
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.N)
    private fun loadViewForImage(viewForImage: ViewForImage): Observable<Renderable> {
        val viewToAdd = if (viewForImage.view != null) {
            viewForImage.view
        } else {
            val view = LayoutInflater.from(context).inflate(R.layout.item_image, cameraContainer, false)
            view.itemImageText.text = "Detected Image"
            view
        }

        return Observable.create { emitter ->
            ViewRenderable.builder()
                    .setView(context, viewToAdd)
                    .setSizer(DpToMetersViewSizer(viewForImage.dpsForMeter))
                    .build()
                    .thenAccept {
                        emitter.onNext(it)
                        emitter.onComplete()
                    }
        }
    }


}
