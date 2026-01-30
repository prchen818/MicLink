package com.miclink.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.miclink.model.CallState
import com.miclink.network.NetworkQuality
import com.miclink.ui.theme.MicLinkColors
import com.miclink.viewmodel.CallViewModel
import com.miclink.webrtc.AudioDeviceInfo2
import com.miclink.webrtc.MicLinkAudioManager

/**
 * 通话界面 - 清新简约风格
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CallScreen(
    viewModel: CallViewModel,
    onEndCall: () -> Unit
) {
    val callState by viewModel.callState.collectAsState()
    val peerUserId by viewModel.peerUserId.collectAsState()
    val isMuted by viewModel.isMuted.collectAsState()
    val isSpeakerOn by viewModel.isSpeakerOn.collectAsState()
    val connectionType by viewModel.connectionType.collectAsState()
    val callDuration by viewModel.callDuration.collectAsState()
    val networkQuality by viewModel.networkQuality.collectAsState()
    val currentAudioDevice by viewModel.currentAudioDevice.collectAsState()
    val availableDevices by viewModel.availableAudioDevices.collectAsState()
    
    var showAudioDeviceSheet by remember { mutableStateOf(false) }
    
    // 呼叫动画
    val infiniteTransition = rememberInfiniteTransition(label = "call")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.surface,
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    )
                )
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp)
                .systemBarsPadding(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // 顶部：网络质量指示器
            if (callState is CallState.Connected) {
                NetworkQualityIndicator(
                    quality = networkQuality,
                    connectionType = connectionType
                )
            } else {
                Spacer(modifier = Modifier.height(32.dp))
            }
            
            // 中间：用户信息
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.Center
            ) {
                // 头像 - 带呼叫动画
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.size(160.dp)
                ) {
                    // 呼叫波纹效果
                    if (callState is CallState.Ringing || callState is CallState.Connecting) {
                        Box(
                            modifier = Modifier
                                .size(160.dp)
                                .scale(pulseScale)
                                .clip(CircleShape)
                                .background(MicLinkColors.Primary.copy(alpha = 0.1f))
                        )
                        Box(
                            modifier = Modifier
                                .size(140.dp)
                                .scale(pulseScale * 0.95f)
                                .clip(CircleShape)
                                .background(MicLinkColors.Primary.copy(alpha = 0.15f))
                        )
                    }
                    
                    // 主头像
                    Surface(
                        modifier = Modifier.size(120.dp),
                        shape = CircleShape,
                        color = MicLinkColors.Primary.copy(alpha = 0.15f),
                        tonalElevation = 4.dp
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Filled.Person,
                                contentDescription = null,
                                modifier = Modifier.size(56.dp),
                                tint = MicLinkColors.Primary
                            )
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(24.dp))
                
                // 对方ID
                Text(
                    text = peerUserId ?: "Unknown",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                // 通话状态
                Text(
                    text = when (callState) {
                        is CallState.Ringing -> {
                            if ((callState as CallState.Ringing).isIncoming) "来电中..."
                            else "呼叫中..."
                        }
                        is CallState.Connecting -> "连接中..."
                        is CallState.Connected -> formatDuration(callDuration)
                        else -> ""
                    },
                    style = MaterialTheme.typography.titleLarge,
                    color = MicLinkColors.Primary
                )
                
                // 连接类型标签
                if (callState is CallState.Connected && connectionType != null) {
                    Spacer(modifier = Modifier.height(12.dp))
                    ConnectionTypeChip(connectionType = connectionType)
                }
            }
            
            // 底部：控制按钮
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                // 通话中的控制按钮
                if (callState is CallState.Connected) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(32.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // 静音
                        CallControlButton(
                            icon = if (isMuted) Icons.Filled.MicOff else Icons.Outlined.Mic,
                            label = if (isMuted) "取消静音" else "静音",
                            isActive = isMuted,
                            activeColor = MicLinkColors.Error,
                            onClick = { viewModel.toggleMute() }
                        )
                        
                        // 音频设备选择
                        CallControlButton(
                            icon = getAudioDeviceIcon(currentAudioDevice),
                            label = getAudioDeviceLabel(currentAudioDevice),
                            isActive = currentAudioDevice != MicLinkAudioManager.AudioDevice.EARPIECE,
                            activeColor = MicLinkColors.Primary,
                            onClick = { showAudioDeviceSheet = true }
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(8.dp))
                }
                
                // 主操作按钮
                when (callState) {
                    is CallState.Ringing -> {
                        if ((callState as CallState.Ringing).isIncoming) {
                            // 来电：接听/拒绝
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(48.dp)
                            ) {
                                // 拒绝
                                LargeActionButton(
                                    icon = Icons.Filled.Close,
                                    label = "拒绝",
                                    containerColor = MicLinkColors.Error,
                                    onClick = { viewModel.rejectCall() }
                                )
                                
                                // 接听
                                LargeActionButton(
                                    icon = Icons.Filled.Phone,
                                    label = "接听",
                                    containerColor = MicLinkColors.Success,
                                    onClick = { viewModel.acceptCall() }
                                )
                            }
                        } else {
                            // 呼出：取消
                            LargeActionButton(
                                icon = Icons.Filled.CallEnd,
                                label = "取消",
                                containerColor = MicLinkColors.Error,
                                onClick = onEndCall
                            )
                        }
                    }
                    
                    is CallState.Connecting,
                    is CallState.Connected -> {
                        LargeActionButton(
                            icon = Icons.Filled.CallEnd,
                            label = "挂断",
                            containerColor = MicLinkColors.Error,
                            onClick = onEndCall
                        )
                    }
                    
                    else -> {}
                }
                
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
    
    // 音频设备选择底部弹窗
    if (showAudioDeviceSheet) {
        AudioDeviceBottomSheet(
            currentDevice = currentAudioDevice,
            availableDevices = availableDevices,
            onDeviceSelected = { deviceInfo ->
                viewModel.selectAudioDevice(deviceInfo.type)
                showAudioDeviceSheet = false
            },
            onDismiss = { showAudioDeviceSheet = false }
        )
    }
}

@Composable
fun NetworkQualityIndicator(
    quality: NetworkQuality,
    connectionType: String?
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 连接类型
        if (connectionType != null) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceVariant
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = if (connectionType == "p2p") Icons.Outlined.Wifi else Icons.Outlined.Cloud,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = if (connectionType == "p2p") "直连" else "中转",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        
        // 网络质量
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surfaceVariant
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // 信号条
                val (color, bars) = when (quality) {
                    NetworkQuality.EXCELLENT -> MicLinkColors.Success to 4
                    NetworkQuality.GOOD -> MicLinkColors.Success to 3
                    NetworkQuality.FAIR -> MicLinkColors.Warning to 2
                    NetworkQuality.POOR -> MicLinkColors.Error to 1
                    NetworkQuality.UNKNOWN -> MaterialTheme.colorScheme.onSurfaceVariant to 0
                }
                
                repeat(4) { index ->
                    Box(
                        modifier = Modifier
                            .width(4.dp)
                            .height((8 + index * 4).dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(
                                if (index < bars) color else color.copy(alpha = 0.2f)
                            )
                    )
                }
            }
        }
    }
}

@Composable
fun ConnectionTypeChip(connectionType: String?) {
    val (icon, text, color) = when (connectionType) {
        "p2p" -> Triple(Icons.Outlined.Wifi, "P2P直连", MicLinkColors.Success)
        "relay" -> Triple(Icons.Outlined.Cloud, "服务器中转", MicLinkColors.Secondary)
        else -> Triple(Icons.Outlined.HourglassEmpty, "连接中", MaterialTheme.colorScheme.onSurfaceVariant)
    }
    
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = color.copy(alpha = 0.1f)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = color
            )
            Text(
                text = text,
                style = MaterialTheme.typography.labelMedium,
                color = color
            )
        }
    }
}

@Composable
fun CallControlButton(
    icon: ImageVector,
    label: String,
    isActive: Boolean = false,
    activeColor: Color = MicLinkColors.Primary,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Surface(
            modifier = Modifier.size(60.dp),
            shape = CircleShape,
            color = if (isActive) activeColor.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surfaceVariant,
            onClick = onClick
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = icon,
                    contentDescription = label,
                    modifier = Modifier.size(26.dp),
                    tint = if (isActive) activeColor else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun LargeActionButton(
    icon: ImageVector,
    label: String,
    containerColor: Color,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        FloatingActionButton(
            onClick = onClick,
            containerColor = containerColor,
            contentColor = Color.White,
            modifier = Modifier.size(72.dp),
            shape = CircleShape
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                modifier = Modifier.size(32.dp)
            )
        }
        
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AudioDeviceBottomSheet(
    currentDevice: MicLinkAudioManager.AudioDevice,
    availableDevices: List<AudioDeviceInfo2>,
    onDeviceSelected: (AudioDeviceInfo2) -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp)
        ) {
            Text(
                text = "选择音频输出",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 20.dp)
            )
            
            availableDevices.forEach { deviceInfo ->
                val isSelected = deviceInfo.type == currentDevice
                
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    shape = RoundedCornerShape(12.dp),
                    color = if (isSelected) MicLinkColors.Primary.copy(alpha = 0.1f) 
                           else Color.Transparent,
                    onClick = { onDeviceSelected(deviceInfo) }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Icon(
                            imageVector = getAudioDeviceIcon(deviceInfo.type),
                            contentDescription = null,
                            modifier = Modifier.size(24.dp),
                            tint = if (isSelected) MicLinkColors.Primary 
                                   else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        
                        Text(
                            text = deviceInfo.name,  // 使用实际设备名称
                            style = MaterialTheme.typography.bodyLarge,
                            color = if (isSelected) MicLinkColors.Primary 
                                    else MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f)
                        )
                        
                        if (isSelected) {
                            Icon(
                                imageVector = Icons.Filled.Check,
                                contentDescription = null,
                                tint = MicLinkColors.Primary,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

fun getAudioDeviceIcon(device: MicLinkAudioManager.AudioDevice): ImageVector {
    return when (device) {
        MicLinkAudioManager.AudioDevice.EARPIECE -> Icons.Outlined.PhoneInTalk
        MicLinkAudioManager.AudioDevice.SPEAKER_PHONE -> Icons.Outlined.VolumeUp
        MicLinkAudioManager.AudioDevice.WIRED_HEADSET -> Icons.Outlined.Headphones
        MicLinkAudioManager.AudioDevice.BLUETOOTH -> Icons.Outlined.Bluetooth
    }
}

fun getAudioDeviceLabel(device: MicLinkAudioManager.AudioDevice): String {
    return when (device) {
        MicLinkAudioManager.AudioDevice.EARPIECE -> "听筒"
        MicLinkAudioManager.AudioDevice.SPEAKER_PHONE -> "扬声器"
        MicLinkAudioManager.AudioDevice.WIRED_HEADSET -> "耳机"
        MicLinkAudioManager.AudioDevice.BLUETOOTH -> "蓝牙"
    }
}

fun getAudioDeviceFullLabel(device: MicLinkAudioManager.AudioDevice): String {
    return when (device) {
        MicLinkAudioManager.AudioDevice.EARPIECE -> "听筒"
        MicLinkAudioManager.AudioDevice.SPEAKER_PHONE -> "扬声器"
        MicLinkAudioManager.AudioDevice.WIRED_HEADSET -> "有线耳机"
        MicLinkAudioManager.AudioDevice.BLUETOOTH -> "蓝牙耳机"
    }
}

/**
 * 格式化通话时长
 */
fun formatDuration(seconds: Int): String {
    val hours = seconds / 3600
    val minutes = (seconds % 3600) / 60
    val secs = seconds % 60
    
    return if (hours > 0) {
        String.format("%02d:%02d:%02d", hours, minutes, secs)
    } else {
        String.format("%02d:%02d", minutes, secs)
    }
}
