package com.onefin.posapp.ui.payment.components

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.view.MotionEvent
import android.view.View
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource  // ⭐ Import này
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import androidx.core.graphics.toColorInt
import com.onefin.posapp.R  // ⭐ Import R

@Composable
fun SignatureBottomSheet(
    onConfirm: (ByteArray?) -> Unit
) {
    var isVisible by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        isVisible = true
    }

    Dialog(
        onDismissRequest = { },
        properties = DialogProperties(
            dismissOnBackPress = false,
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.5f))
                .pointerInput(Unit) {
                    detectTapGestures { }
                },
            contentAlignment = Alignment.BottomCenter
        ) {
            androidx.compose.animation.AnimatedVisibility(
                visible = isVisible,
                enter = androidx.compose.animation.slideInVertically(
                    initialOffsetY = { it },
                    animationSpec = androidx.compose.animation.core.tween(300)
                ) + androidx.compose.animation.fadeIn(
                    animationSpec = androidx.compose.animation.core.tween(300)
                ),
                exit = androidx.compose.animation.slideOutVertically(
                    targetOffsetY = { it },
                    animationSpec = androidx.compose.animation.core.tween(300)
                ) + androidx.compose.animation.fadeOut(
                    animationSpec = androidx.compose.animation.core.tween(300)
                )
            ) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .fillMaxHeight(0.85f),
                    shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
                    color = androidx.compose.ui.graphics.Color.White,
                    shadowElevation = 8.dp
                ) {
                    SignatureContent(
                        onConfirm = onConfirm
                    )
                }
            }
        }
    }
}

@Composable
private fun SignatureContent(
    onConfirm: (ByteArray?) -> Unit
) {
    var signatureView by remember { mutableStateOf<SignatureView?>(null) }
    var showError by remember { mutableStateOf(false) }
    var isProcessing by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
    ) {
        // Header
        Text(
            text = stringResource(R.string.signature_title),  // ⭐ Sử dụng stringResource
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = androidx.compose.ui.graphics.Color(0xFF1976D2),
            modifier = Modifier.fillMaxWidth(),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = stringResource(R.string.signature_description),  // ⭐ Sử dụng stringResource
            fontSize = 14.sp,
            color = androidx.compose.ui.graphics.Color.Gray,
            modifier = Modifier.fillMaxWidth(),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Signature View
        AndroidView(
            factory = { context ->
                SignatureView(context).also {
                    signatureView = it
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        )

        // Error message
        if (showError) {
            Text(
                fontSize = 14.sp,
                color = androidx.compose.ui.graphics.Color.Red,
                text = stringResource(R.string.signature_error),  // ⭐ Sử dụng stringResource
                modifier = Modifier.padding(vertical = 8.dp)
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Info text
        Text(
            text = stringResource(R.string.signature_warning),  // ⭐ Sử dụng stringResource
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            color = androidx.compose.ui.graphics.Color(0xFFE65100),
            modifier = Modifier.fillMaxWidth(),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Clear button
            OutlinedButton(
                onClick = {
                    signatureView?.clear()
                    showError = false
                },
                modifier = Modifier
                    .weight(1f)
                    .height(56.dp),
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = androidx.compose.ui.graphics.Color(0xFF757575)
                )
            ) {
                Text(
                    stringResource(R.string.signature_clear),  // ⭐ Sử dụng stringResource
                    fontSize = 16.sp
                )
            }

            // Confirm button
            Button(
                onClick = {
                    val isEmpty = signatureView?.isEmpty() ?: true
                    if (isEmpty) {
                        showError = true
                    } else {
                        isProcessing = true
                        scope.launch {
                            try {
                                val bitmap = withContext(Dispatchers.Main) {
                                    signatureView?.createBitmap()
                                }

                                if (bitmap != null) {
                                    val signatureData = withContext(Dispatchers.IO) {
                                        compressBitmap(bitmap)
                                    }

                                    bitmap.recycle()

                                    withContext(Dispatchers.Main) {
                                        isProcessing = false
                                        onConfirm(signatureData)
                                    }
                                } else {
                                    withContext(Dispatchers.Main) {
                                        isProcessing = false
                                        showError = true
                                    }
                                }
                            } catch (e: Exception) {
                                withContext(Dispatchers.Main) {
                                    isProcessing = false
                                    showError = true
                                }
                            }
                        }
                    }
                },
                enabled = !isProcessing,
                modifier = Modifier
                    .weight(1f)
                    .height(56.dp),
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = androidx.compose.ui.graphics.Color(0xFF1976D2)
                )
            ) {
                if (isProcessing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = androidx.compose.ui.graphics.Color.White,
                        strokeWidth = 2.dp
                    )
                } else {
                    Text(
                        stringResource(R.string.signature_confirm),
                        fontSize = 16.sp
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}

// SignatureView class giữ nguyên - không cần thay đổi
class SignatureView(context: Context) : View(context) {

    private val paint = Paint().apply {
        color = Color.BLACK
        strokeWidth = 5f
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        isAntiAlias = true
    }

    private val path = Path()
    private val paths = mutableListOf<Path>()
    private var currentX = 0f
    private var currentY = 0f

    init {
        setBackgroundColor("#F5F5F5".toColorInt())
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        val x = event.x
        val y = event.y

        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                path.moveTo(x, y)
                currentX = x
                currentY = y
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                path.lineTo(x, y)
                currentX = x
                currentY = y
                invalidate()
                return true
            }
            MotionEvent.ACTION_UP -> {
                paths.add(Path(path))
                path.reset()
                invalidate()
                return true
            }
        }
        return false
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        paths.forEach { savedPath ->
            canvas.drawPath(savedPath, paint)
        }

        canvas.drawPath(path, paint)
    }

    fun clear() {
        path.reset()
        paths.clear()
        invalidate()
    }

    fun isEmpty(): Boolean {
        return paths.isEmpty() && path.isEmpty
    }

    fun createBitmap(): Bitmap? {
        return try {
            if (isEmpty()) {
                return null
            }

            val bitmap = Bitmap.createBitmap(
                width,
                height,
                Bitmap.Config.ARGB_8888
            )

            val canvas = Canvas(bitmap)
            canvas.drawColor(Color.WHITE)

            paths.forEach { savedPath ->
                canvas.drawPath(savedPath, paint)
            }
            bitmap
        } catch (e: Exception) {
            null
        }
    }
}

private fun compressBitmap(bitmap: Bitmap): ByteArray? {
    return try {
        val stream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
        val byteArray = stream.toByteArray()
        stream.close()
        byteArray
    } catch (e: Exception) {
        null
    }
}