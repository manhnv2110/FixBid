package com.example.fixbid.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.fixbid.ui.components.BottomNavbar
import com.example.fixbid.ui.components.CategoryGrid
import com.example.fixbid.ui.components.PromoBanner
import com.example.fixbid.ui.theme.BackgroundGray
import com.example.fixbid.ui.theme.PrimaryBlue
import com.example.fixbid.ui.components.SeachBar
import com.example.fixbid.ui.theme.TextPrimary
import com.example.fixbid.model.Category
import com.example.fixbid.R

@Composable
fun HomeScreen() {
    var searchQuery by remember { mutableStateOf("") }
    var selectedNavIndex by remember { mutableStateOf(0) }

    val categories = listOf(
        Category(1,  "Sửa chữa nhà ở", R.drawable.home_repairs),
        Category(2, "Sửa xe", R.drawable.verhicle_services),
        Category(3, "Sửa đồ gia dụng", R.drawable.appliances_services),
        Category(4, "Dich vụ vận chuyển", R.drawable.delivery_services),
        Category(5, "Dịch vụ chăm sóc cây", R.drawable.outdoor_services)
    )

    Scaffold(
        bottomBar = {
            BottomNavbar(
                selectedIndex = selectedNavIndex,
                onItemSelected = { selectedNavIndex = it }
            )
        },
        containerColor = BackgroundGray
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(PrimaryBlue)
                    .padding(horizontal = 20.dp)
                    .padding(top = 48.dp, bottom = 24.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Outlined.LocationOn,
                            contentDescription = "Location",
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "New York",
                            color = Color.White,
                            fontWeight = FontWeight.Medium,
                            fontSize = 16.sp
                        )
                    }
                    IconButton(onClick = {}) {
                        Icon(
                            imageVector = Icons.Outlined.Notifications,
                            contentDescription = "Notifications",
                            tint = Color.White
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "I need help with",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 22.sp
                )

                Spacer(modifier = Modifier.height(12.dp))

                SeachBar(
                    query = searchQuery,
                    onQueryChange = { searchQuery = it }
                )

                Spacer(modifier = Modifier.height(8.dp))
            }
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp)
                    .padding(top = 20.dp, bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                PromoBanner()

                Column {
                    Text(
                        text = "Categories",
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        color = TextPrimary
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    CategoryGrid(
                        categories = categories,
                        onCategoryClick = { category -> {}}
                    )
                }
            }
        }
    }
}