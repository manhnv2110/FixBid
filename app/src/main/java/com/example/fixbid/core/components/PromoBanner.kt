package com.example.fixbid.core.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Redeem
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.fixbid.R

@Composable
fun PromoBanner(modifier: Modifier = Modifier) {
    Card(
        modifier  = modifier.fillMaxWidth().height(130.dp),
        shape     = RoundedCornerShape(16.dp),
        colors    = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalAlignment     = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(
                    text       = "GIẢM 20%",
                    fontWeight = FontWeight.ExtraBold,
                    fontSize   = 24.sp,
                    color      = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Text(
                    text       = "Cho dịch vụ đầu tiên",
                    fontWeight = FontWeight.Medium,
                    fontSize   = 14.sp,
                    color      = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Use a tinted Material vector so the icon adopts the banner's
                    // colour scheme instead of carrying a hard-coded white PNG
                    // background that broke visual continuity.
                    Box(
                        modifier = Modifier
                            .size(26.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector        = Icons.Outlined.Redeem,
                            contentDescription = "Khuyến mãi",
                            tint               = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier           = Modifier.size(16.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text       = "MÃ : FIXEN",
                        fontWeight = FontWeight.Bold,
                        fontSize   = 13.sp,
                        color      = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }

            Box(
                modifier        = Modifier.width(130.dp).fillMaxHeight(),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    painter            = painterResource(R.drawable.banner_1),
                    contentDescription = "Banner",
                    modifier           = Modifier.fillMaxHeight(),
                    contentScale       = ContentScale.FillHeight
                )
            }
        }
    }
}