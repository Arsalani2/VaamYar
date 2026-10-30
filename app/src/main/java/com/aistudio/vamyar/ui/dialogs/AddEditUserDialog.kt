package com.aistudio.vamyar.ui.dialogs

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
            shape = RoundedCornerShape(28.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
            modifier = Modifier.fillMaxWidth(0.96f)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(22.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Modern Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(42.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (userToEdit == null) Icons.Default.PersonAdd else Icons.Default.Edit,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        Column {
                            Text(
                                text = if (userToEdit == null) "تعریف کاربر جدید" else "ویرایش مشخصات کاربر",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "جهت تفکیک سهم شرکا و مدیریت دقیق مطالبات",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 11.sp
                            )
                        }
                    }

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "بستن",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                // Name Input
                OutlinedTextField(
                    value = name,
                    onValueChange = {
                        name = it
                        nameError = duplicateNameError(it)
                    },
                    label = { Text("نام و نام خانوادگی یا عنوان") },
                    placeholder = { Text("مثلاً: علی رضایی یا حساب مشترک") },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Person,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                            modifier = Modifier.size(20.dp)
                        )
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    isError = nameError != null,
                    supportingText = { nameError?.let { Text(it, color = MaterialTheme.colorScheme.error, fontSize = 11.sp) } },
                    modifier = Modifier.fillMaxWidth()
                )

                // Notes / Role
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("نقش یا توضیحات (اختیاری)") },
                    placeholder = { Text("مثلاً: همسر، همکار، شریک وام مسکن") },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Description,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            modifier = Modifier.size(20.dp)
                        )
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                // Color picker
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "رنگ نشانگر پروفایل:",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
                            .padding(vertical = 10.dp, horizontal = 8.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        colorOptions.forEach { colorHex ->
                            val color = Color(colorHex)
                            val isSelected = selectedColor == colorHex
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(color)
                                    .clickable { selectedColor = colorHex }
                                    .then(
                                        if (isSelected) Modifier.border(2.5.dp, MaterialTheme.colorScheme.onSurface, CircleShape)
                                        else Modifier
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                if (isSelected) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = "انتخاب‌شده",
                                        tint = Color.White,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                // Actions
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                    ) {
                        Text("انصراف", fontWeight = FontWeight.Bold)
                    }

                    Button(
                        onClick = {
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
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .weight(1.5f)
                            .height(48.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("ذخیره کاربر", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
