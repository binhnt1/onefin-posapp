package com.onefin.posapp.core.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.annotation.DrawableRes
import com.onefin.posapp.R
import com.onefin.posapp.core.models.Terminal
import com.onefin.posapp.core.models.Transaction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.net.URL

class ReceiptPrinter(
    private val context: Context,
    private val printerHelper: PrinterHelper
) {

    companion object {
        private const val TAG = "ReceiptPrinter"
        private const val PAPER_WIDTH = 32
    }

    suspend fun printReceipt(
        transaction: Transaction,
        terminal: Terminal
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            if (!printerHelper.isReady()) {
                return@withContext Result.failure(Exception("Máy in chưa sẵn sàng"))
            }

            printerHelper.enterPrinterBuffer(true)

            printerHelper.initPrinter()
            printerHelper.setPrintDensity(15)

            printHeader(terminal)
            printTransactionType(transaction)
            printTransactionInfo(transaction)
            printAmountSection(transaction)
            printStatusSection(transaction)

            if (transaction.remark.isNotEmpty()) {
                printNotesSection(transaction.remark)
            }

            printFooter(terminal)

            printerHelper.feedPaper(4)

            printerHelper.exitPrinterBuffer(true)

            Result.success(Unit)
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Error printing receipt")
            Result.failure(e)
        }
    }

    private suspend fun printHeader(terminal: Terminal) {
        printerHelper.printDoubleDivider(PAPER_WIDTH)

        val merchantLogoBitmap = loadMerchantLogo(terminal.logo)
        val onefinLogoBitmap = loadDrawableBitmap(R.drawable.logo_small)

        if (merchantLogoBitmap != null || onefinLogoBitmap != null) {
            val combinedBitmap = combineTwoLogos(merchantLogoBitmap, onefinLogoBitmap)
            if (combinedBitmap != null) {
                printerHelper.printBitmap(combinedBitmap)
            }
        }

        printerHelper.printTextWithFormat(
            text = terminal.name,
            alignment = PrinterHelper.ALIGN_CENTER,
            fontSize = PrinterHelper.TEXT_SIZE_LARGE,
            isBold = true
        )

        printerHelper.printDivider("-", PAPER_WIDTH)

        if (terminal.phone.isNotEmpty()) {
            printerHelper.printTextWithFormat(
                text = "SĐT: ${terminal.phone}",
                alignment = PrinterHelper.ALIGN_CENTER,
                fontSize = PrinterHelper.TEXT_SIZE_NORMAL
            )
        }

        printerHelper.printTwoColumns("MID: ${terminal.mid}", "TID: ${terminal.tid}")

        printerHelper.printDoubleDivider(PAPER_WIDTH)
    }

    private fun printTransactionType(transaction: Transaction) {
        printerHelper.printTextWithFormat(
            text = "HÓA ĐƠN THANH TOÁN",
            alignment = PrinterHelper.ALIGN_CENTER,
            fontSize = PrinterHelper.TEXT_SIZE_MEDIUM,
            isBold = true
        )

        printerHelper.printTextWithFormat(
            text = transaction.getFormTypeText(context),
            alignment = PrinterHelper.ALIGN_CENTER,
            fontSize = PrinterHelper.TEXT_SIZE_NORMAL
        )

        printerHelper.printDoubleDivider(PAPER_WIDTH)
    }

    private fun printTransactionInfo(transaction: Transaction) {
        printerHelper.printTwoColumns("Mã GD:", transaction.transactionId)

        if (transaction.invoiceNumber.isNotEmpty()) {
            printerHelper.printTwoColumns("Hóa đơn:", transaction.invoiceNumber)
        }

        if (transaction.batchNumber.isNotEmpty()) {
            printerHelper.printTwoColumns("Batch:", transaction.batchNumber)
        }

        printerHelper.printTwoColumns("Ngày GD:", transaction.getFormattedTransactionDateTime())

        printerHelper.printDivider("-", PAPER_WIDTH)

        if (transaction.formType == 1) {
            printerHelper.printTwoColumns("Loại thẻ:", transaction.getCardTypeText(context))

            if (transaction.accountNumber.isNotEmpty()) {
                printerHelper.printTwoColumns("Số thẻ:", transaction.getMaskedNumber())
            }

            if (transaction.accountName.isNotEmpty()) {
                printerHelper.printTextWithFormat(
                    text = "Tên chủ thẻ:",
                    alignment = PrinterHelper.ALIGN_LEFT,
                    fontSize = PrinterHelper.TEXT_SIZE_NORMAL
                )
                printerHelper.printTextWithFormat(
                    text = transaction.accountName,
                    alignment = PrinterHelper.ALIGN_LEFT,
                    fontSize = PrinterHelper.TEXT_SIZE_NORMAL
                )
            }

            if (transaction.approvedCode.isNotEmpty()) {
                printerHelper.printTwoColumns("Mã phê duyệt:", transaction.approvedCode)
            }
        }

        printerHelper.printDivider("-", PAPER_WIDTH)
    }

    private fun printAmountSection(transaction: Transaction) {
        val amountStr = printerHelper.formatCurrencyWithPadding(transaction.totalTransAmt, 15)
        printerHelper.printTwoColumns("Số tiền:", amountStr)

        if (transaction.feeTransAmt > 0) {
            val feeStr = printerHelper.formatCurrencyWithPadding(transaction.feeTransAmt, 15)
            printerHelper.printTwoColumns("Phí GD:", feeStr)
        }

        printerHelper.printDoubleDivider(PAPER_WIDTH)

        val totalStr = printerHelper.formatCurrencyWithPadding(transaction.totalTransAmt, 15)
        printerHelper.printTextWithFormat(
            text = "TỔNG CỘNG:",
            alignment = PrinterHelper.ALIGN_LEFT,
            fontSize = PrinterHelper.TEXT_SIZE_MEDIUM,
            isBold = true
        )
        printerHelper.printTextWithFormat(
            text = totalStr,
            alignment = PrinterHelper.ALIGN_RIGHT,
            fontSize = PrinterHelper.TEXT_SIZE_LARGE,
            isBold = true
        )

        printerHelper.printDoubleDivider(PAPER_WIDTH)
    }

    private fun printStatusSection(transaction: Transaction) {
        val statusInfo = transaction.getReceiptStatusInfo(context)
        printerHelper.printTwoColumns("Trạng thái:", statusInfo.text)

        val settledDate = transaction.getFormattedSettledDateTime()
        if (settledDate.isNotEmpty()) {
            printerHelper.printTwoColumns("Ngày quyết toán:", settledDate)
        }
    }

    private fun printNotesSection(notes: String) {
        printerHelper.printDivider("-", PAPER_WIDTH)

        printerHelper.printTextWithFormat(
            text = "Ghi chú:",
            alignment = PrinterHelper.ALIGN_LEFT,
            fontSize = PrinterHelper.TEXT_SIZE_NORMAL
        )

        printerHelper.printTextWithFormat(
            text = notes,
            alignment = PrinterHelper.ALIGN_LEFT,
            fontSize = PrinterHelper.TEXT_SIZE_NORMAL
        )
    }

    private fun printFooter(terminal: Terminal) {
        printerHelper.printDivider("-", PAPER_WIDTH)

        printerHelper.printTextWithFormat(
            text = "Cảm ơn quý khách!",
            alignment = PrinterHelper.ALIGN_CENTER,
            fontSize = PrinterHelper.TEXT_SIZE_NORMAL,
            isBold = true
        )

        printerHelper.printTextWithFormat(
            text = "Hẹn gặp lại!",
            alignment = PrinterHelper.ALIGN_CENTER,
            fontSize = PrinterHelper.TEXT_SIZE_NORMAL
        )

        printerHelper.printNewLine(1)

        if (terminal.phone.isNotEmpty()) {
            printerHelper.printTextWithFormat(
                text = "Hotline: ${terminal.phone}",
                alignment = PrinterHelper.ALIGN_CENTER,
                fontSize = PrinterHelper.TEXT_SIZE_NORMAL
            )
        }

        printerHelper.printDivider("-", PAPER_WIDTH)

        printerHelper.printTextWithFormat(
            text = "Powered by OneFin POS",
            alignment = PrinterHelper.ALIGN_CENTER,
            fontSize = PrinterHelper.TEXT_SIZE_NORMAL
        )

        printerHelper.printDoubleDivider(PAPER_WIDTH)
    }

    private suspend fun loadMerchantLogo(logoUrl: String): Bitmap? = withContext(Dispatchers.IO) {
        try {
            if (logoUrl.isEmpty()) return@withContext null

            val url = URL(logoUrl)
            val bitmap = BitmapFactory.decodeStream(url.openConnection().getInputStream())

            resizeBitmap(bitmap, 150, 80)
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Error loading merchant logo")
            null
        }
    }

    private fun loadDrawableBitmap(@DrawableRes drawableId: Int): Bitmap? {
        return try {
            val drawable = context.resources.getDrawable(drawableId, null)
            val bitmap = Bitmap.createBitmap(
                drawable.intrinsicWidth,
                drawable.intrinsicHeight,
                Bitmap.Config.ARGB_8888
            )
            val canvas = android.graphics.Canvas(bitmap)
            drawable.setBounds(0, 0, canvas.width, canvas.height)
            drawable.draw(canvas)

            resizeBitmap(bitmap, 150, 80)
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Error loading drawable bitmap")
            null
        }
    }

    private fun resizeBitmap(bitmap: Bitmap, maxWidth: Int, maxHeight: Int): Bitmap {
        val width = bitmap.width
        val height = bitmap.height

        val ratio = minOf(maxWidth.toFloat() / width, maxHeight.toFloat() / height)

        val newWidth = (width * ratio).toInt()
        val newHeight = (height * ratio).toInt()

        return Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)
    }

    private fun combineTwoLogos(leftBitmap: Bitmap?, rightBitmap: Bitmap?): Bitmap? {
        if (leftBitmap == null && rightBitmap == null) return null

        val left = leftBitmap ?: createEmptyBitmap(1, 1)
        val right = rightBitmap ?: createEmptyBitmap(1, 1)

        // Tổng chiều rộng giấy (pixels), giả sử 384px cho giấy 58mm
        val paperWidthPx = 384

        // THÊM MARGIN BOTTOM
        val marginBottom = 40 // Điều chỉnh giá trị này để tăng/giảm khoảng cách

        val totalWidth = paperWidthPx
        val maxHeight = maxOf(left.height, right.height) + marginBottom // Thêm margin vào height

        val combinedBitmap = Bitmap.createBitmap(totalWidth, maxHeight, Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(combinedBitmap)

        canvas.drawColor(android.graphics.Color.WHITE)

        // Logo bên trái - căn sát mép trái
        val leftY = (maxHeight - marginBottom - left.height) / 2f // Trừ margin khi tính Y
        canvas.drawBitmap(left, 0f, leftY, null)

        // Logo bên phải - căn sát mép phải
        val rightX = (totalWidth - right.width).toFloat()
        val rightY = (maxHeight - marginBottom - right.height) / 2f // Trừ margin khi tính Y
        canvas.drawBitmap(right, rightX, rightY, null)

        return combinedBitmap
    }

    private fun createEmptyBitmap(width: Int, height: Int): Bitmap {
        return Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).apply {
            eraseColor(android.graphics.Color.TRANSPARENT)
        }
    }
}