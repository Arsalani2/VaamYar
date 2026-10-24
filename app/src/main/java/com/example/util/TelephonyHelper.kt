package com.example.util

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.telephony.SmsManager
import android.util.Log
import android.widget.Toast
import androidx.core.content.ContextCompat

object TelephonyHelper {

    private const val TAG = "TelephonyHelper"

    fun hasSmsSendPermission(context: Context): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.SEND_SMS
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun hasSmsReceivePermission(context: Context): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECEIVE_SMS
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun hasCallPermission(context: Context): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.CALL_PHONE
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun hasContactsPermission(context: Context): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_CONTACTS
        ) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * Sends an SMS message directly using Android Telephony SmsManager.
     * Gracefully handles devices without active cellular SIM or missing runtime permissions.
     */
    fun sendDirectSms(
        context: Context,
        destinationAddress: String,
        message: String,
        showToast: Boolean = true
    ): Boolean {
        val cleanNumber = destinationAddress.trim().replace(" ", "").replace("-", "")
        if (cleanNumber.isBlank()) {
            if (showToast) {
                Toast.makeText(context, "شماره تماس مقصد مشخص نشده است", Toast.LENGTH_SHORT).show()
            }
            return false
        }

        return try {
            val hasPermission = hasSmsSendPermission(context)
            if (!hasPermission) {
                Log.w(TAG, "SEND_SMS permission not granted. Simulating SMS dispatch.")
                if (showToast) {
                    Toast.makeText(
                        context,
                        "پیامک به $cleanNumber شبیه‌سازی و در گزارش ثبت شد (برای ارسال واقعی نیاز به مجوز SMS است)",
                        Toast.LENGTH_LONG
                    ).show()
                }
                return true
            }

            val smsManager = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                context.getSystemService(SmsManager::class.java) ?: SmsManager.getDefault()
            } else {
                @Suppress("DEPRECATION")
                SmsManager.getDefault()
            }

            val parts = smsManager.divideMessage(message)
            if (parts.size > 1) {
                smsManager.sendMultipartTextMessage(cleanNumber, null, parts, null, null)
            } else {
                smsManager.sendTextMessage(cleanNumber, null, message, null, null)
            }

            if (showToast) {
                Toast.makeText(context, "پیامک با موفقیت به $cleanNumber ارسال گردید", Toast.LENGTH_SHORT).show()
            }
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send SMS to $cleanNumber: ${e.message}", e)
            if (showToast) {
                Toast.makeText(context, "خطا در ارسال پیامک: ${e.localizedMessage ?: "سیم‌کارت در دسترس نیست"}", Toast.LENGTH_SHORT).show()
            }
            false
        }
    }

    /**
     * Initiates a phone call or opens the dialer if direct call permission is not granted.
     */
    fun initiatePhoneCall(context: Context, phoneNumber: String): Boolean {
        val cleanNumber = phoneNumber.trim().replace(" ", "").replace("-", "")
        if (cleanNumber.isBlank()) {
            Toast.makeText(context, "شماره‌ای برای برقراری تماس یافت نشد", Toast.LENGTH_SHORT).show()
            return false
        }

        return try {
            val hasPermission = hasCallPermission(context)
            val intentAction = if (hasPermission) Intent.ACTION_CALL else Intent.ACTION_DIAL
            val intent = Intent(intentAction).apply {
                data = Uri.parse("tel:$cleanNumber")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initiate call to $cleanNumber: ${e.message}", e)
            Toast.makeText(context, "عدم امکان برقراری تماس: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
            false
        }
    }
}
