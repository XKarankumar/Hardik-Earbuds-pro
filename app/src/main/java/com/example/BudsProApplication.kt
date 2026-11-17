package com.example

import android.app.Application
import com.example.audio.BudsMediaPlayerManager
import com.example.audio.FindEarbudsEngine
import com.example.audio.SpatialAudioMonitor
import com.example.bluetooth.BudsBluetoothManager
import com.example.data.local.BudsProDatabase
import com.example.data.preferences.AppPreferencesRepository
import com.example.data.repository.BudsRepository
import com.example.notifications.BudsNotificationManager

class BudsProApplication : Application() {

    lateinit var database: BudsProDatabase
        private set
    lateinit var repository: BudsRepository
        private set
    lateinit var preferencesRepository: AppPreferencesRepository
        private set
    lateinit var bluetoothManager: BudsBluetoothManager
        private set
    lateinit var mediaPlayerManager: BudsMediaPlayerManager
        private set
    lateinit var spatialAudioMonitor: SpatialAudioMonitor
        private set
    lateinit var findEarbudsEngine: FindEarbudsEngine
        private set

    override fun onCreate() {
        super.onCreate()
        database = BudsProDatabase.getInstance(this)
        repository = BudsRepository(database)
        preferencesRepository = AppPreferencesRepository(this)
        bluetoothManager = BudsBluetoothManager(this)
        mediaPlayerManager = BudsMediaPlayerManager(this)
        spatialAudioMonitor = SpatialAudioMonitor(this)
        findEarbudsEngine = FindEarbudsEngine()

        BudsNotificationManager.ensureChannels(this)
    }
}
