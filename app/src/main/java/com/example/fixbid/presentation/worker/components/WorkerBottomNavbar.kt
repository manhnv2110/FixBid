package com.example.fixbid.presentation.worker.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.fixbid.ui.theme.LightBlue
import com.example.fixbid.ui.theme.PrimaryBlue

@Composable
fun WorkerBottomNavbar(
    selectedIndex: Int,
    onItemSelected: (Int) -> Unit
) {
    val items = listOf(
        Triple("Tổng quan", Icons.Filled.Dashboard, Icons.Outlined.Dashboard),
        Triple("Yêu cầu", Icons.Filled.Inbox, Icons.Outlined.Inbox),
        Triple("Việc làm", Icons.Filled.Work, Icons.Outlined.Work),
        Triple("Hồ sơ", Icons.Filled.Person, Icons.Outlined.Person)
    )

    NavigationBar(
        containerColor = Color.White,
        tonalElevation = 8.dp
    ) {
        items.forEachIndexed { index, (label, activeIcon, inactiveIcon) ->
            NavigationBarItem(
                selected = selectedIndex == index,
                onClick = { onItemSelected(index) },
                icon = {
                    Icon(
                        imageVector = if (selectedIndex == index) activeIcon else inactiveIcon,
                        contentDescription = label
                    )
                },
                label = { Text(label) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = PrimaryBlue,
                    selectedTextColor = PrimaryBlue,
                    indicatorColor = LightBlue,
                    unselectedIconColor = Color.Gray,
                    unselectedTextColor = Color.Gray
                )
            )
        }
    }
}
