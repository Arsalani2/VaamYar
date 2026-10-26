package com.aistudio.vamyar.ui.dialogs

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.aistudio.vamyar.model.UserProfile
import com.aistudio.vamyar.util.PersianNameNormalizer

@Composable
fun AddEditUserDialog(
    userToEdit: UserProfile? = null,
    existingUsers: List<UserProfile> = emptyList(),
    onDismiss: () -> Unit,
    onSave: (UserProfile) -> Unit
) {
    var name by remember { mutableStateOf(userToEdit?.name ?: "") }
    var notes by remember { mutableStateOf(userToEdit?.notes ?: "") }
    var selectedColor by remember { mutableLongStateOf(userToEdit?.avatarColorHex ?: 0xFF1976D2L) }
    var nameError by remember { mutableStateOf<String?>(null) }

    // فیکس bug.md/NB-20 — نام تکراری رد می‌شود؛ مقایسه با namesMatch انجام می‌شود تا
    // «علی» و «علي» (ي/ك عربی) و فاصله‌های زائد هم تکراری شمرده شوند. هنگام ویرایش،
    // خودِ کاربر از بررسی کنار گذاشته می‌شود.
    val editing = userToEdit
    fun duplicateNameError(candidate: String): String? {
        val trimmed = candidate.trim()
        if (trimmed.isEmpty()) return null
        val selfKey = editing?.let { if (it.publicId.isNotBlank()) it.publicId else "id-${it.id}" }
        val duplicate = existingUsers.any { existing ->
            val existingKey = if (existing.publicId.isNotBlank()) existing.publicId else "id-${existing.id}"
            existingKey != selfKey && PersianNameNormalizer.namesMatch(existing.name, trimmed)
        }
        return if (duplicate) "کاربری با همین نام از قبل وجود دارد؛ لطفاً نام دیگری انتخاب کنید." else null
    }

    // فیکس bug.md/NB-27: برچسب‌های عمومی رنگ‌ها جایگزین نام‌های شخصی شد
    val colorOptions = listOf(
        0xFFD4AF37L, // طلایی
        0xFFC2185BL, // یاقوتی
        0xFFF57C00L, // نارنجی
        0xFF0288D1L, // آبی
        0xFF388E3CL, // سبز
        0xFF7B1FA2L, // بنفش
        0xFF455A64L  // خاکستری
    )

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            modifier = Modifier.fillMaxWidth(0.95f)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (userToEdit == null) "تعریف کاربر جدید" else "ویرایش مشخصات کاربر",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(imageVector = Icons.Default.Close, contentDescription = "بستن")
                    }
                }

                HorizontalDivider()

                // Name
                OutlinedTextField(
                    value = name,
                    onValueChange = {
                        name = it
                        nameError = duplicateNameError(it)
                    },
                    label = { Text("نام کاربر") },
                    placeholder = { Text("مثلاً: علی یا مریم") },
                    singleLine = true,
                    isError = nameError != null,
                    supportingText = { nameError?.let { Text(it, color = MaterialTheme.colorScheme.error) } },
                    modifier = Modifier.fillMaxWidth()
                )

                // Notes / Description
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("توضیحات یا نقش کاربر") },
                    placeholder = { Text("مثلاً: شریک وام‌های شخصی") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                // Color picker
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("رنگ نشانگر پروفایل:", style = MaterialTheme.typography.bodySmall)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        colorOptions.forEach { colorHex ->
                            val color = Color(colorHex)
                            val isSelected = selectedColor == colorHex
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(CircleShape)
                                    .background(color)
                                    .clickable { selectedColor = colorHex }
                                    .then(
                                        if (isSelected) Modifier.border(3.dp, MaterialTheme.colorScheme.onSurface, CircleShape)
                                        else Modifier
                                    )
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                // Actions
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f)) {
                        Text("انصراف")
                    }

                    Button(
                        onClick = {
                            // فیکس bug.md/NB-20: ذخیره فقط با نام غیرخالی و غیرتکراری
                            val duplicateError = duplicateNameError(name)
                            nameError = duplicateError
                            if (name.isNotBlank() && duplicateError == null) {
                                onSave(
                                    (userToEdit ?: UserProfile(name = name.trim())).copy(
                                        name = name.trim(),
                                        notes = notes.trim(),
                                        avatarColorHex = selectedColor
                                    )
                                )
                            }
                        },
                        enabled = name.isNotBlank(),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("ذخیره کاربر")
                    }
                }
            }
        }
    }
}
