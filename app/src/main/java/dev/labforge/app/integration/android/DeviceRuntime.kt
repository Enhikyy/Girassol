package dev.labforge.app.integration.android

import android.content.Context
import android.app.Application

object DeviceRuntime {
    lateinit var context: Application
        private set
    fun init(context: Context) { this.context = context.applicationContext as Application }
}
