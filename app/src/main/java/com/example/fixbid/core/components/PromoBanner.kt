package com.example.fixbid.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.fixbid.ui.theme.PrimaryBlue
import com.example.fixbid.ui.theme.White
import com.example.fixbid.R

@Composable
fun PromoBanner(modifier: Modifier = Modifier) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .height(130.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = White),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(
                    text = "20% OFF",
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 24.sp,
                    color = PrimaryBlue
                )
                Text(
                    text = "For this Seasonal",
                    fontWeight = FontWeight.Medium,
                    fontSize = 14.sp,
                    color = PrimaryBlue
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Image(
                        painter = painterResource(R.drawable.gift),
                        contentDescription = "Sale Gift",
                        modifier = Modifier
                            .size(32.dp)
                            .padding(end = 6.dp)
                    )
                    Text(
                        text = "CODE : FIXEN",
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = PrimaryBlue
                    )
                }
            }

            Box(
                modifier = Modifier
                    .width(130.dp)
                    .fillMaxHeight()
                    .background(White),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    painter = painterResource(R.drawable.banner_1),
                    contentDescription = "Banner",
                    modifier = Modifier.fillMaxHeight(),
                    contentScale = ContentScale.FillHeight
                )
            }
        }
    }
}