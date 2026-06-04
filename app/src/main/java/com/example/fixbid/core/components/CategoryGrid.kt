package com.example.fixbid.core.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.fixbid.domain.model.ServiceCategory

@Composable
fun CategoryGrid(
    categories: List<ServiceCategory>,
    iconMapper: (ServiceCategory) -> Int,
    onCategoryClick: (ServiceCategory) -> Unit,
    modifier: Modifier = Modifier
) {
    val rows = categories.chunked(3)
    Column(
        modifier              = modifier.fillMaxWidth(),
        verticalArrangement   = Arrangement.spacedBy(12.dp)
    ) {
        rows.forEach { rowItems ->
            Row(
                modifier              = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                rowItems.forEach { category ->
                    CategoryItem(
                        category    = category,
                        iconRes     = iconMapper(category),
                        onClick     = { onCategoryClick(category) },
                        modifier    = Modifier.weight(1f)
                    )
                }
                // Lấp khoảng trống nếu hàng không đủ 3 item
                repeat(3 - rowItems.size) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun CategoryItem(
    category: ServiceCategory,
    iconRes: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier  = modifier.aspectRatio(1f).clickable { onClick() },
        shape     = RoundedCornerShape(12.dp),
        colors    = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier              = Modifier.fillMaxSize().padding(8.dp),
            horizontalAlignment   = Alignment.CenterHorizontally,
            verticalArrangement   = Arrangement.Center
        ) {
            Image(
                painter            = painterResource(iconRes),
                contentDescription = category.displayName,
                modifier           = Modifier.size(48.dp),
                contentScale       = ContentScale.Fit
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text      = category.displayName,
                fontSize  = 11.sp,
                textAlign = TextAlign.Center,
                color     = MaterialTheme.colorScheme.onSurface,
                maxLines  = 2
            )
        }
    }
}