package com.miclink.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import com.miclink.R
import com.miclink.ui.MainActivity

/**
 * MicLink统一前台服务
 * 管理在线状态和通话状态，避免后台被杀
 */
class MicLinkService : Service() {
    
    companion object {
        private const val TAG = "MicLinkService"
        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "miclink_service_channel"
        
        // Actions
        const val ACTION_GO_ONLINE = "com.miclink.action.GO_ONLINE"
        const val ACTION_START_CALL = "com.miclink.action.START_CALL"
        const val ACTION_END_CALL = "com.miclink.action.END_CALL"
        const val ACTION_GO_OFFLINE = "com.miclink.action.GO_OFFLINE"
        
        // Extras
        const val EXTRA_USER_ID = "user_id"
        const val EXTRA_PEER_ID = "peer_id"
        const val EXTRA_IS_INCOMING = "is_incoming"
        
        private var currentState = ServiceState.IDLE
        
        fun isRunning(): Boolean = currentState != ServiceState.IDLE
        
        fun isInCall(): Boolean = currentState == ServiceState.IN_CALL
        
        /**
         * 上线（连接到服务器）
         */
        fun goOnline(context: Context, userId: String) {
            val intent = Intent(context, MicLinkService::class.java).apply {
                action = ACTION_GO_ONLINE
                putExtra(EXTRA_USER_ID, userId)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }
        
        /**
         * 开始通话
         */
        fun startCall(context: Context, peerId: String, isIncoming: Boolean = false) {
            val intent = Intent(context, MicLinkService::class.java).apply {
                action = ACTION_START_CALL
                putExtra(EXTRA_PEER_ID, peerId)
                putExtra(EXTRA_IS_INCOMING, isIncoming)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }
        
        /**
         * 结束通话（回到在线状态）
         */
        fun endCall(context: Context, userId: String) {
            val intent = Intent(context, MicLinkService::class.java).apply {
                action = ACTION_END_CALL
                putExtra(EXTRA_USER_ID, userId)
            }
            context.startService(intent)
        }
        
        /**
         * 离线（断开连接）
         */
        fun goOffline(context: Context) {
            val intent = Intent(context, MicLinkService::class.java).apply {
                action = ACTION_GO_OFFLINE
            }
            context.startService(intent)
        }
    }
    
    private enum class ServiceState {
        IDLE,       // 未运行
        ONLINE,     // 在线状态（可接收来电）
        IN_CALL     // 通话中
    }
    
    private val binder = LocalBinder()
    private var wakeLock: PowerManager.WakeLock? = null
    private var currentUserId: String? = null
    private var currentPeerId: String? = null
    private var isIncomingCall: Boolean = false
    
    inner class LocalBinder : Binder() {
        fun getService(): MicLinkService = this@MicLinkService
    }
    
    override fun onBind(intent: Intent?): IBinder = binder
    
    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "MicLinkService created")
        createNotificationChannel()
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "onStartCommand: action=${intent?.action}, state=$currentState")
        
        when (intent?.action) {
            ACTION_GO_ONLINE -> {
                val userId = intent.getStringExtra(EXTRA_USER_ID)
                if (userId != null) {
                    handleGoOnline(userId)
                }
            }
            
            ACTION_START_CALL -> {
                val peerId = intent.getStringExtra(EXTRA_PEER_ID) ?: "未知"
                val isIncoming = intent.getBooleanExtra(EXTRA_IS_INCOMING, false)
                handleStartCall(peerId, isIncoming)
            }
            
            ACTION_END_CALL -> {
                val userId = intent.getStringExtra(EXTRA_USER_ID) ?: currentUserId
                if (userId != null) {
                    handleEndCall(userId)
                }
            }
            
            ACTION_GO_OFFLINE -> {
                handleGoOffline()
            }
        }
        
        return START_STICKY
    }
    
    /**
     * 处理上线
     */
    private fun handleGoOnline(userId: String) {
        if (currentState == ServiceState.IN_CALL) {
            Log.d(TAG, "Already in call, ignoring go online")
            return
        }
        
        currentUserId = userId
        currentState = ServiceState.ONLINE
        
        Log.d(TAG, "Going online: $userId")
        
        acquireWakeLock(24 * 60 * 60 * 1000L) // 24小时
        updateNotification()
    }
    
    /**
     * 处理开始通话
     */
    private fun handleStartCall(peerId: String, isIncoming: Boolean) {
        currentPeerId = peerId
        isIncomingCall = isIncoming
        currentState = ServiceState.IN_CALL
        
        Log.d(TAG, "Starting call with: $peerId (incoming=$isIncoming)")
        
        // 通话时使用较短的WakeLock
        releaseWakeLock()
        acquireWakeLock(60 * 60 * 1000L) // 1小时
        
        updateNotification()
    }
    
    /**
     * 处理结束通话（回到在线状态）
     */
    private fun handleEndCall(userId: String) {
        if (currentState != ServiceState.IN_CALL) {
            Log.d(TAG, "Not in call, ignoring end call")
            return
        }
        
        Log.d(TAG, "Ending call, returning to online state")
        
        currentPeerId = null
        isIncomingCall = false
        currentUserId = userId
        currentState = ServiceState.ONLINE
        
        // 回到在线状态，使用较长的WakeLock
        releaseWakeLock()
        acquireWakeLock(24 * 60 * 60 * 1000L) // 24小时
        
        updateNotification()
    }
    
    /**
     * 处理离线
     */
    private fun handleGoOffline() {
        Log.d(TAG, "Going offline")
        
        releaseWakeLock()
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
        
        currentState = ServiceState.IDLE
        currentUserId = null
        currentPeerId = null
        isIncomingCall = false
        
        stopSelf()
    }
    
    /**
     * 更新通知内容
     */
    private fun updateNotification() {
        val notification = when (currentState) {
            ServiceState.ONLINE -> createOnlineNotification(currentUserId ?: "未知")
            ServiceState.IN_CALL -> createCallNotification(currentPeerId ?: "未知", isIncomingCall)
            ServiceState.IDLE -> return
        }
        
        startForeground(NOTIFICATION_ID, notification)
    }
    
    /**
     * 创建通知渠道
     */
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "MicLink 服务",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "保持在线和通话状态"
                setShowBadge(false)
                enableLights(false)
                enableVibration(false)
                setSound(null, null)
            }
            
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }
    
    /**
     * 创建在线状态通知
     */
    private fun createOnlineNotification(userId: String): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("MicLink 在线")
            .setContentText("ID: $userId · 可接收来电")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setOngoing(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setContentIntent(pendingIntent)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }
    
    /**
     * 创建通话中通知
     */
    private fun createCallNotification(peerId: String, isIncoming: Boolean): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        val title = if (isIncoming) "来电中" else "通话中"
        val text = "与 $peerId 的通话"
        
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setOngoing(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setContentIntent(pendingIntent)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }
    
    /**
     * 获取WakeLock
     */
    private fun acquireWakeLock(timeout: Long) {
        if (wakeLock == null) {
            val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
            wakeLock = powerManager.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "MicLink::ServiceWakeLock"
            ).apply {
                acquire(timeout)
            }
            Log.d(TAG, "WakeLock acquired for ${timeout}ms")
        }
    }
    
    /**
     * 释放WakeLock
     */
    private fun releaseWakeLock() {
        wakeLock?.let {
            if (it.isHeld) {
                it.release()
                Log.d(TAG, "WakeLock released")
            }
        }
        wakeLock = null
    }
    
    override fun onDestroy() {
        super.onDestroy()
        releaseWakeLock()
        currentState = ServiceState.IDLE
        Log.d(TAG, "MicLinkService destroyed")
    }
}
