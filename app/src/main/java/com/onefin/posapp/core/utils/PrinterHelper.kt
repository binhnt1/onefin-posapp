package com.onefin.posapp.core.utils

import android.content.Context
import android.graphics.Bitmap
import android.os.RemoteException
import com.onefin.posapp.PosApplication
import com.sunmi.peripheral.printer.InnerResultCallback
import com.sunmi.peripheral.printer.SunmiPrinterService
import timber.log.Timber
import kotlinx.coroutines.delay

class PrinterHelper(private val context: Context) {

    companion object {
        private const val TAG = "PrinterHelper"

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
        if (!ready) {
            Timber.tag(TAG).w("Printer not ready - Service: ${printerService != null}")
        }
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
            Timber.tag(TAG).d("Enter printer buffer")
        } catch (e: RemoteException) {
            Timber.tag(TAG).e(e, "Error entering printer buffer")
        }
    }

    fun exitPrinterBuffer(commit: Boolean) {
        try {
            printerService?.exitPrinterBuffer(commit)
            Timber.tag(TAG).d("Exit printer buffer and commit")
        } catch (e: RemoteException) {
            Timber.tag(TAG).e(e, "Error exiting printer buffer")
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

            Timber.tag(TAG).d("Set print density: $validDensity")
        } catch (e: RemoteException) {
            Timber.tag(TAG).e(e, "Error setting print density")
        }
    }

    fun setAlignment(alignment: Int, callback: InnerResultCallback? = null) {
        try {
            printerService?.setAlignment(alignment, callback)
        } catch (e: RemoteException) {
            Timber.tag(TAG).e(e, "Error setting alignment")
        }
    }

    fun setFontSize(size: Float, callback: InnerResultCallback? = null) {
        try {
            printerService?.setFontSize(size, callback)
        } catch (e: RemoteException) {
            Timber.tag(TAG).e(e, "Error setting font size")
        }
    }

    fun printText(text: String, callback: InnerResultCallback? = null) {
        try {
            printerService?.printText(text, callback)
        } catch (e: RemoteException) {
            Timber.tag(TAG).e(e, "Error printing text")
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
        } catch (e: RemoteException) {
            Timber.tag(TAG).e(e, "Error printing formatted text")
        }
    }

    fun printNewLine(lines: Int = 1) {
        try {
            printerService?.lineWrap(lines, null)
        } catch (e: RemoteException) {
            Timber.tag(TAG).e(e, "Error printing new line")
        }
    }

    fun printDivider(char: String = "-", length: Int = 32) {
        try {
            val divider = char.repeat(length)
            printTextWithFormat(divider, ALIGN_CENTER)
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Error printing divider")
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
                // Left quá dài - IN RIÊNG BIỆT HOÀN TOÀN

                // 1. In tất cả left trên các dòng riêng
                val leftLines = wrapText(left, totalWidth) // Dùng full width cho left
                setAlignment(ALIGN_LEFT)
                for (line in leftLines) {
                    printText(line)
                    printNewLine()
                }

                // 2. In right trên dòng riêng, căn phải
                val spaces = " ".repeat(totalWidth - rightLength)
                setAlignment(ALIGN_LEFT)
                printText(spaces + right)
                printNewLine()
            }
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Error printing two columns")
        }
    }

    private fun wrapText(text: String, maxWidth: Int): List<String> {
        if (text.length <= maxWidth) return listOf(text)

        val lines = mutableListOf<String>()
        var remaining = text

        while (remaining.length > maxWidth) {
            var cutPos = maxWidth
            val lastSpace = remaining.substring(0, maxWidth).lastIndexOf(' ')

            // Ưu tiên cắt tại space
            if (lastSpace > 0) {
                cutPos = lastSpace
            }

            lines.add(remaining.substring(0, cutPos).trim())
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
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Error printing three columns")
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
        } catch (e: RemoteException) {
            Timber.tag(TAG).e(e, "Error printing columns")
        }
    }

    fun printQRCode(data: String, size: Int = 8, callback: InnerResultCallback? = null) {
        try {
            printerService?.setAlignment(ALIGN_CENTER, null)
            printerService?.printQRCode(data, size, 0, callback)
            printNewLine(1)
            setAlignment(ALIGN_LEFT)
        } catch (e: RemoteException) {
            Timber.tag(TAG).e(e, "Error printing QR code")
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
        } catch (e: RemoteException) {
            Timber.tag(TAG).e(e, "Error printing barcode")
        }
    }

    fun printBitmap(bitmap: Bitmap, callback: InnerResultCallback? = null) {
        try {
            printerService?.setAlignment(ALIGN_CENTER, null)
            printerService?.printBitmap(bitmap, callback)
            printNewLine(1)
            setAlignment(ALIGN_LEFT)
        } catch (e: RemoteException) {
            Timber.tag(TAG).e(e, "Error printing bitmap")
        }
    }

    fun feedPaper(lines: Int = 3) {
        try {
            printerService?.lineWrap(lines, null)
        } catch (e: RemoteException) {
            Timber.tag(TAG).e(e, "Error feeding paper")
        }
    }

    fun cutPaper() {
        try {
            printerService?.sendRAWData(byteArrayOf(0x1D, 0x56, 0x00), null)
        } catch (e: RemoteException) {
            Timber.tag(TAG).e(e, "Error cutting paper")
        }
    }

    fun openCashDrawer(callback: InnerResultCallback? = null) {
        try {
            printerService?.openDrawer(callback)
        } catch (e: RemoteException) {
            Timber.tag(TAG).e(e, "Error opening cash drawer")
        }
    }

    fun getPrinterStatus(): Int {
        return try {
            printerService?.updatePrinterState() ?: -1
        } catch (e: RemoteException) {
            Timber.tag(TAG).e(e, "Error getting printer status")
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
        } catch (e: RemoteException) {
            Timber.tag(TAG).e(e, "Error getting serial number")
            null
        }
    }

    fun getPrinterVersion(): String? {
        return try {
            printerService?.printerVersion
        } catch (e: RemoteException) {
            Timber.tag(TAG).e(e, "Error getting printer version")
            null
        }
    }

    fun formatCurrency(amount: Long): String {
        return String.format("%,d", amount).replace(",", ".") + "đ"
    }

    fun formatCurrencyWithPadding(amount: Long, width: Int = 12): String {
        val formatted = formatCurrency(amount)
        return formatted.padStart(width)
    }

    fun initPrinter(callback: InnerResultCallback? = null) {
        try {
            printerService?.printerInit(callback)
        } catch (e: RemoteException) {
            Timber.tag(TAG).e(e, "Error initializing printer")
        }
    }
}