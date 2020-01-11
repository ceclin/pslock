package top.ceclin.pslock

import android.app.Application
import android.os.StrictMode
import timber.log.Timber

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        if (BuildConfig.DEBUG) {
            StrictMode.setThreadPolicy(
                StrictMode.ThreadPolicy.Builder()
                    .detectAll()
                    .penaltyLog()
                    .build()
            )
            StrictMode.setVmPolicy(
                StrictMode.VmPolicy.Builder()
                    .detectAll()
                    .penaltyLog()
                    .build()
            )
        }
        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
            Thread.setDefaultUncaughtExceptionHandler { _, e ->
                Timber.e(e, "Uncaught exception")
            }
        }
    }
}