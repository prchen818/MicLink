package com.miclink.ui

import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.miclink.ui.theme.MicLinkColors
import com.miclink.ui.theme.MicLinkTheme

/**
 * 全屏来电界面 - 在锁屏上显示
 */
class IncomingCallActivity : ComponentActivity() {
    
    companion object {
        const val EXTRA_CALLER_ID = "caller_id"
        const val RESULT_ACCEPTED = "accepted"
        
        // 静态回调，用于通知CallViewModel
        var onCallResponse: ((accepted: Boolean) -> Unit)? = null
        
        fun show(context: Context, callerId: String) {
            val intent = Intent(context, IncomingCallActivity::class.java).apply {
                putExtra(EXTRA_CALLER_ID, callerId)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }
            context.startActivity(intent)
        }
    }
    
    private var callerId: String = ""
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // 设置窗口标志以在锁屏上显示
        setupWindowFlags()
        
        callerId = intent.getStringExtra(EXTRA_CALLER_ID) ?: "未知来电"
        
        setContent {
            MicLinkTheme {
                IncomingCallScreen(
                    callerId = callerId,
                    onAccept = {
                        onCallResponse?.invoke(true)
                        finish()
                    },
                    onReject = {
                        onCallResponse?.invoke(false)
                        finish()
                    }
                )
            }
        }
    }
    
    @Suppress("DEPRECATION")
    private fun setupWindowFlags() {
        // 在锁屏上显示
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
            
            val keyguardManager = getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
            keyguardManager.requestDismissKeyguard(this, null)
        } else {
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD
            )
        }
        
        // 保持屏幕常亮
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }
    
    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        // 禁止返回键关闭来电界面
    }
}

@Composable
fun IncomingCallScreen(
    callerId: String,
    onAccept: () -> Unit,
    onReject: () -> Unit
) {
    // 呼吸动画
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.2f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )
    
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.6f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        MicLinkColors.Primary.copy(alpha = 0.9f),
                        MicLinkColors.PrimaryDark
                    )
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Spacer(modifier = Modifier.height(60.dp))
            
            // 顶部：来电提示
            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "来电",
                    style = MaterialTheme.typography.titleLarge,
                    color = Color.White.copy(alpha = 0.8f)
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Text(
                    text = "MicLink 语音通话",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.6f)
                )
            }
            
            // 中间：来电者信息
            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // 头像带动画
                Box(contentAlignment = Alignment.Center) {
                    // 脉冲背景
                    Surface(
                        modifier = Modifier
                            .size(160.dp)
                            .scale(pulseScale),
                        shape = CircleShape,
                        color = Color.White.copy(alpha = pulseAlpha * 0.3f)
                    ) {}
                    
                    // 内圈
                    Surface(
                        modifier = Modifier.size(140.dp),
                        shape = CircleShape,
                        color = Color.White.copy(alpha = 0.2f)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Filled.Person,
                                contentDescription = null,
                                modifier = Modifier.size(80.dp),
                                tint = Color.White
                            )
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(32.dp))
                
                // 来电者ID
                Text(
                    text = callerId,
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }
            
            // 底部：接听/拒绝按钮
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 60.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 拒绝按钮
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    FloatingActionButton(
                        onClick = onReject,
                        modifier = Modifier.size(72.dp),
                        containerColor = MicLinkColors.Error,
                        contentColor = Color.White,
                        shape = CircleShape
                    ) {
                        Icon(
                            imageVector = Icons.Filled.CallEnd,
                            contentDescription = "拒绝",
                            modifier = Modifier.size(32.dp)
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    Text(
                        text = "拒绝",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.8f)
                    )
                }
                
                // 接听按钮
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    FloatingActionButton(
                        onClick = onAccept,
                        modifier = Modifier.size(72.dp),
                        containerColor = MicLinkColors.Success,
                        contentColor = Color.White,
                        shape = CircleShape
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Call,
                            contentDescription = "接听",
                            modifier = Modifier.size(32.dp)
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    Text(
                        text = "接听",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.8f)
                    )
                }
            }
        }
    }
}
