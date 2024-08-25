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
    private val medicationDataList = mutableListOf<List<String>>()

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

    private val DATA_COLLECTION_INTERVAL = 500 // 0.5 second
    private val ClOUD_SYNC_INTERVAL = 10000 // 10 seconds
    private val headers: List<String> = listOf("Timestamp", "Acc X", "Acc Y", "Acc Z", "Angular X",
        "Angular Y", "Angular Z", "Heart Rate", "Medication (0/1)")

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
    private val dataCollectionHandler = Handler()
    private val cloudSyncHandler = Handler()
    private var lastSynced = System.currentTimeMillis()
    private var currentFileName: String = ""

    override fun onBind(p0: Intent?): IBinder? {
        return null
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.getBooleanExtra("medication_taken", false) == true) {
            val medicationData = collectCurrentData() // Collect the current data
            medicationDataList.add(medicationData) // Add it to the list of medication data
            Log.v("Collect", "Medication taken; data added to list")
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
        // Do something if needed
    }

    private fun collectCurrentData(): List<String> {
        val currentTime = dateFormat.format(Date())
        return listOf(
            currentTime,
            String.format(Locale.US, "%.2f", accXValue),
            String.format(Locale.US, "%.2f", accYValue),
            String.format(Locale.US, "%.2f", accZValue),
            String.format(Locale.US, "%.2f", angularSpeedX),
            String.format(Locale.US, "%.2f", angularSpeedY),
            String.format(Locale.US, "%.2f", angularSpeedZ),
            String.format(Locale.US, "%.2f", heartRate),
            "1" // Medication taken
        )
    }

    private fun collectAndSaveData() {
        val currentTime = dateFormat.format(Date())
        timestamps.add(currentTime)
        accXValues.add(accXValue)
        accYValues.add(accYValue)
        accZValues.add(accZValue)
        angularSpeedXValues.add(angularSpeedX)
        angularSpeedYValues.add(angularSpeedY)
        angularSpeedZValues.add(angularSpeedZ)
        heartRateValues.add(heartRate)

        Log.v("Collect", "Collecting data at $currentTime")

        // Save data at the end of the sync interval
        if (System.currentTimeMillis() - lastSynced >= ClOUD_SYNC_INTERVAL) {
            val data: List<List<Float>> = listOf(
                accXValues, accYValues, accZValues,
                angularSpeedXValues, angularSpeedYValues, angularSpeedZValues, heartRateValues
            )

            val rows = mutableListOf<List<String>>()

            // Add all normal data rows first
            for (i in 0 until data[0].size) {
                val row = listOf(
                    timestamps[i],
                    String.format(Locale.US, "%.2f", accXValues[i]),
                    String.format(Locale.US, "%.2f", accYValues[i]),
                    String.format(Locale.US, "%.2f", accZValues[i]),
                    String.format(Locale.US, "%.2f", angularSpeedXValues[i]),
                    String.format(Locale.US, "%.2f", angularSpeedYValues[i]),
                    String.format(Locale.US, "%.2f", angularSpeedZValues[i]),
                    String.format(Locale.US, "%.2f", heartRateValues[i]),
                    "0" // Medication flag is 0 for normal data
                )
                rows.add(row)
            }

            // Add all medication data rows
            rows.addAll(medicationDataList)
            medicationDataList.clear() // Clear the list after saving

            // Sort rows by timestamp
            rows.sortBy { it[0] }

            // Save the sorted rows to CSV
            if (saveRowsToCSV(headers, rows, currentFileName)) {
                // Clear the lists for the next round of data collection
                accXValues.clear()
                accYValues.clear()
                accZValues.clear()
                angularSpeedXValues.clear()
                angularSpeedYValues.clear()
                angularSpeedZValues.clear()
                heartRateValues.clear()
                timestamps.clear()

                // Prepare for the next interval
                lastSynced = System.currentTimeMillis()
                currentFileName = getFileName()
            }
        }
    }

    private fun saveRowsToCSV(headers: List<String>, rows: List<List<String>>, fileName: String): Boolean {
        if (isExternalStorageWritable()) {
            val csvFile = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS), fileName)

            try {
                Log.v("Cloud", "Saving file to $fileName")
                val fileWriter = FileWriter(csvFile, true) // Append mode

                // Write headers only if file is empty
                if (csvFile.length() == 0L) {
                    fileWriter.append(headers.joinToString(","))
                    fileWriter.append("\n")
                }

                for (row in rows) {
                    fileWriter.append(row.joinToString(","))
                    fileWriter.append("\n")
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

    private fun getFileName(): String {
        return "data-${System.currentTimeMillis()}.csv"
    }

    private val dataCollectionRunnable = object : Runnable {
        override fun run() {
            collectAndSaveData()
            dataCollectionHandler.postDelayed(this, DATA_COLLECTION_INTERVAL.toLong())
        }
    }

    private fun startDataCollection() {
        currentFileName = getFileName()
        dataCollectionHandler.post(dataCollectionRunnable)
    }

    private fun stopDataCollection() {
        dataCollectionHandler.removeCallbacks(dataCollectionRunnable)
    }
}
