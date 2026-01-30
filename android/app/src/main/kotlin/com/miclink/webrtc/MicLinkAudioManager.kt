package com.miclink.webrtc

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothHeadset
import android.bluetooth.BluetoothProfile
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 音频设备信息（带名称）
 */
data class AudioDeviceInfo2(
    val type: MicLinkAudioManager.AudioDevice,
    val name: String,
    val id: Int = 0
)

/**
 * 音频设备管理器 - 管理扬声器/听筒/蓝牙耳机切换
 */
class MicLinkAudioManager(private val context: Context) {
    
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val TAG = "MicLinkAudioManager"
    
    private var savedAudioMode = 0
    private var savedIsSpeakerPhoneOn = false
    private var savedIsMicrophoneMute = false
    
    // 蓝牙相关
    private var bluetoothAdapter: BluetoothAdapter? = null
    private var bluetoothHeadset: BluetoothHeadset? = null
    private var connectedBluetoothDevice: BluetoothDevice? = null
    
    // 可用设备列表（带名称）
    private val _availableDevices = MutableStateFlow<List<AudioDeviceInfo2>>(emptyList())
    val availableDevices: StateFlow<List<AudioDeviceInfo2>> = _availableDevices.asStateFlow()
    
    // 设备变化监听器
    private var deviceChangeListener: (() -> Unit)? = null
    
    private val mainHandler = Handler(Looper.getMainLooper())
    
    enum class AudioDevice {
        EARPIECE,      // 听筒
        SPEAKER_PHONE, // 扬声器
        WIRED_HEADSET, // 有线耳机
        BLUETOOTH      // 蓝牙耳机
    }
    
    // 蓝牙状态广播接收器
    private val bluetoothReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                BluetoothHeadset.ACTION_CONNECTION_STATE_CHANGED -> {
                    val state = intent.getIntExtra(BluetoothHeadset.EXTRA_STATE, BluetoothHeadset.STATE_DISCONNECTED)
                    @Suppress("DEPRECATION")
                    val device = intent.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE)
                    
                    val deviceName = if (hasBluetoothConnectPermission()) {
                        device?.name
                    } else {
                        "Unknown"
                    }
                    Log.d(TAG, "Bluetooth headset state changed: $state, device: $deviceName")
                    
                    when (state) {
                        BluetoothHeadset.STATE_CONNECTED -> {
                            connectedBluetoothDevice = device
                            refreshAvailableDevices()
                        }
                        BluetoothHeadset.STATE_DISCONNECTED -> {
                            if (connectedBluetoothDevice == device) {
                                connectedBluetoothDevice = null
                            }
                            refreshAvailableDevices()
                        }
                    }
                }
                BluetoothDevice.ACTION_ACL_CONNECTED -> {
                    @Suppress("DEPRECATION")
                    val device = intent.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE)
                    val deviceName = if (hasBluetoothConnectPermission()) {
                        device?.name
                    } else {
                        "Unknown"
                    }
                    Log.d(TAG, "Bluetooth device connected: $deviceName")
                    // 延迟刷新，等待设备完全连接
                    mainHandler.postDelayed({
                        refreshAvailableDevices()
                    }, 500)
                }
                BluetoothDevice.ACTION_ACL_DISCONNECTED -> {
                    @Suppress("DEPRECATION")
                    val device = intent.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE)
                    val deviceName = if (hasBluetoothConnectPermission()) {
                        device?.name
                    } else {
                        "Unknown"
                    }
                    Log.d(TAG, "Bluetooth device disconnected: $deviceName")
                    if (connectedBluetoothDevice == device) {
                        connectedBluetoothDevice = null
                    }
                    refreshAvailableDevices()
                }
                AudioManager.ACTION_HEADSET_PLUG -> {
                    val state = intent.getIntExtra("state", 0)
                    Log.d(TAG, "Wired headset state: $state")
                    refreshAvailableDevices()
                }
            }
        }
    }
    
    // 音频设备回调 (Android M+)
    private val audioDeviceCallback = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
        object : AudioDeviceCallback() {
            override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>?) {
                Log.d(TAG, "Audio devices added: ${addedDevices?.map { it.productName }}")
                refreshAvailableDevices()
            }

            override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>?) {
                Log.d(TAG, "Audio devices removed: ${removedDevices?.map { it.productName }}")
                refreshAvailableDevices()
            }
        }
    } else null
    
    // 蓝牙Profile监听器
    private val bluetoothProfileListener = object : BluetoothProfile.ServiceListener {
        override fun onServiceConnected(profile: Int, proxy: BluetoothProfile?) {
            if (profile == BluetoothProfile.HEADSET) {
                bluetoothHeadset = proxy as? BluetoothHeadset
                // 检查蓝牙连接权限 (Android 12+)
                if (!hasBluetoothConnectPermission()) {
                    Log.w(TAG, "Missing BLUETOOTH_CONNECT permission")
                    refreshAvailableDevices()
                    return
                }
                val connectedDevices = bluetoothHeadset?.connectedDevices
                if (!connectedDevices.isNullOrEmpty()) {
                    connectedBluetoothDevice = connectedDevices[0]
                    Log.d(TAG, "Bluetooth headset connected: ${connectedBluetoothDevice?.name}")
                }
                refreshAvailableDevices()
            }
        }

        override fun onServiceDisconnected(profile: Int) {
            if (profile == BluetoothProfile.HEADSET) {
                bluetoothHeadset = null
                connectedBluetoothDevice = null
                refreshAvailableDevices()
            }
        }
    }

    /**
     * 初始化音频管理器
     */
    fun start() {
        Log.d(TAG, "Starting audio manager")
        
        // 保存当前音频状态
        savedAudioMode = audioManager.mode
        savedIsSpeakerPhoneOn = audioManager.isSpeakerphoneOn
        savedIsMicrophoneMute = audioManager.isMicrophoneMute
        
        // 设置为通话模式
        audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
        
        // 初始化蓝牙
        initBluetooth()
        
        // 注册设备变化监听
        registerDeviceListeners()
        
        // 刷新设备列表
        refreshAvailableDevices()
        
        // 默认使用听筒
        selectAudioDevice(AudioDevice.EARPIECE)
        
        Log.d(TAG, "Audio manager started - Mode: ${audioManager.mode}")
    }
    
    /**
     * 初始化蓝牙
     */
    private fun initBluetooth() {
        try {
            bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
            bluetoothAdapter?.getProfileProxy(context, bluetoothProfileListener, BluetoothProfile.HEADSET)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize Bluetooth", e)
        }
    }
    
    /**
     * 注册设备变化监听器
     */
    @Suppress("DEPRECATION")
    private fun registerDeviceListeners() {
        // 注册蓝牙广播接收器
        val filter = IntentFilter().apply {
            addAction(BluetoothHeadset.ACTION_CONNECTION_STATE_CHANGED)
            addAction(BluetoothDevice.ACTION_ACL_CONNECTED)
            addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED)
            addAction(AudioManager.ACTION_HEADSET_PLUG)
        }
        context.registerReceiver(bluetoothReceiver, filter)
        
        // 注册音频设备回调
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            audioDeviceCallback?.let {
                audioManager.registerAudioDeviceCallback(it, null)
            }
        }
    }
    
    /**
     * 取消注册监听器
     */
    private fun unregisterDeviceListeners() {
        try {
            context.unregisterReceiver(bluetoothReceiver)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to unregister bluetooth receiver", e)
        }
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            audioDeviceCallback?.let {
                audioManager.unregisterAudioDeviceCallback(it)
            }
        }
    }
    
    /**
     * 刷新可用设备列表
     */
    fun refreshAvailableDevices() {
        val devices = mutableListOf<AudioDeviceInfo2>()
        
        // 听筒总是可用
        devices.add(AudioDeviceInfo2(AudioDevice.EARPIECE, "听筒"))
        
        // 扬声器总是可用
        devices.add(AudioDeviceInfo2(AudioDevice.SPEAKER_PHONE, "扬声器"))
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val audioDevices = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
            
            var hasWired = false
            var hasBluetooth = false
            
            for (deviceInfo in audioDevices) {
                when (deviceInfo.type) {
                    AudioDeviceInfo.TYPE_WIRED_HEADSET,
                    AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
                    AudioDeviceInfo.TYPE_USB_HEADSET -> {
                        if (!hasWired) {
                            hasWired = true
                            val name = deviceInfo.productName?.toString()?.takeIf { it.isNotBlank() } ?: "有线耳机"
                            devices.add(AudioDeviceInfo2(AudioDevice.WIRED_HEADSET, name, deviceInfo.id))
                        }
                    }
                    AudioDeviceInfo.TYPE_BLUETOOTH_SCO,
                    AudioDeviceInfo.TYPE_BLUETOOTH_A2DP -> {
                        if (!hasBluetooth) {
                            hasBluetooth = true
                            // 优先使用已连接设备的名称
                            val name = if (hasBluetoothConnectPermission()) {
                                connectedBluetoothDevice?.name 
                                    ?: deviceInfo.productName?.toString()?.takeIf { it.isNotBlank() }
                                    ?: "蓝牙耳机"
                            } else {
                                "蓝牙耳机"
                            }
                            devices.add(AudioDeviceInfo2(AudioDevice.BLUETOOTH, name, deviceInfo.id))
                        }
                    }
                }
            }
        } else {
            // Android M以下版本使用旧API
            @Suppress("DEPRECATION")
            if (audioManager.isWiredHeadsetOn) {
                devices.add(AudioDeviceInfo2(AudioDevice.WIRED_HEADSET, "有线耳机"))
            }
            if (audioManager.isBluetoothScoAvailableOffCall) {
                val name = if (hasBluetoothConnectPermission()) {
                    connectedBluetoothDevice?.name ?: "蓝牙耳机"
                } else {
                    "蓝牙耳机"
                }
                devices.add(AudioDeviceInfo2(AudioDevice.BLUETOOTH, name))
            }
        }
        
        _availableDevices.value = devices
        deviceChangeListener?.invoke()
        
        Log.d(TAG, "Available devices: ${devices.map { "${it.type}: ${it.name}" }}")
    }
    
    /**
     * 设置设备变化监听器
     */
    fun setOnDeviceChangeListener(listener: (() -> Unit)?) {
        deviceChangeListener = listener
    }

    /**
     * 停止音频管理器，恢复之前的状态
     */
    fun stop() {
        Log.d(TAG, "Stopping audio manager")
        
        // 取消注册监听器
        unregisterDeviceListeners()
        
        // 关闭蓝牙Profile代理
        bluetoothHeadset?.let {
            bluetoothAdapter?.closeProfileProxy(BluetoothProfile.HEADSET, it)
        }
        bluetoothHeadset = null
        connectedBluetoothDevice = null
        
        // 恢复音频状态
        audioManager.mode = savedAudioMode
        audioManager.isSpeakerphoneOn = savedIsSpeakerPhoneOn
        audioManager.isMicrophoneMute = savedIsMicrophoneMute
        
        Log.d(TAG, "Audio manager stopped")
    }

    /**
     * 选择音频设备
     */
    fun selectAudioDevice(device: AudioDevice) {
        Log.d(TAG, "Selecting audio device: $device")
        
        when (device) {
            AudioDevice.EARPIECE -> {
                audioManager.isSpeakerphoneOn = false
                audioManager.isBluetoothScoOn = false
            }
            AudioDevice.SPEAKER_PHONE -> {
                audioManager.isSpeakerphoneOn = true
                audioManager.isBluetoothScoOn = false
            }
            AudioDevice.WIRED_HEADSET -> {
                // 有线耳机插入时自动使用
                audioManager.isSpeakerphoneOn = false
                audioManager.isBluetoothScoOn = false
            }
            AudioDevice.BLUETOOTH -> {
                audioManager.isSpeakerphoneOn = false
                audioManager.isBluetoothScoOn = true
                audioManager.startBluetoothSco()
            }
        }
    }

    /**
     * 切换扬声器状态
     */
    fun toggleSpeakerPhone(): Boolean {
        val newState = !audioManager.isSpeakerphoneOn
        audioManager.isSpeakerphoneOn = newState
        Log.d(TAG, "Speaker phone toggled: $newState")
        return newState
    }

    /**
     * 设置扬声器状态
     */
    fun setSpeakerPhoneOn(on: Boolean) {
        audioManager.isSpeakerphoneOn = on
        Log.d(TAG, "Speaker phone set to: $on")
    }

    /**
     * 获取当前扬声器状态
     */
    fun isSpeakerPhoneOn(): Boolean {
        return audioManager.isSpeakerphoneOn
    }

    /**
     * 设置麦克风静音
     */
    fun setMicrophoneMute(mute: Boolean) {
        audioManager.isMicrophoneMute = mute
        Log.d(TAG, "Microphone mute set to: $mute")
    }

    /**
     * 获取可用的音频设备列表（兼容旧接口）
     */
    fun getAvailableAudioDevices(): List<AudioDevice> {
        return _availableDevices.value.map { it.type }
    }
    
    /**
     * 获取可用的音频设备列表（带名称）
     */
    fun getAvailableAudioDevicesWithNames(): List<AudioDeviceInfo2> {
        return _availableDevices.value
    }

    /**
     * 检查是否连接了有线耳机
     */
    fun hasWiredHeadset(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val devices = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
            devices.any { 
                it.type == AudioDeviceInfo.TYPE_WIRED_HEADSET ||
                it.type == AudioDeviceInfo.TYPE_WIRED_HEADPHONES ||
                it.type == AudioDeviceInfo.TYPE_USB_HEADSET
            }
        } else {
            @Suppress("DEPRECATION")
            audioManager.isWiredHeadsetOn
        }
    }

    /**
     * 检查蓝牙耳机是否可用
     */
    fun hasBluetoothHeadset(): Boolean {
        return audioManager.isBluetoothScoAvailableOffCall
    }
    
    /**
     * 获取已连接的蓝牙设备名称
     */
    fun getConnectedBluetoothDeviceName(): String? {
        if (!hasBluetoothConnectPermission()) {
            Log.w(TAG, "Missing BLUETOOTH_CONNECT permission")
            return null
        }
        return connectedBluetoothDevice?.name
    }
    
    /**
     * 检查是否有蓝牙连接权限 (Android 12+)
     */
    private fun hasBluetoothConnectPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.BLUETOOTH_CONNECT
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true // Android 12 以下不需要此权限
        }
    }
}
