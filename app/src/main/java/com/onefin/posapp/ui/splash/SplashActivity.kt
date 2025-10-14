package com.onefin.posapp.ui.splash

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import com.onefin.posapp.R
import com.onefin.posapp.core.services.StorageService
import com.onefin.posapp.ui.home.HomeActivity
import com.onefin.posapp.ui.login.LoginActivity
import com.onefin.posapp.ui.theme.PosAppTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class SplashActivity : ComponentActivity() {

    @Inject
    lateinit var storageService: StorageService

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            PosAppTheme {
                SplashScreen()
            }
        }

        // Kiểm tra đăng nhập và điều hướng sau 3 giây
        checkLoginAndNavigate()
    }

    private fun checkLoginAndNavigate() {
        lifecycleScope.launch {
            // Delay 3 giây
            delay(5000)

            val token = storageService.getToken()

            val intent = if (!token.isNullOrEmpty()) {
                Intent(this@SplashActivity, HomeActivity::class.java)
            } else {
                Intent(this@SplashActivity, LoginActivity::class.java)
            }

            startActivity(intent)
            finish()
        }
    }
}

@Composable
fun SplashScreen() {
    // Animation states
    val infiniteTransition = rememberInfiniteTransition(label = "infinite")

    // Logo scale animation (phóng to/thu nhỏ nhẹ)
    val logoScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "logoScale"
    )

    // Gradient animation (thay đổi màu nền nhẹ)
    val gradientOffset by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(3000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "gradient"
    )

    // Logo fade in animation
    val logoAlpha by animateFloatAsState(
        targetValue = 1f,
        animationSpec = tween(1000, easing = FastOutSlowInEasing),
        label = "logoAlpha"
    )

    // Text fade in animation (chậm hơn logo một chút)
    var textVisible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(500)
        textVisible = true
    }

    val textAlpha by animateFloatAsState(
        targetValue = if (textVisible) 1f else 0f,
        animationSpec = tween(1000, easing = FastOutSlowInEasing),
        label = "textAlpha"
    )

    // Footer fade in animation
    var footerVisible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(1000)
        footerVisible = true
    }

    val footerAlpha by animateFloatAsState(
        targetValue = if (footerVisible) 1f else 0f,
        animationSpec = tween(1000, easing = FastOutSlowInEasing),
        label = "footerAlpha"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 1f - gradientOffset * 0.1f),
                        Color(0xFFFAFAFA).copy(alpha = 1f - gradientOffset * 0.05f)
                    )
                )
            )
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Spacer(modifier = Modifier.weight(1f))

            // Logo chính với animation
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.alpha(logoAlpha)
            ) {
                Image(
                    painter = painterResource(id = R.drawable.logo),
                    contentDescription = "OneFin Logo",
                    modifier = Modifier
                        .width(180.dp)
                        .padding(20.dp)
                        .scale(logoScale)
                )

                Spacer(modifier = Modifier.height(24.dp))

                // Text slogan với fade in
                Text(
                    text = "Điểm đến tài chính của bạn",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color(0xFF795548),
                    letterSpacing = 0.5.sp,
                    modifier = Modifier.alpha(textAlpha)
                )

                Spacer(modifier = Modifier.height(32.dp))

                // Loading indicator
                CircularProgressIndicator(
                    modifier = Modifier
                        .size(32.dp)
                        .alpha(textAlpha),
                    color = Color(0xFF795548),
                    strokeWidth = 3.dp
                )
            }

            Spacer(modifier = Modifier.weight(1f))

            // Footer với fade in
            Column(
                modifier = Modifier
                    .padding(bottom = 24.dp)
                    .alpha(footerAlpha),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Contact info
                Surface(
                    modifier = Modifier.padding(horizontal = 20.dp),
                    shape = RoundedCornerShape(25.dp),
                    color = Color.White.copy(alpha = 0.8f)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Email,
                            contentDescription = "Email",
                            tint = Color(0xFF795548),
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "cskh@onefin.vn",
                            fontSize = 14.sp,
                            color = Color(0xFF795548)
                        )

                        Spacer(modifier = Modifier.width(12.dp))

                        HorizontalDivider(
                            modifier = Modifier
                                .width(1.dp)
                                .height(16.dp),
                            color = Color(0xFF795548).copy(alpha = 0.3f)
                        )

                        Spacer(modifier = Modifier.width(12.dp))

                        Icon(
                            imageVector = Icons.Default.Phone,
                            contentDescription = "Phone",
                            tint = Color(0xFF795548),
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "1900 996 688",
                            fontSize = 14.sp,
                            color = Color(0xFF795548)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // PCI DSS logo
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color.White.copy(alpha = 0.9f)
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.pci_dss),
                        contentDescription = "PCI DSS",
                        modifier = Modifier
                            .height(40.dp)
                            .padding(8.dp)
                    )
                }
            }
        }
    }
}