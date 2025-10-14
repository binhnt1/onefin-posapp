package com.onefin.posapp.core.utils

import android.content.Context
import android.content.Intent
import android.widget.Toast
import com.onefin.posapp.ui.transaction.TransactionActivity
import com.onefin.posapp.ui.settlement.SettlementActivity
import com.onefin.posapp.ui.history.HistoryActivity
import com.onefin.posapp.ui.billpayment.BillPaymentActivity
import com.onefin.posapp.ui.report.ReportActivity

object NavigationHelper {

    fun navigateToTransactions(context: Context) {
        val intent = Intent(context, TransactionActivity::class.java)
        context.startActivity(intent)
    }

    fun navigateToSettlement(context: Context) {
        val intent = Intent(context, SettlementActivity::class.java)
        context.startActivity(intent)
    }

    fun navigateToHistory(context: Context) {
        val intent = Intent(context, HistoryActivity::class.java)
        context.startActivity(intent)
    }

    fun navigateToBillPayment(context: Context) {
        val intent = Intent(context, BillPaymentActivity::class.java)
        context.startActivity(intent)
    }

    fun navigateToReport(context: Context) {
        val intent = Intent(context, ReportActivity::class.java)
        context.startActivity(intent)
    }

    fun showNotifications(context: Context) {
        Toast.makeText(context, "Thông báo", Toast.LENGTH_SHORT).show()
    }
}