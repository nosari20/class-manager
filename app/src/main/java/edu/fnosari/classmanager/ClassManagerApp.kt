package edu.fnosari.classmanager

import android.app.Application
import android.content.Context
import edu.fnosari.classmanager.notifications.NotificationHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class ClassManagerApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        NotificationHelper.createChannels(this)
        CoroutineScope(Dispatchers.IO).launch { container.alarms.scheduleDailyDigest() }
    }
}

val Context.appContainer: AppContainer
    get() = (applicationContext as ClassManagerApp).container
