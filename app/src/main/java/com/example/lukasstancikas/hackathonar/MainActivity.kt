package com.example.lukasstancikas.hackathonar

import android.Manifest
import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.drawable.Drawable
import android.os.Build
import android.support.v7.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import androidx.annotation.RequiresApi
import com.google.ar.core.*
import com.google.ar.sceneform.ux.ArFragment
import com.tbruyelle.rxpermissions2.RxPermissions
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.rxkotlin.addTo
import kotlinx.android.synthetic.main.activity_main.*
import com.google.ar.sceneform.FrameTime
import com.google.ar.sceneform.Scene
import com.squareup.picasso.Picasso
import com.squareup.picasso.Target
import io.reactivex.Observable
import java.lang.Exception
import com.google.ar.sceneform.AnchorNode
import com.google.ar.sceneform.rendering.DpToMetersViewSizer
import com.google.ar.sceneform.rendering.Renderable
import com.google.ar.sceneform.rendering.ViewRenderable
import com.jakewharton.rxbinding3.view.clicks
import com.miguelbcr.ui.rx_paparazzo2.RxPaparazzo
import com.miguelbcr.ui.rx_paparazzo2.entities.FileData
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.rxkotlin.Observables
import io.reactivex.schedulers.Schedulers
import kotlinx.android.synthetic.main.item_image.view.*


class MainActivity : AppCompatActivity(), Scene.OnUpdateListener {


    private val arFragment = ArFragment()
    private val disposable = CompositeDisposable()
    private val imageMap = hashMapOf<Int, Pair<String?, Boolean>>()
    private var listenerAdded = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
    }

    @SuppressLint("CheckResult")
    override fun onResume() {
        super.onResume()
        RxPermissions(this)
            .request(Manifest.permission.CAMERA)
            .subscribe { granted ->
                if (granted) {
                    launchArFragment()
                } else {
                    showSimpleDialog(R.string.need_permission) {
                        finish()
                    }
                }
            }
    }

    override fun onStart() {
        super.onStart()
        Observable.merge(
            mainSubmit.clicks().map { LoadType.URL },
            mainGallery.clicks().map { LoadType.GALLERY }
        )
            .subscribe({ loadType ->
                if (arFragment.isAdded) {
                    when (loadType) {
                        LoadType.URL -> getImageFromUrl()
                        LoadType.GALLERY -> getImageFromGallery()
                        else -> {}
                    }
                    getImageFromUrl()
                }
            }, Throwable::printStackTrace)
            .addTo(disposable)
    }

    override fun onStop() {
        super.onStop()
        disposable.clear()
    }

    private fun getImageFromUrl() {
        val url = mainInput.text.toString()
        val name = mainNameInput.text.toString()
        if (name.isNotEmpty() && url.isNotEmpty()) {
            getImageObservable(url, name)
                .filter {
                    it.bitmap != null
                }
                .subscribe(::addImageToDb, Throwable::printStackTrace)
                .addTo(disposable)
        }
    }

    //we cant dispose this observable
    @SuppressLint("CheckResult")
    private fun getImageFromGallery() {
        RxPaparazzo.single(this)
            .usingGallery()
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe { response ->
                // See response.resultCode() doc
                if (response.resultCode() == RESULT_OK) {
                    response.targetUI().loadGalleryImageForAr(response.data())
                }
            }

    }

    private fun loadGalleryImageForAr(data: FileData) {
        getBitmapFromFile(data)
            .filter {
                it.bitmap != null
            }
            .subscribe(::addImageToDb, Throwable::printStackTrace)
            .addTo(disposable)
    }

    private fun addImageToDb(bitmapWrapper: BitmapWrapper) {
        if (!listenerAdded) {
            setupArFragmentConfigs()
            listenerAdded = true
        }

        addImageToDbObservable(bitmapWrapper)
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .doOnSubscribe {
                mainProgress.visibility = View.VISIBLE
            }
            .doOnTerminate {
                mainProgress.visibility = View.GONE
            }
            .subscribe({
                mainNameInput.setText("")
                mainInput.setText("")
                hideKeyboard()
            }, Throwable::printStackTrace)
            .addTo(disposable)
    }

    private fun setupArFragmentConfigs() {
        arFragment.planeDiscoveryController.hide()
        arFragment.planeDiscoveryController.setInstructionView(null)
        arFragment.arSceneView.planeRenderer.isEnabled = false
        arFragment.arSceneView.scene.addOnUpdateListener(this)

    }

    private fun addImageToDbObservable(bitmapWrapper: BitmapWrapper): Observable<Int> {
        return Observable.create {
            if (arFragment.isAdded && arFragment.arSceneView.scene != null) {
                val db = AugmentedImageDatabase(arFragment.arSceneView.session)
                val index = db.addImage(bitmapWrapper.name, bitmapWrapper.bitmap)
                val config = Config(arFragment.arSceneView.session)
                config.updateMode = Config.UpdateMode.LATEST_CAMERA_IMAGE
                config.augmentedImageDatabase = db
                config.focusMode = Config.FocusMode.AUTO
                imageMap[index] = Pair(bitmapWrapper.name, false)
                arFragment.arSceneView.session.configure(config)

                it.onNext(index)
                it.onComplete()
            }
        }
    }

    private fun getBitmapFromFile(fileData: FileData): Observable<BitmapWrapper> {
        return Observable.create {
            val options = BitmapFactory.Options()
            options.inPreferredConfig = Bitmap.Config.ARGB_8888
            val bitmap = BitmapFactory.decodeFile(fileData.file.absolutePath, options)
            val wrapper = BitmapWrapper(bitmap, fileData.file.absolutePath, fileData.file.name)
            it.onNext(wrapper)
            it.onComplete()
        }

    }


    private fun launchArFragment() {
        val transaction = supportFragmentManager.beginTransaction()
        transaction.replace(R.id.mainContainer, arFragment)
        transaction.commit()
    }


    private fun getImageObservable(url: String?, name: String): Observable<BitmapWrapper> {
        return Observable.create {
            val target = object : Target {
                override fun onPrepareLoad(placeHolderDrawable: Drawable?) {
                }

                override fun onBitmapFailed(e: Exception?, errorDrawable: Drawable?) {
                    it.onError(Throwable(e))
                }

                override fun onBitmapLoaded(bitmap: Bitmap?, from: Picasso.LoadedFrom?) {
                    it.onNext(BitmapWrapper(bitmap, url, name))
                    it.onComplete()
                }
            }

            Picasso.get().load(url).into(target)
        }
    }

    override fun onUpdate(frameTime: FrameTime?) {
        frameTime?.let { arFragment.arSceneView.arFrame }?.let { frame ->
            val updatedAugmentedImages = frame.getUpdatedTrackables(AugmentedImage::class.java)
            Log.e("AUGMENTED IMAGE SIZE", updatedAugmentedImages.size.toString())
            for (img in updatedAugmentedImages) {

                if (img.trackingState == TrackingState.TRACKING) {
                    val item = imageMap[img.index]
                    item?.takeIf { !it.second }?.let { pair ->
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                            imageMap[img.index] = Pair(pair.first, true)
                            loadViewForImage(pair)
                                .subscribe({
                                    val anchorNode = AnchorNode(img.createAnchor(img.centerPose))
                                    val transformableNode = FacingNode(arFragment.transformationSystem)
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
    private fun loadViewForImage(it: Pair<String?, Boolean>): Observable<Renderable> {
        val view = LayoutInflater.from(this).inflate(R.layout.item_image, mainRoot, false)
        view.itemImageText.text = it.first
        return Observable.create { emitter ->
            ViewRenderable.builder()
                .setView(this, view)
                .setSizer(DpToMetersViewSizer(600))
                .build()
                .thenAccept {
                    emitter.onNext(it)
                    emitter.onComplete()
                }
        }

    }

}
