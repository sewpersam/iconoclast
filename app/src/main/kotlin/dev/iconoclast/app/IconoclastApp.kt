package dev.iconoclast.app

import android.app.Application
import android.os.StrictMode

class IconoclastApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        // Debug builds log disk, network and slow calls on the main thread, and leaks.
        if (BuildConfig.DEBUG) {
            StrictMode.setThreadPolicy(StrictMode.ThreadPolicy.Builder().detectAll().penaltyLog().build())
            StrictMode.setVmPolicy(StrictMode.VmPolicy.Builder().detectAll().penaltyLog().build())
        }
        container = AppContainer(this)
    }
}

val Application.container: AppContainer get() = (this as IconoclastApp).container
