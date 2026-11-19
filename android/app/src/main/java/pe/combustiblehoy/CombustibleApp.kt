package pe.combustiblehoy

import android.app.Application
import pe.combustiblehoy.data.AppDatabase
import pe.combustiblehoy.data.PriceRepository
import pe.combustiblehoy.data.SettingsStore
import pe.combustiblehoy.work.Notifications
import pe.combustiblehoy.work.SyncWorker

class CombustibleApp : Application() {
    lateinit var settings: SettingsStore
        private set
    lateinit var repository: PriceRepository
        private set

    override fun onCreate() {
        super.onCreate()
        settings = SettingsStore(this)
        repository = PriceRepository(this, AppDatabase.create(this), settings)
        Notifications.createChannel(this)
        SyncWorker.schedule(this)
    }
}

val android.content.Context.app: CombustibleApp get() = applicationContext as CombustibleApp
