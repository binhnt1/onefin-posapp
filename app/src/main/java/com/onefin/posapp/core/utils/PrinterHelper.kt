package com.onefin.posapp.core.utils

import android.content.Context
import android.graphics.Bitmap
import android.os.RemoteException
import com.onefin.posapp.PosApplication
import com.sunmi.peripheral.printer.InnerResultCallback
import com.sunmi.peripheral.printer.SunmiPrinterService
import kotlinx.coroutines.delay

class PrinterHelper(private val context: Context) {

    companion object {
        const val ALIGN_LEFT = 0
        const val ALIGN_CENTER = 1
        const val ALIGN_RIGHT = 2

        const val TEXT_SIZE_NORMAL = 24f
        const val TEXT_SIZE_MEDIUM = 28f
        const val TEXT_SIZE_LARGE = 32f
        const val TEXT_SIZE_XLARGE = 40f
    }

    private val printerService: SunmiPrinterService?
        get() = (context.applicationContext as? PosApplication)?.sunmiPrinterService

    fun isReady(): Boolean {
        val ready = printerService != null
        return ready
    }

    suspend fun waitForReady(timeoutMs: Long = 3000): Boolean {
        val startTime = System.currentTimeMillis()
        while (System.currentTimeMillis() - startTime < timeoutMs) {
            if (isReady()) return true
            delay(100)
        }
        return false
    }

    fun enterPrinterBuffer(clear: Boolean) {
        try {
            printerService?.enterPrinterBuffer(clear)
        } catch (_: RemoteException) {
        }
    }

    fun exitPrinterBuffer(commit: Boolean) {
        try {
            printerService?.exitPrinterBuffer(commit)
        } catch (e: RemoteException) {
        }
    }

    fun setPrintDensity(density: Int) {
        try {
            val validDensity = density.coerceIn(0, 15)

            printerService?.sendRAWData(
                byteArrayOf(0x1D, 0x28, 0x4B, 0x02, 0x00, 0x32, validDensity.toByte()),
                null
            )

            printerService?.sendRAWData(
                byteArrayOf(0x1B, 0x37, 0x0A, 0x64, 0x64),
                null
            )
        } catch (_: RemoteException) {
        }
    }

    fun setAlignment(alignment: Int, callback: InnerResultCallback? = null) {
        try {
            printerService?.setAlignment(alignment, callback)
        } catch (_: RemoteException) {
        }
    }

    fun setFontSize(size: Float, callback: InnerResultCallback? = null) {
        try {
            printerService?.setFontSize(size, callback)
        } catch (_: RemoteException) {
        }
    }

    fun printText(text: String, callback: InnerResultCallback? = null) {
        try {
            printerService?.printText(text, callback)
        } catch (_: RemoteException) {
        }
    }

    fun printTextWithFormat(
        text: String,
        alignment: Int = ALIGN_LEFT,
        fontSize: Float = TEXT_SIZE_NORMAL,
        isBold: Boolean = false
    ) {
        try {
            setAlignment(alignment)
            setFontSize(fontSize)

            if (isBold) {
                printerService?.sendRAWData(byteArrayOf(0x1B, 0x45, 0x01), null)
            }

            printText(text)
            printNewLine()

            if (isBold) {
                printerService?.sendRAWData(byteArrayOf(0x1B, 0x45, 0x00), null)
            }

            setAlignment(ALIGN_LEFT)
            setFontSize(TEXT_SIZE_NORMAL)
        } catch (_: RemoteException) {
        }
    }

    fun printNewLine(lines: Int = 1) {
        try {
            printerService?.lineWrap(lines, null)
        } catch (_: RemoteException) {
        }
    }

    fun printDivider(char: String = "-", length: Int = 32) {
        try {
            val divider = char.repeat(length)
            printTextWithFormat(divider, ALIGN_CENTER)
        } catch (_: Exception) {
        }
    }

    fun printDoubleDivider(length: Int = 32) {
        printDivider("=", length)
    }

    fun printTwoColumns(left: String, right: String, totalWidth: Int = 31) {
        try {
            val rightLength = right.length
            val leftMaxWidth = totalWidth - rightLength - 1 // Trừ 1 space tối thiểu

            if (left.length <= leftMaxWidth) {
                // Left vừa đủ, in trên 1 dòng
                val spaces = " ".repeat(totalWidth - left.length - rightLength)
                val line = left + spaces + right

                setAlignment(ALIGN_LEFT)
                printText(line)
                printNewLine()
            } else {
                val leftLines = wrapText(left, totalWidth) // Dùng full width cho left
                setAlignment(ALIGN_LEFT)
                for (line in leftLines) {
                    printText(line)
                    printNewLine()
                }

                var repeatValue = totalWidth - rightLength
                if (repeatValue < 0)
                    repeatValue = 0
                val spaces = " ".repeat(repeatValue)
                setAlignment(ALIGN_LEFT)
                printText(spaces + right)
                printNewLine()
            }
        } catch (_: Exception) {
        }
    }

    private fun wrapText(text: String, maxWidth: Int): List<String> {
        if (text.length <= maxWidth) return listOf(text)

        val lines = mutableListOf<String>()
        var remaining = text

        while (remaining.length > maxWidth) {
            var cutPos = maxWidth
            val lastSpace = remaining.take(maxWidth).lastIndexOf(' ')

            // Ưu tiên cắt tại space
            if (lastSpace > 0) {
                cutPos = lastSpace
            }

            lines.add(remaining.take(cutPos).trim())
            remaining = remaining.substring(cutPos).trim()
        }

        if (remaining.isNotEmpty()) {
            lines.add(remaining)
        }

        return lines
    }

    fun printThreeColumns(
        left: String,
        center: String,
        right: String,
        leftWidth: Int = 16,
        centerWidth: Int = 6,
        rightWidth: Int = 10
    ) {
        try {
            val leftText = if (left.length > leftWidth) {
                left.substring(0, leftWidth)
            } else {
                left.padEnd(leftWidth)
            }

            val centerText = center.padStart(centerWidth)
            val rightText = right.padStart(rightWidth)

            val line = leftText + centerText + rightText

            setAlignment(ALIGN_LEFT)
            printText(line)
            printNewLine()
        } catch (_: Exception) {
        }
    }

    fun printColumns(
        texts: Array<String>,
        widths: IntArray,
        aligns: IntArray,
        callback: InnerResultCallback? = null
    ) {
        try {
            printerService?.printColumnsText(texts, widths, aligns, callback)
        } catch (_: RemoteException) {
        }
    }

    fun printQRCode(data: String, size: Int = 8, callback: InnerResultCallback? = null) {
        try {
            printerService?.setAlignment(ALIGN_CENTER, null)
            printerService?.printQRCode(data, size, 0, callback)
            printNewLine(1)
            setAlignment(ALIGN_LEFT)
        } catch (_: RemoteException) {
        }
    }

    fun printBarcode(
        data: String,
        barcodeType: Int = 8,
        width: Int = 2,
        height: Int = 90,
        callback: InnerResultCallback? = null
    ) {
        try {
            printerService?.setAlignment(ALIGN_CENTER, null)
            printerService?.printBarCode(data, barcodeType, height, width, 0, callback)
            printNewLine(1)
            setAlignment(ALIGN_LEFT)
        } catch (_: RemoteException) {
        }
    }

    fun printBitmap(bitmap: Bitmap, callback: InnerResultCallback? = null) {
        try {
            printerService?.setAlignment(ALIGN_CENTER, null)
            printerService?.printBitmap(bitmap, callback)
            printNewLine(1)
            setAlignment(ALIGN_LEFT)
        } catch (_: RemoteException) {
        }
    }

    fun feedPaper(lines: Int = 3) {
        try {
            printerService?.lineWrap(lines, null)
        } catch (_: RemoteException) {
        }
    }

    fun cutPaper() {
        try {
            printerService?.sendRAWData(byteArrayOf(0x1D, 0x56, 0x00), null)
        } catch (_: RemoteException) {
        }
    }

    fun openCashDrawer(callback: InnerResultCallback? = null) {
        try {
            printerService?.openDrawer(callback)
        } catch (_: RemoteException) {
        }
    }

    fun getPrinterStatus(): Int {
        return try {
            printerService?.updatePrinterState() ?: -1
        } catch (_: RemoteException) {
            -1
        }
    }

    fun hasPaper(): Boolean {
        val status = getPrinterStatus()
        return status == 0
    }

    fun getSerialNumber(): String? {
        return try {
            printerService?.printerSerialNo
        } catch (_: RemoteException) {
            null
        }
    }

    fun getPrinterVersion(): String? {
        return try {
            printerService?.printerVersion
        } catch (_: RemoteException) {
            null
        }
    }

    fun formatCurrencyWithPadding(amount: Long, width: Int = 12): String {
        val formatted = UtilHelper.formatCurrency(amount, "đ")
        return formatted.padStart(width)
    }

    fun initPrinter(callback: InnerResultCallback? = null) {
        try {
            printerService?.printerInit(callback)
        } catch (_: RemoteException) {
        }
    }
}