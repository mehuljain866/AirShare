package com.mehuljain.airshare

import android.app.Application
import com.mehuljain.airshare.service.PrinterController

class PrinterApp : Application() {
    override fun onCreate() {
        super.onCreate()
        PrinterController.attach(this)
    }
}
