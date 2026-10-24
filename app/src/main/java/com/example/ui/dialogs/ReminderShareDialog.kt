package com.example.ui.dialogs

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.example.model.DebtorShare
import com.example.util.NumberFormatter
import com.example.util.ShamsiDateHelper

@Composable
fun ReminderShareDialog(
    debtorName: String,
    shares: List<DebtorShare>,
    senderName: String = "",
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val currentShamsi = remember { ShamsiDateHelper.getCurrentShamsiDate() }
    val totalAmount = remember(shares) { shares.sumOf { it.shareAmount } }

    val detailsText = shares.joinToString("\n") { share ->
        "• ${share.loanTitle}: ${NumberFormatter.formatPrice(share.shareAmount)} (موعد روز ${NumberFormatter.toPersianDigits(share.dueDay)} ماه)"
    }

    val defaultMessage = remember(debtorName, totalAmount, detailsText, senderName) {
        val signOff = if (senderName.isNotBlank()) "\n($senderName)" else ""
        """
سلام ${debtorName} عزیز،
امیدوارم خوب باشی.
یادآوری مبلغ سهم اقساط وام‌های این ماه (${currentShamsi.monthName}):

$detailsText

جمع کل سهم این ماه: ${NumberFormatter.formatPrice(totalAmount)}

لطفاً در زمان مقرر به حساب واریز بفرمایید. ممنون از شما!$signOff
        """.trimIndent()
    }

    var messageText by remember { mutableStateOf(defaultMessage) }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "ارسال یادآوری به $debtorName",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(imageVector = Icons.Default.Close, contentDescription = "بستن")
                    }
                }

                Text(
                    text = "متن پیام یادآوری آماده ارسال در پیامک، ایتا یا واتساپ:",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                OutlinedTextField(
                    value = messageText,
                    onValueChange = { messageText = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp),
                    shape = RoundedCornerShape(12.dp)
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            val clip = ClipData.newPlainText("یادآوری قسط", messageText)
                            clipboard.setPrimaryClip(clip)
                            Toast.makeText(context, "متن یادآوری کپی شد", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(imageVector = Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("کپی متن")
                    }

                    Button(
                        onClick = {
                            val sendIntent = Intent().apply {
                                action = Intent.ACTION_SEND
                                putExtra(Intent.EXTRA_TEXT, messageText)
                                type = "text/plain"
                            }
                            val shareIntent = Intent.createChooser(sendIntent, "ارسال پیام یادآوری با...")
                            context.startActivity(shareIntent)
                            onDismiss()
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(imageVector = Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("اشتراک‌گذاری")
                    }
                }
            }
        }
    }
}
