package com.asu.pddata

import android.app.Activity
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import com.asu.pddata.constants.Constants
import com.asu.pddata.databinding.ActivityMainBinding
import com.asu.pddata.service.ForegroundService

class MainActivity : Activity() {

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        createNotificationChannel()

        val serviceIntent = Intent(this, ForegroundService::class.java)
        startService(serviceIntent)

        binding.button.setOnClickListener {
            val medIntent = Intent(this, ForegroundService::class.java)
            medIntent.putExtra("medication_taken", true)
            startService(medIntent)
            Toast.makeText(this, "Medication recorded", Toast.LENGTH_SHORT).show()
        }
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(Constants.NOTIFICATION_CHANNEL_ID,
            Constants.NOTIFICATION_CHANNEL_NAME, NotificationManager.IMPORTANCE_DEFAULT).apply {
            description = Constants.NOTIFICATION_CHANNEL_DESCRIPTION
        }

        val notificationManager: NotificationManager =
            getSystemService(NotificationManager::class.java)
        notificationManager.createNotificationChannel(channel)
    }
}
