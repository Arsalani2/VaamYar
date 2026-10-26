package com.aistudio.vamyar.util

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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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
    private val iconCache = androidx.collection.LruCache<String, Bitmap>(30)

    fun loadSampledBitmap(path: String, reqWidth: Int = 120, reqHeight: Int = 120): Bitmap? {
        iconCache.get(path)?.let { return it }
        return try {
            val file = File(path)
            if (!file.exists()) return null
            val boundsOptions = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.absolutePath, boundsOptions)
            var inSampleSize = 1
            val maxDim = maxOf(boundsOptions.outWidth, boundsOptions.outHeight)
            while (maxDim / (inSampleSize * 2) >= maxOf(reqWidth, reqHeight)) {
                inSampleSize *= 2
            }
            val options = BitmapFactory.Options().apply { this.inSampleSize = inSampleSize }
            val bmp = BitmapFactory.decodeFile(file.absolutePath, options)
            if (bmp != null) iconCache.put(path, bmp)
            bmp
        } catch (_: Exception) {
            null
        }
    }

    /**
     * فیکس bug.md/NB-7 و P3-6: دکود دومرحله‌ای با inSampleSize برای جلوگیری از OOM
     */
    fun saveCustomIconImage(context: Context, imageUri: Uri): String? {
        return try {
            val iconsDir = File(context.filesDir, "loan_icons")
            if (!iconsDir.exists()) {
                iconsDir.mkdirs()
            }
            val fileName = "loan_icon_${System.currentTimeMillis()}.png"
            val destFile = File(iconsDir, fileName)

            // مرحله اول: فقط استخراج ابعاد تصویر
            val boundsOptions = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            context.contentResolver.openInputStream(imageUri)?.use { input ->
                BitmapFactory.decodeStream(input, null, boundsOptions)
            }

            val origWidth = boundsOptions.outWidth
            val origHeight = boundsOptions.outHeight
            if (origWidth <= 0 || origHeight <= 0) return null

            // محاسبه ضریب کاهش ابعاد (inSampleSize)
            var inSampleSize = 1
            val maxDim = maxOf(origWidth, origHeight)
            while (maxDim / (inSampleSize * 2) >= 300) {
                inSampleSize *= 2
            }

            // مرحله دوم: دکود با سایز متناسب برای جلوگیری از مصرف حافظه بالا
            val decodeOptions = BitmapFactory.Options().apply {
                this.inSampleSize = inSampleSize
            }
            val sampledBitmap = context.contentResolver.openInputStream(imageUri)?.use { input ->
                BitmapFactory.decodeStream(input, null, decodeOptions)
            } ?: return null

            val scaled = if (sampledBitmap.width > 300 || sampledBitmap.height > 300) {
                val max = maxOf(sampledBitmap.width, sampledBitmap.height)
                val scale = 300f / max
                Bitmap.createScaledBitmap(sampledBitmap, (sampledBitmap.width * scale).toInt(), (sampledBitmap.height * scale).toInt(), true)
            } else sampledBitmap

            FileOutputStream(destFile).use { out ->
                scaled.compress(Bitmap.CompressFormat.PNG, 90, out)
            }
            destFile.absolutePath
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * فیکس bug.md/NB-18: نوشتن فایل آیکونِ بازسازی‌شده از بکاپ (بایت‌های خام PNG که
     * در JSON به‌صورت base64 حمل شده بودند) در همان دایرکتوری خصوصی loan_icons —
     * مثل saveCustomIconImage ولی ورودی Uri نیست تا چرخهٔ بکاپ/restore بدون
     * ContentResolver و روی هر دستگاهی کار کند.
     */
    fun saveCustomIconBytes(context: Context, bytes: ByteArray): String? {
        return try {
            val iconsDir = File(context.filesDir, "loan_icons")
            if (!iconsDir.exists()) {
                iconsDir.mkdirs()
            }
            val destFile = File(iconsDir, "loan_icon_${System.currentTimeMillis()}.png")
            FileOutputStream(destFile).use { out -> out.write(bytes) }
            destFile.absolutePath
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun deleteCustomIconFile(filePath: String?) {
        if (!filePath.isNullOrBlank()) {
            try {
                iconCache.remove(filePath)
                val file = File(filePath)
                if (file.exists()) {
                    file.delete()
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun clearAllCustomIcons(context: Context) {
        runCatching {
            iconCache.evictAll()
            val iconsDir = File(context.filesDir, "loan_icons")
            if (iconsDir.exists()) {
                iconsDir.listFiles()?.forEach { it.delete() }
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

    // فیکس bug.md/NB-30: دکود دیسکِ آیکون قبلاً داخل remember روی نخ اصلی انجام
    // می‌شد و در اولین recomposition هر کارت، فایل از دیسک خوانده و دکود می‌شد —
    // در فهرست‌های بلند یخ‌زدن می‌کرد. حالا در LaunchedEffect روی Dispatchers.IO
    // لود می‌شود و تا آماده‌شدن، آیکون برداریِ پیش‌فرض نمایش می‌یابد.
    var customBitmap by remember(customIconPath) { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(customIconPath) {
        val path = customIconPath
        customBitmap = if (!path.isNullOrBlank()) {
            withContext(Dispatchers.IO) { LoanIconHelper.loadSampledBitmap(path) }
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
