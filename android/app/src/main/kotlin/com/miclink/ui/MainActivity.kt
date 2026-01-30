package com.miclink.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.miclink.model.CallState
import com.miclink.ui.screens.CallScreen
import com.miclink.ui.screens.HomeScreen
import com.miclink.ui.theme.MicLinkTheme
import com.miclink.viewmodel.CallViewModel
import com.miclink.viewmodel.HomeViewModel

/**
 * 主Activity
 */
class MainActivity : ComponentActivity() {
    
    private val requiredPermissions: Array<String>
        get() {
            val permissions = mutableListOf(
                Manifest.permission.RECORD_AUDIO,
                Manifest.permission.MODIFY_AUDIO_SETTINGS
            )
            // Android 12+ 需要蓝牙连接权限
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                permissions.add(Manifest.permission.BLUETOOTH_CONNECT)
            }
            return permissions.toTypedArray()
        }
    
    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.values.all { it }
        if (!allGranted) {
            Toast.makeText(
                this,
                "需要麦克风权限才能进行语音通话",
                Toast.LENGTH_LONG
            ).show()
            finish()
        }
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // 检查并请求权限
        checkAndRequestPermissions()
        
        setContent {
            MicLinkTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    MicLinkApp()
                }
            }
        }
    }
    
    private fun checkAndRequestPermissions() {
        val permissionsToRequest = requiredPermissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        
        if (permissionsToRequest.isNotEmpty()) {
            permissionLauncher.launch(permissionsToRequest.toTypedArray())
        }
    }
}

@Composable
fun MicLinkApp() {
    val homeViewModel: HomeViewModel = viewModel()
    val callViewModel: CallViewModel = viewModel()
    
    val callState by callViewModel.callState.collectAsState()
    val currentUserId by homeViewModel.currentUserId.collectAsState()
    
    // 共享SignalingRepository
    LaunchedEffect(Unit) {
        callViewModel.setSignalingRepository(homeViewModel.getSignalingRepository())
    }
    
    // 同步设置到CallViewModel
    LaunchedEffect(currentUserId) {
        currentUserId?.let { callViewModel.setUserId(it) }
    }
    
    val connectionMode by homeViewModel.connectionMode.collectAsState()
    val audioQuality by homeViewModel.audioQuality.collectAsState()
    
    LaunchedEffect(connectionMode, audioQuality) {
        callViewModel.setCallSettings(connectionMode, audioQuality)
    }
    
    // 根据通话状态显示不同界面
    when (callState) {
        is CallState.Idle -> {
            HomeScreen(
                viewModel = homeViewModel,
                onCallUser = { targetId ->
                    callViewModel.initiateCall(targetId)
                }
            )
        }
        
        else -> {
            CallScreen(
                viewModel = callViewModel,
                onEndCall = {
                    callViewModel.hangup()
                }
            )
        }
    }
}
