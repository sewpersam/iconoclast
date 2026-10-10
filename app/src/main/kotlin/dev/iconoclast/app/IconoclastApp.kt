package dev.iconoclast.app

import android.app.Application

class IconoclastApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}

val Application.container: AppContainer get() = (this as IconoclastApp).container
