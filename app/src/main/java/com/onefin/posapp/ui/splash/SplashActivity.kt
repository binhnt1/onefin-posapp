package com.onefin.posapp.ui.splash

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
        checkLoginAndNavigate()
    }

    private fun checkLoginAndNavigate() {
        lifecycleScope.launch {
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
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFFAFAFA))
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Spacer(modifier = Modifier.weight(1f))

            // Logo và nội dung chính (không có animation)
            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Image(
                    painter = painterResource(id = R.drawable.logo),
                    contentDescription = "OneFin Logo",
                    modifier = Modifier
                        .width(180.dp)
                        .padding(20.dp)
                )

                Spacer(modifier = Modifier.height(24.dp))

                Text(
                    text = "Điểm đến tài chính của bạn",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color(0xFF795548),
                    letterSpacing = 0.5.sp
                )

                Spacer(modifier = Modifier.height(32.dp))

                CircularProgressIndicator(
                    modifier = Modifier.size(32.dp),
                    color = Color(0xFF795548),
                    strokeWidth = 3.dp
                )
            }

            Spacer(modifier = Modifier.weight(1f))

            // Footer (không có animation)
            Column(
                modifier = Modifier.padding(bottom = 24.dp),
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