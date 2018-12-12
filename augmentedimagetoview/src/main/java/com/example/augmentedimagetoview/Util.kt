package com.example.augmentedimagetoview

import android.app.Activity
import android.content.Context
import android.content.DialogInterface
import android.support.annotation.StringRes
import android.support.v4.app.Fragment
import android.support.v7.app.AlertDialog
import android.view.View
import android.view.inputmethod.InputMethodManager


fun Fragment.showSimpleDialog(@StringRes message: Int, onOkClick: () -> Unit) {
    context?.let {AlertDialog.Builder(it)}
        ?.setMessage(message)
        ?.setPositiveButton(android.R.string.ok) { dialogInterface: DialogInterface, i: Int ->
            onOkClick()
        }
        ?.show()
}