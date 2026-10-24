package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.SmartFinancialTip
import com.example.ui.TipType

@Composable
fun SmartFinancialAdvisorCard(
    tips: List<SmartFinancialTip>,
    onActionClick: ((SmartFinancialTip) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    if (tips.isEmpty()) return

    var expanded by remember { mutableStateOf(false) }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.AutoAwesome,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = "دستیار و پیشنهادات هوشمند مالی",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Text(
                        text = if (expanded) "بستن ▲" else "${tips.size} پیشنهاد ▼",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            // Always show the top most important tip
            val topTip = tips.first()
            SingleTipItem(
                tip = topTip,
                onActionClick = onActionClick
            )

            // Other tips shown when expanded
            AnimatedVisibility(visible = expanded) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    tips.drop(1).forEach { tip ->
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                        SingleTipItem(
                            tip = tip,
                            onActionClick = onActionClick
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SingleTipItem(
    tip: SmartFinancialTip,
    onActionClick: ((SmartFinancialTip) -> Unit)? = null
) {
    val icon = when (tip.type) {
        TipType.URGENT_DUE -> Icons.Default.Alarm
        TipType.COLLECTION_REMINDER -> Icons.Default.Groups
        TipType.SNOWBALL_PAYOFF -> Icons.Default.TrendingUp
        TipType.LIQUIDITY_PLAN -> Icons.Default.AccountBalanceWallet
        TipType.BUDGET_ADVICE -> Icons.Default.Lightbulb
    }

    val iconColor = when (tip.type) {
        TipType.URGENT_DUE -> MaterialTheme.colorScheme.error
        TipType.COLLECTION_REMINDER -> MaterialTheme.colorScheme.secondary
        TipType.SNOWBALL_PAYOFF -> MaterialTheme.colorScheme.primary
        TipType.LIQUIDITY_PLAN -> MaterialTheme.colorScheme.tertiary
        TipType.BUDGET_ADVICE -> MaterialTheme.colorScheme.secondary
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.Top
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(iconColor.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconColor,
                modifier = Modifier.size(18.dp)
            )
        }

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = tip.title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = tip.description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 18.sp
            )
        }

        if (tip.actionText != null && onActionClick != null) {
            TextButton(
                onClick = { onActionClick(tip) },
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
            ) {
                Text(
                    text = tip.actionText,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
