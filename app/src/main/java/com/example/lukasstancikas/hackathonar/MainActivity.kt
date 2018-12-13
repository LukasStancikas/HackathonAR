package com.example.lukasstancikas.hackathonar

import android.Manifest
import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.rxkotlin.addTo
import io.reactivex.Observable
import com.jakewharton.rxbinding3.view.clicks
import com.miguelbcr.ui.rx_paparazzo2.RxPaparazzo
import com.miguelbcr.ui.rx_paparazzo2.entities.FileData
import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.android.synthetic.main.item_image_custom.view.*
import android.content.Intent
import android.net.Uri
import android.support.v4.app.Fragment
import com.example.augmentedimagetoview.CameraFragment
import com.tbruyelle.rxpermissions2.RxPermissions


class MainActivity : GalleryLoadingActivity() {


    private val disposable = CompositeDisposable()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        RxPaparazzo.register(application)

        setContentView(R.layout.activity_main)
    }


    override fun onStart() {
        super.onStart()
        Observable.merge(
                mainSubmit.clicks().map { LoadType.URL },
                mainGallery.clicks().map { LoadType.GALLERY }
        )
                .subscribe({ loadType ->
                    when (loadType) {
                        LoadType.URL -> getImageFromUrl()
                        LoadType.GALLERY -> getImageFileFromGallery()
                        else -> {
                        }
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
            getImageFromUrlObservable(url, name)
                    .filter {
                        it.bitmap != null
                    }
                    .doOnSubscribe {
                        mainProgress.visibility = View.VISIBLE
                    }
                    .doOnTerminate {
                        mainProgress.visibility = View.GONE
                    }
                    .subscribe(::onImageReady, Throwable::printStackTrace)
                    .addTo(disposable)
        }
    }


    override fun onGalleryImageLoaded(data: FileData) {
        getBitmapFromFile(data)
                .subscribe(::onImageReady, Throwable::printStackTrace)
                .addTo(disposable)
    }

    @SuppressLint("CheckResult")
    private fun onImageReady(bitmapWrapper: BitmapWrapper) {
        mainNameInput.setText("")
        mainInput.setText("")
        hideKeyboard()
        RxPermissions(this)
                .request(Manifest.permission.CAMERA)
                .subscribe({
                    if (it) {
                        launchArFragment(bitmapWrapper.bitmap!!, getViewForBitmap(bitmapWrapper))
                    }
                }, Throwable::printStackTrace)
    }

    private fun getViewForBitmap(bitmapWrapper: BitmapWrapper): View {
        val view = LayoutInflater.from(this).inflate(R.layout.item_image_custom, null, false)
        view.itemImageText.text = bitmapWrapper.name
        view.itemImageButton.setOnClickListener {
            val url = "http://google.com"
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            startActivity(intent)
        }
        return view
    }

    private fun launchArFragment(bitmap: Bitmap, view: View) {
        val cameraFragments =   supportFragmentManager.fragments.filterIsInstance<CameraFragment>()
        if (cameraFragments.isEmpty()) {
            val cameraFragment = CameraFragment()
            val transaction = supportFragmentManager.beginTransaction()
            transaction.replace(R.id.mainContainer, cameraFragment)
            transaction.addToBackStack(null)
            transaction.commit()
            cameraFragment.addImageToDb(bitmap, view, 500)
        } else {
            cameraFragments.first().addImageToDb(bitmap, view, 500)
        }

    }

}
