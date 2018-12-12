package com.example.lukasstancikas.hackathonar

import android.app.Activity
import android.content.Context
import android.content.DialogInterface
import android.support.annotation.StringRes
import android.support.v7.app.AlertDialog
import android.view.View
import android.view.inputmethod.InputMethodManager

fun Activity.hideKeyboard() {
    val imm = getSystemService(Activity.INPUT_METHOD_SERVICE) as InputMethodManager
    //Find the currently focused view, so we can grab the correct window token from it.
    var view = currentFocus;
    //If no view currently has focus, create a new one, just so we can grab a window token from it
    if (view == null) {
        view = View(this)
    }
    imm.hideSoftInputFromWindow(view.windowToken, 0);
}

fun Context.showSimpleDialog(@StringRes message: Int, onOkClick: () -> Unit) {
    AlertDialog.Builder(this)
        .setMessage(message)
        .setPositiveButton(android.R.string.ok) { dialogInterface: DialogInterface, i: Int ->
            onOkClick()
        }
        .show()
}