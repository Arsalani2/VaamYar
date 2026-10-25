package com.example.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream

data class LoanIconItem(
    val id: String,
    val titleFa: String,
    val icon: ImageVector
)

object LoanIconHelper {

    val PRESET_ICONS = listOf(
        LoanIconItem("account_balance", "بانک و وام", Icons.Default.AccountBalance),
        LoanIconItem("directions_car", "خودرو و نقلیه", Icons.Default.DirectionsCar),
        LoanIconItem("home", "مسکن و رهن", Icons.Default.Home),
        LoanIconItem("credit_card", "کارت و قرض", Icons.Default.CreditCard),
        LoanIconItem("payments", "وجه نقد و بدهی", Icons.Default.Payments),
        LoanIconItem("favorite", "ازدواج و شخصی", Icons.Default.Favorite),
        LoanIconItem("school", "تحصیلی و رشد", Icons.Default.School),
        LoanIconItem("phone_android", "کالا و دیجیتال", Icons.Default.PhoneAndroid),
        LoanIconItem("shopping_bag", "خرید اقساطی", Icons.Default.ShoppingBag),
        LoanIconItem("store", "کسب‌وکار", Icons.Default.Store),
        LoanIconItem("savings", "صندوق و پس‌انداز", Icons.Default.Savings),
        LoanIconItem("medical_services", "درمان و سلامت", Icons.Default.MedicalServices),
        LoanIconItem("flight", "سفر و زیارت", Icons.Default.Flight),
        LoanIconItem("diamond", "طلا و سرمایه", Icons.Default.Diamond),
        LoanIconItem("build", "تعمیرات و مسکن", Icons.Default.Build),
        LoanIconItem("work", "شغلی و اداری", Icons.Default.Work),
        LoanIconItem("handshake", "شراکت دوستانه", Icons.Default.Handshake)
    )

    fun getIconVector(id: String): ImageVector {
        return PRESET_ICONS.find { it.id == id }?.icon ?: Icons.Default.AccountBalance
    }

    /**
     * Copies a user-selected image Uri into the app's internal private storage directory
     * and returns the absolute local file path for reliable offline persistence.
     */
    fun saveCustomIconImage(context: Context, imageUri: Uri): String? {
        return try {
            val iconsDir = File(context.filesDir, "loan_icons")
            if (!iconsDir.exists()) {
                iconsDir.mkdirs()
            }
            val fileName = "loan_icon_${System.currentTimeMillis()}.png"
            val destFile = File(iconsDir, fileName)

            val inputStream: InputStream? = context.contentResolver.openInputStream(imageUri)
            val bitmap = BitmapFactory.decodeStream(inputStream)
            inputStream?.close()

            if (bitmap != null) {
                // Resize to max 300x300 for optimal memory and storage efficiency
                val scaled = if (bitmap.width > 300 || bitmap.height > 300) {
                    val max = maxOf(bitmap.width, bitmap.height)
                    val scale = 300f / max
                    Bitmap.createScaledBitmap(bitmap, (bitmap.width * scale).toInt(), (bitmap.height * scale).toInt(), true)
                } else bitmap

                FileOutputStream(destFile).use { out ->
                    scaled.compress(Bitmap.CompressFormat.PNG, 90, out)
                }
                destFile.absolutePath
            } else null
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun deleteCustomIconFile(filePath: String?) {
        if (!filePath.isNullOrBlank()) {
            try {
                val file = File(filePath)
                if (file.exists()) {
                    file.delete()
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}

@Composable
fun LoanIconDisplay(
    iconName: String,
    iconShape: String, // "ROUNDED_SQUARE" or "CIRCLE"
    customIconPath: String?,
    tintColor: Color,
    size: Dp = 40.dp,
    iconSize: Dp = 22.dp,
    modifier: Modifier = Modifier
) {
    val shape = if (iconShape == "CIRCLE") CircleShape else RoundedCornerShape(12.dp)

    val customBitmap = remember(customIconPath) {
        if (!customIconPath.isNullOrBlank()) {
            val file = File(customIconPath)
            if (file.exists()) {
                BitmapFactory.decodeFile(file.absolutePath)
            } else null
        } else null
    }

    Box(
        modifier = modifier
            .size(size)
            .clip(shape)
            .background(tintColor.copy(alpha = 0.16f)),
        contentAlignment = Alignment.Center
    ) {
        if (customBitmap != null) {
            Image(
                bitmap = customBitmap.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(size)
                    .clip(shape)
            )
        } else {
            val vector = LoanIconHelper.getIconVector(iconName)
            Icon(
                imageVector = vector,
                contentDescription = null,
                tint = tintColor,
                modifier = Modifier.size(iconSize)
            )
        }
    }
}
