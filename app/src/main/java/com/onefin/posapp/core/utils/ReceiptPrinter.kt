package com.onefin.posapp.core.utils

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.annotation.DrawableRes
import com.onefin.posapp.R
import com.onefin.posapp.core.models.Terminal
import com.onefin.posapp.core.models.Transaction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.URL
import androidx.core.graphics.createBitmap
import androidx.core.graphics.scale
import com.onefin.posapp.core.models.data.SettleResultData

class ReceiptPrinter(
    private val context: Context,
    private val printerHelper: PrinterHelper
) {

    companion object {
        private const val TAG = "ReceiptPrinter"
        private const val PAPER_WIDTH = 32
    }

    suspend fun printReceipt(
        terminal: Terminal,
        transaction: Transaction
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

            printFooter()
            printerHelper.feedPaper(4)
            printerHelper.exitPrinterBuffer(true)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun printReceiptWithSignature(
        terminal: Terminal,
        transaction: Transaction,
        signatureBitmap: ByteArray?
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

            // 🔥 IN CHỮ KÝ
            if (signatureBitmap != null) {
                printSignatureSection(signatureBitmap)
            }

            if (transaction.remark.isNotEmpty()) {
                printNotesSection(transaction.remark)
            }
            printFooter()
            printerHelper.feedPaper(4)
            printerHelper.exitPrinterBuffer(true)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun printSettlementReceipt(
        settleData: SettleResultData
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            if (!printerHelper.isReady()) {
                return@withContext Result.failure(Exception("Máy in chưa sẵn sàng"))
            }

            printerHelper.enterPrinterBuffer(true)
            printerHelper.initPrinter()
            printerHelper.setPrintDensity(15)

            printSettlementHeader(settleData)
            printSettlementInfo(settleData)
            printSettlementStatistics(settleData)
            printSettlementSummary(settleData)
            printSettlementConfirmation(settleData)
            printSettlementStatus(settleData)
            printFooter()

            printerHelper.feedPaper(4)
            printerHelper.exitPrinterBuffer(true)

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun printSettlementHeader(settleData: SettleResultData) {
        printerHelper.printDoubleDivider(PAPER_WIDTH)

        // Logo công ty bên trái + Tên ngân hàng bên phải
        val companyLogoBitmap = loadDrawableBitmap(R.drawable.logo)
        val provider = settleData.header?.provider ?: ""
        val bankTextBitmap = if (provider.isNotEmpty()) {
            createTextBitmap(provider)
        } else null

        if (companyLogoBitmap != null || bankTextBitmap != null) {
            val combinedBitmap = combineTwoLogos(companyLogoBitmap, bankTextBitmap)
            if (combinedBitmap != null) {
                printerHelper.printBitmap(combinedBitmap)
            }
        }

        printerHelper.printDivider("-", PAPER_WIDTH)

        // Tiêu đề hóa đơn kết toán
        printerHelper.printTextWithFormat(
            text = "HÓA ĐƠN KẾT TOÁN",
            alignment = PrinterHelper.ALIGN_CENTER,
            fontSize = PrinterHelper.TEXT_SIZE_LARGE,
            isBold = true
        )

        printerHelper.printTextWithFormat(
            text = "SETTLEMENT RECEIPT",
            alignment = PrinterHelper.ALIGN_CENTER,
            fontSize = PrinterHelper.TEXT_SIZE_NORMAL
        )

        printerHelper.printDoubleDivider(PAPER_WIDTH)
    }

    // Hàm helper tạo bitmap từ text
    private fun createTextBitmap(text: String): Bitmap {
        val paint = android.graphics.Paint().apply {
            color = android.graphics.Color.BLACK
            textSize = 24f
            textAlign = android.graphics.Paint.Align.CENTER
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            isAntiAlias = true
        }

        // Đo kích thước text
        val bounds = android.graphics.Rect()
        paint.getTextBounds(text, 0, text.length, bounds)

        val textWidth = bounds.width() + 20 // padding
        val textHeight = bounds.height() + 20

        // Tạo bitmap với kích thước phù hợp
        val bitmap = createBitmap(
            minOf(textWidth, 150),
            minOf(textHeight, 80)
        )

        val canvas = android.graphics.Canvas(bitmap)
        canvas.drawColor(android.graphics.Color.WHITE)

        // Vẽ text vào giữa bitmap
        val x = bitmap.width / 2f
        val y = (bitmap.height / 2f) - ((paint.descent() + paint.ascent()) / 2f)

        canvas.drawText(text, x, y, paint)

        return bitmap
    }
    private fun printSettlementInfo(settleData: SettleResultData) {
        // Thông tin merchant & thiết bị
        settleData.header?.let { header ->
            if (header.merchantId?.isNotEmpty() == true) {
                printerHelper.printTwoColumns("Mã merchant:", header.merchantId)
            }
            if (header.terminalId?.isNotEmpty() == true) {
                printerHelper.printTwoColumns("Mã thiết bị:", header.terminalId)
            }
            if (header.transType?.isNotEmpty() == true) {
                printerHelper.printTwoColumns("Loại GD:", header.transType)
            }
        }

        printerHelper.printDoubleDivider(PAPER_WIDTH)

        // Thông tin batch
        settleData.data?.let { data ->
            if (data.batchNo?.isNotEmpty() == true) {
                printerHelper.printTwoColumns("Số batch:", data.batchNo)
            }
        }

        val dateTime = settleData.getFormattedDateTime()
        if (dateTime != null) {
            printerHelper.printTwoColumns("Thời gian:", dateTime)
        }

        settleData.header?.let { header ->
            if (header.transId?.isNotEmpty() == true) {
                printerHelper.printTwoColumns("Mã GD:", header.transId)
            }
            if (header.merchantTransId?.isNotEmpty() == true) {
                printerHelper.printTwoColumns("Mã tham chiếu:", header.merchantTransId)
            }
        }

        printerHelper.printDoubleDivider(PAPER_WIDTH)
    }

    private fun printSettlementStatistics(settleData: SettleResultData) {
        printerHelper.printTextWithFormat(
            text = "THỐNG KÊ GIAO DỊCH",
            alignment = PrinterHelper.ALIGN_CENTER,
            fontSize = PrinterHelper.TEXT_SIZE_MEDIUM,
            isBold = true
        )

        printerHelper.printDivider("-", PAPER_WIDTH)

        settleData.data?.let { data ->
            // Giao dịch mua hàng (SALE)
            val saleQty = data.saleQuantity ?: "0"
            val saleAmt = data.saleAmount ?: "0"

            if (saleQty != "0") {
                printerHelper.printTextWithFormat(
                    text = "GIAO DỊCH MUA HÀNG (SALE):",
                    alignment = PrinterHelper.ALIGN_LEFT,
                    fontSize = PrinterHelper.TEXT_SIZE_NORMAL,
                    isBold = true
                )

                printerHelper.printTwoColumns(
                    "  Số lượng:",
                    "$saleQty giao dịch"
                )

                val saleAmountFormatted = printerHelper.formatCurrencyWithPadding(
                    saleAmt.toLongOrNull() ?: 0L,
                    15
                )
                printerHelper.printTwoColumns("  Tổng tiền:", saleAmountFormatted)

                printerHelper.printNewLine(1)
            }

            // Giao dịch hủy (VOID)
            val voidQty = data.voidQuantity ?: "0"
            val voidAmt = data.voidAmount ?: "0"

            if (voidQty != "0") {
                printerHelper.printTextWithFormat(
                    text = "GIAO DỊCH HỦY (VOID):",
                    alignment = PrinterHelper.ALIGN_LEFT,
                    fontSize = PrinterHelper.TEXT_SIZE_NORMAL,
                    isBold = true
                )

                printerHelper.printTwoColumns(
                    "  Số lượng:",
                    "$voidQty giao dịch"
                )

                val voidAmountFormatted = printerHelper.formatCurrencyWithPadding(
                    voidAmt.toLongOrNull() ?: 0L,
                    15
                )
                printerHelper.printTwoColumns("  Tổng tiền:", voidAmountFormatted)
            }
        }

        printerHelper.printDoubleDivider(PAPER_WIDTH)
    }

    private fun printSettlementSummary(settleData: SettleResultData) {
        printerHelper.printTextWithFormat(
            text = "TỔNG KẾT",
            alignment = PrinterHelper.ALIGN_CENTER,
            fontSize = PrinterHelper.TEXT_SIZE_MEDIUM,
            isBold = true
        )

        printerHelper.printDivider("-", PAPER_WIDTH)

        settleData.data?.let { data ->
            // Tổng số giao dịch
            val totalQty = data.totalQuantity ?: "0"
            printerHelper.printTwoColumns("TỔNG SỐ GD:", "$totalQty GD")

            // Tổng tiền bán
            val saleAmount = data.saleAmount?.toLongOrNull() ?: 0L
            val saleFormatted = printerHelper.formatCurrencyWithPadding(saleAmount, 15)
            printerHelper.printTwoColumns("Tổng tiền bán:", saleFormatted)

            // Tổng tiền hoàn
            val voidAmount = data.voidAmount?.toLongOrNull() ?: 0L
            if (voidAmount > 0) {
                val voidFormatted = printerHelper.formatCurrencyWithPadding(-voidAmount, 15)
                printerHelper.printTwoColumns("Tổng tiền hoàn:", voidFormatted)
            }

            printerHelper.printDivider("-", PAPER_WIDTH)

            // Net amount
            val netAmount = settleData.getNetAmount()
            val netFormatted = printerHelper.formatCurrencyWithPadding(netAmount, 15)

            printerHelper.printTextWithFormat(
                text = "TỔNG THANH TOÁN:",
                alignment = PrinterHelper.ALIGN_LEFT,
                fontSize = PrinterHelper.TEXT_SIZE_MEDIUM,
                isBold = true
            )
            printerHelper.printTextWithFormat(
                text = netFormatted,
                alignment = PrinterHelper.ALIGN_RIGHT,
                fontSize = PrinterHelper.TEXT_SIZE_LARGE,
                isBold = true
            )
        }

        printerHelper.printDoubleDivider(PAPER_WIDTH)
    }

    private fun printSettlementConfirmation(settleData: SettleResultData) {
        printerHelper.printTextWithFormat(
            text = "THÔNG TIN XÁC NHẬN",
            alignment = PrinterHelper.ALIGN_CENTER,
            fontSize = PrinterHelper.TEXT_SIZE_MEDIUM,
            isBold = true
        )

        printerHelper.printDivider("-", PAPER_WIDTH)

        settleData.data?.let { data ->
            if (data.approveCode?.isNotEmpty() == true) {
                printerHelper.printTwoColumns("Mã phê duyệt:", data.approveCode)
            }
            if (data.refNo?.isNotEmpty() == true) {
                printerHelper.printTwoColumns("Mã tham chiếu:", data.refNo)
            }
            if (data.traceNo?.isNotEmpty() == true) {
                printerHelper.printTwoColumns("Mã trace:", data.traceNo)
            }
            if (data.isoResponseCode?.isNotEmpty() == true) {
                val isoText = when (data.isoResponseCode) {
                    "00" -> "${data.isoResponseCode} - Thành công"
                    else -> data.isoResponseCode
                }
                printerHelper.printTwoColumns("Mã ISO:", isoText)
            }
            if (data.currency?.isNotEmpty() == true) {
                printerHelper.printTwoColumns("Loại tiền:", data.currency)
            }
        }

        printerHelper.printDivider("-", PAPER_WIDTH)
    }

    private fun printSettlementStatus(settleData: SettleResultData) {
        val isSuccess = settleData.isSuccess()
        val statusText = if (isSuccess) "✓ KẾT TOÁN THÀNH CÔNG" else "✗ KẾT TOÁN THẤT BẠI"

        printerHelper.printTextWithFormat(
            text = statusText,
            alignment = PrinterHelper.ALIGN_CENTER,
            fontSize = PrinterHelper.TEXT_SIZE_MEDIUM,
            isBold = true
        )

        settleData.status?.let { status ->
            if (!status.message.isNullOrEmpty() && status.message != "Success") {
                printerHelper.printTextWithFormat(
                    text = "(${status.message})",
                    alignment = PrinterHelper.ALIGN_CENTER,
                    fontSize = PrinterHelper.TEXT_SIZE_NORMAL
                )
            }
        }

        printerHelper.printDoubleDivider(PAPER_WIDTH)

        // Ngày in
        val currentDate = settleData.getFormattedDateTime() ?:
        java.text.SimpleDateFormat("dd/MM/yyyy HH:mm", java.util.Locale.getDefault())
            .format(java.util.Date())

        printerHelper.printTextWithFormat(
            text = "Ngày in: $currentDate",
            alignment = PrinterHelper.ALIGN_CENTER,
            fontSize = PrinterHelper.TEXT_SIZE_NORMAL
        )

        printerHelper.printDoubleDivider(PAPER_WIDTH)

        printerHelper.printTextWithFormat(
            text = "Vui lòng giữ phiếu để đối chiếu",
            alignment = PrinterHelper.ALIGN_CENTER,
            fontSize = PrinterHelper.TEXT_SIZE_NORMAL
        )
    }

    private fun printSignatureSection(signatureBytes: ByteArray) {
        try {
            printerHelper.printDivider("-", PAPER_WIDTH)

            printerHelper.printTextWithFormat(
                text = "Chữ ký khách hàng:",
                alignment = PrinterHelper.ALIGN_LEFT,
                fontSize = PrinterHelper.TEXT_SIZE_NORMAL
            )

            printerHelper.printNewLine(1)

            // Convert ByteArray to Bitmap
            val bitmap = BitmapFactory.decodeByteArray(
                signatureBytes,
                0,
                signatureBytes.size
            )

            val resizedBitmap = resizeBitmap(bitmap, 300, 150)
            printerHelper.printBitmap(resizedBitmap)

            bitmap.recycle()
            resizedBitmap.recycle()

            printerHelper.printDivider("-", PAPER_WIDTH)
        } catch (_: Exception) {
        }
    }

    private suspend fun printHeader(terminal: Terminal) {
        printerHelper.printDoubleDivider(PAPER_WIDTH)

        // Load 3 logos: Bank (left), Merchant (center), OneFin (right)
        val merchantLogoBitmap = loadMerchantLogo(terminal.logo)
        val bankLogoBitmap = loadBankLogo(terminal.bankLogo ?: "")
        val onefinLogoBitmap = loadDrawableBitmap(R.drawable.logo)

        // Print combined logos if at least one exists
        if (bankLogoBitmap != null || merchantLogoBitmap != null || onefinLogoBitmap != null) {
            val combinedBitmap = combineThreeLogos(bankLogoBitmap, merchantLogoBitmap, onefinLogoBitmap)
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

        printerHelper.printTextWithFormat(
            text = "MID: ${terminal.mid}  TID: ${terminal.tid}",
            alignment = PrinterHelper.ALIGN_CENTER,
            fontSize = PrinterHelper.TEXT_SIZE_NORMAL
        )

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

    private fun printFooter() {
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
        printerHelper.printTextWithFormat(
            text = "Hotline: 1900996688",
            alignment = PrinterHelper.ALIGN_CENTER,
            fontSize = PrinterHelper.TEXT_SIZE_NORMAL
        )

        printerHelper.printDivider("-", PAPER_WIDTH)

        // Footer terms
        printerHelper.printTextWithFormat(
            text = "NO REFUND/KHÔNG HOÀN TRẢ",
            alignment = PrinterHelper.ALIGN_CENTER,
            fontSize = PrinterHelper.TEXT_SIZE_NORMAL
        )

        printerHelper.printTextWithFormat(
            text = "NO SIGNATURE REQUIRED/KHÔNG YÊU CẦU KHÁCH HÀNG KÝ",
            alignment = PrinterHelper.ALIGN_CENTER,
            fontSize = PrinterHelper.TEXT_SIZE_NORMAL
        )

        printerHelper.printNewLine(1)
        printerHelper.printTextWithFormat(
            text = "CARDHOLDER'S SIGNATURE/CHỮ KÝ CỦA CHỦ THẺ",
            alignment = PrinterHelper.ALIGN_CENTER,
            fontSize = PrinterHelper.TEXT_SIZE_NORMAL
        )

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
        } catch (_: Exception) {
            null
        }
    }

    private suspend fun loadBankLogo(logoUrl: String): Bitmap? = withContext(Dispatchers.IO) {
        try {
            if (logoUrl.isEmpty()) return@withContext null

            val url = URL(logoUrl)
            val bitmap = BitmapFactory.decodeStream(url.openConnection().getInputStream())

            resizeBitmap(bitmap, 150, 80)
        } catch (_: Exception) {
            null
        }
    }

    @SuppressLint("UseCompatLoadingForDrawables")
    private fun loadDrawableBitmap(@DrawableRes drawableId: Int): Bitmap? {
        return try {
            val drawable = context.resources.getDrawable(drawableId, null)
            val bitmap = createBitmap(drawable.intrinsicWidth, drawable.intrinsicHeight)
            val canvas = android.graphics.Canvas(bitmap)
            drawable.setBounds(0, 0, canvas.width, canvas.height)
            drawable.draw(canvas)

            resizeBitmap(bitmap, 150, 80)
        } catch (_: Exception) {
            null
        }
    }

    private fun resizeBitmap(bitmap: Bitmap, maxWidth: Int, maxHeight: Int): Bitmap {
        val width = bitmap.width
        val height = bitmap.height

        val ratio = minOf(maxWidth.toFloat() / width, maxHeight.toFloat() / height)

        val newWidth = (width * ratio).toInt()
        val newHeight = (height * ratio).toInt()

        return bitmap.scale(newWidth, newHeight)
    }

    private fun combineTwoLogos(leftBitmap: Bitmap?, rightBitmap: Bitmap?): Bitmap? {
        if (leftBitmap == null && rightBitmap == null) return null

        val left = leftBitmap ?: createEmptyBitmap()
        val right = rightBitmap ?: createEmptyBitmap()
        val paperWidthPx = 384

        // THÊM MARGIN BOTTOM
        val marginBottom = 40

        val totalWidth = paperWidthPx
        val maxHeight = maxOf(left.height, right.height) + marginBottom // Thêm margin vào height

        val combinedBitmap = createBitmap(totalWidth, maxHeight)
        val canvas = android.graphics.Canvas(combinedBitmap)

        canvas.drawColor(android.graphics.Color.WHITE)

        val leftY = (maxHeight - marginBottom - left.height) / 2f // Trừ margin khi tính Y
        canvas.drawBitmap(left, 0f, leftY, null)

        // Logo bên phải - căn sát mép phải
        val rightX = (totalWidth - right.width).toFloat()
        val rightY = (maxHeight - marginBottom - right.height) / 2f // Trừ margin khi tính Y
        canvas.drawBitmap(right, rightX, rightY, null)

        return combinedBitmap
    }

    private fun combineThreeLogos(
        leftBitmap: Bitmap?,
        centerBitmap: Bitmap?,
        rightBitmap: Bitmap?
    ): Bitmap? {
        // If all logos are null, return null
        if (leftBitmap == null && centerBitmap == null && rightBitmap == null) return null

        val paperWidthPx = 384
        val marginBottom = 40

        // Collect non-null bitmaps
        val logos = listOfNotNull(leftBitmap, centerBitmap, rightBitmap)
        if (logos.isEmpty()) return null

        val maxHeight = logos.maxOf { it.height } + marginBottom
        val totalWidth = paperWidthPx

        val combinedBitmap = createBitmap(totalWidth, maxHeight)
        val canvas = android.graphics.Canvas(combinedBitmap)
        canvas.drawColor(android.graphics.Color.WHITE)

        // Draw left logo (bank)
        leftBitmap?.let { bitmap ->
            val y = (maxHeight - marginBottom - bitmap.height) / 2f
            canvas.drawBitmap(bitmap, 0f, y, null)
        }

        // Draw center logo (merchant)
        centerBitmap?.let { bitmap ->
            val x = (totalWidth - bitmap.width) / 2f
            val y = (maxHeight - marginBottom - bitmap.height) / 2f
            canvas.drawBitmap(bitmap, x, y, null)
        }

        // Draw right logo (onefin)
        rightBitmap?.let { bitmap ->
            val x = (totalWidth - bitmap.width).toFloat()
            val y = (maxHeight - marginBottom - bitmap.height) / 2f
            canvas.drawBitmap(bitmap, x, y, null)
        }

        return combinedBitmap
    }

    private fun createEmptyBitmap(): Bitmap {
        return createBitmap(1, 1).apply {
            eraseColor(android.graphics.Color.TRANSPARENT)
        }
    }
}