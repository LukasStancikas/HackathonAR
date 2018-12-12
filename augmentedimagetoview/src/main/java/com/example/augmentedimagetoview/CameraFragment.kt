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
    var readyListener: OnReadyListener? = null
        set(value) {
            if (arFragment.isAdded && arFragment.arSceneView.scene != null) {
                value?.onReady()
            }
        }
    private val disposable = CompositeDisposable()
    private val imageMap = hashMapOf<Int, ViewForImage>()


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
        addImageToDbObservable(bitmap, view, dpsForMeter)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .doOnSubscribe { cameraProgress.visibility = View.VISIBLE }
                .doOnTerminate { cameraProgress.visibility = View.GONE }
                .subscribe({
                }, Throwable::printStackTrace)
                .addTo(disposable)
    }


    private fun addImageToDbObservable(bitmap: Bitmap, view: View?, dpsForMeter: Int): Observable<Int> {
        return Observable.create {
            val ready = arFragment.isAdded && arFragment.arSceneView.scene != null
            if (ready) {
                val db = AugmentedImageDatabase(arFragment.arSceneView.session)
                val index = db.addImage("image", bitmap)
                val config = Config(arFragment.arSceneView.session)
                config.updateMode = Config.UpdateMode.LATEST_CAMERA_IMAGE
                config.augmentedImageDatabase = db
                config.focusMode = Config.FocusMode.AUTO
                imageMap[index] = ViewForImage(view, dpsForMeter, false)
                arFragment.arSceneView.session.configure(config)

                it.onNext(index)
                it.onComplete()
            }
        }
    }

    private val newListener = object : MyArFragment.UpdateListener {
        override fun onReady() {
            readyListener?.onReady()
        }

        override fun onFrameUpdate(frame: Frame) {
            val updatedAugmentedImages = frame.getUpdatedTrackables(AugmentedImage::class.java)
            Log.e("AUGMENTED IMAGE SIZE", updatedAugmentedImages.size.toString())
            for (img in updatedAugmentedImages) {

                if (img.trackingState == TrackingState.TRACKING) {
                    val item = imageMap[img.index]
                    item?.takeIf { !it.added }?.let { viewForImage ->
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                            imageMap[img.index]?.added = true
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

    interface OnReadyListener {
        fun onReady()
    }

}
