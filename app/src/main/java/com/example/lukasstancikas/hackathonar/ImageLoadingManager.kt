package com.example.lukasstancikas.hackathonar

import android.annotation.SuppressLint
import android.app.Activity
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.drawable.Drawable
import android.support.v7.app.AppCompatActivity
import com.miguelbcr.ui.rx_paparazzo2.RxPaparazzo
import com.miguelbcr.ui.rx_paparazzo2.entities.FileData
import com.squareup.picasso.Picasso
import com.squareup.picasso.Target
import io.reactivex.Observable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.schedulers.Schedulers
import java.lang.Exception


fun getImageFromUrlObservable(url: String?, name: String): Observable<BitmapWrapper> {
    return Observable.create {
        val target = object : Target {
            override fun onPrepareLoad(placeHolderDrawable: Drawable?) {
            }

            override fun onBitmapFailed(e: Exception?, errorDrawable: Drawable?) {
                it.onError(Throwable(e))
            }

            override fun onBitmapLoaded(bitmap: Bitmap?, from: Picasso.LoadedFrom?) {
                it.onNext(BitmapWrapper(bitmap, name))
                it.onComplete()
            }
        }

        Picasso.get().load(url).into(target)
    }
}


fun getBitmapFromFile(fileData: FileData): Observable<BitmapWrapper> {
    return Observable.create {
        val options = BitmapFactory.Options()
        options.inPreferredConfig = Bitmap.Config.ARGB_8888
        val bitmap = BitmapFactory.decodeFile(fileData.file.absolutePath, options)
        val wrapper =
            BitmapWrapper(bitmap, fileData.file.name)
        it.onNext(wrapper)
        it.onComplete()
    }

}

//we cant dispose this observable
@SuppressLint("CheckResult")
fun GalleryLoadingActivity.getImageFileFromGallery() {
    RxPaparazzo.single(this)
        .usingGallery()
        .subscribeOn(Schedulers.io())
        .observeOn(AndroidSchedulers.mainThread())
        .subscribe { response ->
            // See response.resultCode() doc
            if (response.resultCode() == AppCompatActivity.RESULT_OK) {
                response.targetUI().onGalleryImageLoaded(response.data())
            }
        }

}

abstract class GalleryLoadingActivity: AppCompatActivity() {
    abstract fun onGalleryImageLoaded(data: FileData)
}