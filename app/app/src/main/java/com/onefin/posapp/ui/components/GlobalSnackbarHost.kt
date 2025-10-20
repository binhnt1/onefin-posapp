package com.onefin.posapp.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.onefin.posapp.core.managers.SnackbarDuration
import com.onefin.posapp.core.managers.SnackbarManager
import com.onefin.posapp.core.managers.SnackbarMessage
import com.onefin.posapp.core.managers.SnackbarType
import kotlinx.coroutines.flow.collectLatest

@Composable
fun GlobalSnackbarHost(
    snackbarManager: SnackbarManager,
    modifier: Modifier = Modifier
) {
    val snackbarHostState = remember { SnackbarHostState() }
    var currentMessage by remember { mutableStateOf<SnackbarMessage?>(null) }

    LaunchedEffect(snackbarManager) {
        snackbarManager.messages.collectLatest { message ->
            currentMessage = message

            // Hiển thị Snackbar
            snackbarHostState.showSnackbar(
                message = "${message.title}\n${message.content}",
                duration = when (message.duration) {
                    SnackbarDuration.SHORT -> androidx.compose.material3.SnackbarDuration.Short
                    SnackbarDuration.LONG -> androidx.compose.material3.SnackbarDuration.Long
                    SnackbarDuration.INDEFINITE -> androidx.compose.material3.SnackbarDuration.Indefinite
                }
            )
        }
    }

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        SnackbarHost(
            hostState = snackbarHostState
        ) { data ->
            currentMessage?.let { message ->
                CustomSnackbar(message = message)
            }
        }
    }
}

/**
 * Custom Snackbar với styling theo type
 */
@Composable
fun CustomSnackbar(
    message: SnackbarMessage,
    modifier: Modifier = Modifier
) {
    val backgroundColor = when (message.type) {
        SnackbarType.INFO -> Color(0xFF2196F3)      // Blue
        SnackbarType.SUCCESS -> Color(0xFF4CAF50)   // Green
        SnackbarType.WARNING -> Color(0xFFFFA726)   // Orange
        SnackbarType.ERROR -> Color(0xFFF44336)     // Red
    }

    Surface(
        modifier = modifier
            .fillMaxWidth(),
        color = backgroundColor,
        shadowElevation = 2.dp
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            // Title
            if (message.title.isNotEmpty()) {
                Text(
                    fontSize = 16.sp,
                    color = Color.White,
                    text = message.title,
                    fontWeight = FontWeight.Bold
                )

                if (message.content.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(4.dp))
                }
            }

            // Content
            if (message.content.isNotEmpty()) {
                Text(
                    text = message.content,
                    color = Color.White,
                    fontSize = 14.sp
                )
            }

            // DateTime
            if (message.dateTime.isNotEmpty()) {
                Spacer(modifier = Modifier.height(1.dp))
                Text(
                    text = message.dateTime,
                    color = Color.White.copy(alpha = 0.8f),
                    fontSize = 12.sp
                )
            }
        }
    }
}