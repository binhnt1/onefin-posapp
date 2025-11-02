package com.onefin.posapp.core.managers

import com.onefin.posapp.core.models.data.RabbitNotify
import com.onefin.posapp.core.utils.DateTimeFormatter
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import javax.inject.Inject
import javax.inject.Singleton

data class SnackbarMessage(
    val title: String,
    val content: String,
    val dateTime: String = "",
    val type: SnackbarType = SnackbarType.INFO,
    val duration: SnackbarDuration = SnackbarDuration.SHORT
)

/**
 * Enum cho loại Snackbar
 */
enum class SnackbarType {
    INFO,       // Màu xanh dương
    SUCCESS,    // Màu xanh lá
    WARNING,    // Màu vàng/cam
    ERROR       // Màu đỏ
}

/**
 * Enum cho thời gian hiển thị
 */
enum class SnackbarDuration {
    SHORT,      // 3 giây
    LONG,       // 5 giây
    INDEFINITE  // Hiển thị cho đến khi đóng
}

@Singleton
class SnackbarManager @Inject constructor() {

    private val _messages = MutableSharedFlow<SnackbarMessage>(
        replay = 0,
        extraBufferCapacity = 10
    )
    val messages = _messages.asSharedFlow()

    fun showSnackbar(
        title: String,
        content: String,
        dateTime: String = "",
        type: SnackbarType = SnackbarType.SUCCESS,
        duration: SnackbarDuration = SnackbarDuration.SHORT
    ) {
        _messages.tryEmit(
            SnackbarMessage(
                type = type,
                title = title,
                content = content,
                dateTime = dateTime,
                duration = duration
            )
        )
    }

    fun showFromRabbitNotify(notify: RabbitNotify) {
        val type = SnackbarType.SUCCESS

        showSnackbar(
            type = type,
            title = notify.title,
            content = notify.content,
            duration = SnackbarDuration.LONG,
            dateTime = DateTimeFormatter.formatDateTime(notify.dateTime)
        )
    }

    /**
     * Shortcut methods
     */
    fun showInfo(title: String, content: String) {
        showSnackbar(title, content, type = SnackbarType.INFO)
    }

    fun showSuccess(title: String, content: String) {
        showSnackbar(title, content, type = SnackbarType.SUCCESS)
    }

    fun showWarning(title: String, content: String) {
        showSnackbar(title, content, type = SnackbarType.WARNING)
    }

    fun showError(title: String, content: String) {
        showSnackbar(title, content, type = SnackbarType.ERROR)
    }

    fun showException(e: Exception) {
        showSnackbar("Lỗi nghiêm trọng", e.message ?: "Xảy ra lỗi nghiêm trọng", type = SnackbarType.ERROR)
    }

    // XÓA HÀM PRIVATE formatDateTime() CŨ Ở ĐÂY
}