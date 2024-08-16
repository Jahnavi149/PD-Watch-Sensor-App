package com.asu.pddata.service

import android.app.Notification
import android.app.Service
import android.content.Intent
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Environment
import android.os.Handler
import android.os.IBinder
import android.util.Log
import com.asu.pddata.constants.Constants
import java.io.File
import java.io.FileWriter
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ForegroundService : Service(), SensorEventListener {

    private var isServiceRunning = false
    private var mSensorManager: SensorManager? = null
    private var mAccSensor: Sensor? = null
    private var mGyroSensor: Sensor? = null
    private var mHeartRateSensor: Sensor? = null
    private var medicationTaken = 0

    private var accXValue: Float = 0F
    private var accYValue: Float = 0F
    private var accZValue: Float = 0F
    private var angularSpeedX: Float = 0F
    private var angularSpeedY: Float = 0F
    private var angularSpeedZ: Float = 0F
    private var heartRate: Float = 0F
    private var timestamps: MutableList<String> = arrayListOf()

    private var accXValues: MutableList<Float> = arrayListOf()
    private var accYValues: MutableList<Float> = arrayListOf()
    private var accZValues: MutableList<Float> = arrayListOf()
    private var angularSpeedXValues: MutableList<Float> = arrayListOf()
    private var angularSpeedYValues: MutableList<Float> = arrayListOf()
    private var angularSpeedZValues: MutableList<Float> = arrayListOf()
    private var heartRateValues: MutableList<Float> = arrayListOf()

    private val DATA_COLLECTION_INTERVAL = 10 // 1 second
    private val ClOUD_SYNC_INTERVAL = 10000 // 10 second,
    private val headers: List<String> = listOf("Timestamp", "Acc X", "Acc Y", "Acc Z", "Angular X",
        "Angular Y", "Angular Z", "Heart Rate", "Medication (0/1)")

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
    private val dataCollectionHandler = Handler()
    private val cloudSyncHandler = Handler()
    private var lastSynced = System.currentTimeMillis()

    override fun onBind(p0: Intent?): IBinder? {
        return null
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.getBooleanExtra("medication_taken", false) == true) {
            medicationTaken = 1
            Log.v("Collect", "Writing Medication Data to CSV")
        }

        if (!isServiceRunning) {
            isServiceRunning = true
            startForeground()
        }
        // Service will be restarted if killed by the system
        return START_STICKY
    }

    private fun startForeground() {
        val notification = Notification.Builder(this, Constants.NOTIFICATION_CHANNEL_ID)
            .setContentTitle("Service")
            .setContentText("Collecting data")
            .build()

        startForeground(1, notification)
    }

    override fun onCreate() {
        super.onCreate()

        mSensorManager = getSystemService(SENSOR_SERVICE) as SensorManager
        mAccSensor = mSensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        mGyroSensor = mSensorManager?.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
        mHeartRateSensor = mSensorManager?.getDefaultSensor(Sensor.TYPE_HEART_RATE)

        mSensorManager?.registerListener(this, mAccSensor, SensorManager.SENSOR_DELAY_NORMAL)
        mSensorManager?.registerListener(this, mGyroSensor, SensorManager.SENSOR_DELAY_NORMAL)
        mSensorManager?.registerListener(this, mHeartRateSensor, SensorManager.SENSOR_DELAY_NORMAL)

        startDataCollection()
    }

    override fun onDestroy() {
        super.onDestroy()
        isServiceRunning = false
        stopDataCollection()
        mSensorManager?.unregisterListener(this)
    }

    override fun onSensorChanged(event: SensorEvent) {
        // If sensor is unreliable, then just return
        if (event.accuracy == SensorManager.SENSOR_STATUS_UNRELIABLE) {
            return
        }
        when (event.sensor.type) {
            Sensor.TYPE_ACCELEROMETER -> {
                accXValue = event.values[0]
                accYValue = event.values[1]
                accZValue = event.values[2]
            }
            Sensor.TYPE_GYROSCOPE -> {
                angularSpeedX = event.values[0]
                angularSpeedY = event.values[1]
                angularSpeedZ = event.values[2]
            }
            Sensor.TYPE_HEART_RATE -> {
                heartRate = event.values[0]
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        //do something
    }


    private fun saveDataToCSV(headers: List<String>, data: List<List<Float>>, fileName: String): Boolean {
        if (isExternalStorageWritable()) {
            val csvFile = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS), fileName)

            try {
                Log.v("Cloud", "Saving file to $fileName")
                val fileWriter = FileWriter(csvFile)

                fileWriter.append(headers.joinToString(","))
                fileWriter.append("\n")

                if (data.isNotEmpty()) {
                    for (i in 0 until data[0].size) {
                        val row: MutableList<String> = mutableListOf()
                        row.add(timestamps[i])
                        for (sensor in data) {
                            row.add(String.format(Locale.US, "%.2f", sensor[i]))
                        }
                        row.add(medicationTaken.toString())
                        if (medicationTaken == 1) {
                            medicationTaken = 0
                        }
                        fileWriter.append(row.joinToString(","))
                        fileWriter.append("\n")
                    }
                } else {
                    Log.i("data", "List is empty")
                }

                fileWriter.close()
            } catch (e: IOException) {
                e.printStackTrace()
                return false
            }
            return true
        }
        return false
    }

    private fun isExternalStorageWritable(): Boolean {
        val state = Environment.getExternalStorageState()
        return Environment.MEDIA_MOUNTED == state
    }

    private val dataCollectionRunnable = object : Runnable {
        override fun run() {
            collectData()

            dataCollectionHandler.postDelayed(this, DATA_COLLECTION_INTERVAL.toLong())
        }
    }

    private val cloudSyncRunnable = object : Runnable {
        override fun run() {
            val data: List<List<Float>> = listOf(accXValues, accYValues, accZValues,
                angularSpeedXValues, angularSpeedYValues, angularSpeedZValues, heartRateValues)
            val currentSync = System.currentTimeMillis()
            if (saveDataToCSV(headers, data, "data-$lastSynced-$currentSync")) {
                accXValues.clear()
                accYValues.clear()
                accZValues.clear()
                angularSpeedXValues.clear()
                angularSpeedYValues.clear()
                angularSpeedZValues.clear()
                heartRateValues.clear()
                lastSynced = currentSync
            }
            cloudSyncHandler.postDelayed(this, ClOUD_SYNC_INTERVAL.toLong())
        }
    }

    private fun startDataCollection() {
        dataCollectionHandler.post(dataCollectionRunnable)
        cloudSyncHandler.post(cloudSyncRunnable)
    }

    private fun stopDataCollection() {
        dataCollectionHandler.removeCallbacks(dataCollectionRunnable)
        cloudSyncHandler.removeCallbacks(cloudSyncRunnable)
    }

    fun collectData() {
        val currentTime = dateFormat.format(Date())
        Log.v("Collect", "Collecting data at $currentTime")
        accXValues.add(accXValue)
        accYValues.add(accYValue)
        accZValues.add(accZValue)
        angularSpeedXValues.add(angularSpeedX)
        angularSpeedYValues.add(angularSpeedY)
        angularSpeedZValues.add(angularSpeedZ)
        heartRateValues.add(heartRate)
        timestamps.add(currentTime)
    }

}