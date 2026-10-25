package com.example.videoplayer.service

import android.app.Service
import android.content.Intent
import android.os.IBinder

class LocalTransferForegroundService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null
}
