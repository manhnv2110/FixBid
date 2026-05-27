package com.example.fixbid.core.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.unit.dp

@Composable
fun BottomNavbar(
    selectedIndex: Int,
    onItemSelected: (Int) -> Unit,
    chatUnreadCount: Int = 0
) {
    val items = listOf(
        Triple("Trang chủ", Icons.Filled.Home, Icons.Outlined.Home),
        Triple("Lịch sử", Icons.Filled.History, Icons.Outlined.History),
        Triple("Chat", Icons.Filled.ChatBubble, Icons.Outlined.ChatBubbleOutline),
        Triple("Hồ sơ", Icons.Filled.Person, Icons.Outlined.Person)
    )

    NavigationBar(
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 8.dp
    ) {
        items.forEachIndexed { index, (label, activeIcon, inactiveIcon) ->
            NavigationBarItem(
                selected = selectedIndex == index,
                onClick = { onItemSelected(index) },
                icon = {
                    if (index == 2 && chatUnreadCount > 0) {
                        BadgedBox(
                            badge = {
                                Badge {
                                    Text(if (chatUnreadCount > 9) "9+" else "$chatUnreadCount")
                                }
                            }
                        ) {
                            Icon(
                                imageVector = if (selectedIndex == index) activeIcon else inactiveIcon,
                                contentDescription = label
                            )
                        }
                    } else {
                        Icon(
                            imageVector = if (selectedIndex == index) activeIcon else inactiveIcon,
                            contentDescription = label
                        )
                    }
                },
                label = { Text(label) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = MaterialTheme.colorScheme.primary,
                    selectedTextColor = MaterialTheme.colorScheme.primary,
                    indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                )
            )
        }
    }
}