package com.example.augmentedimagetoview

import android.graphics.Bitmap
import android.view.View


data class ImageInQueue(val bitmap: Bitmap, val view: View?, val dpsForMeter: Int, var isAdding: Boolean)