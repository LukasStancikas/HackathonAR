package com.example.lukasstancikas.hackathonar

import android.app.Application
import com.miguelbcr.ui.rx_paparazzo2.RxPaparazzo

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        RxPaparazzo.register(this)
    }
}