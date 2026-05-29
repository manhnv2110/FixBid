package com.example.fixbid.presentation.worker.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private data class WorkerNavItem(
    val label: String,
    val activeIcon: ImageVector,
    val inactiveIcon: ImageVector
)

/**
 * Bottom navbar cho worker — 4 tab theo workflow thực tế:
 * Trang chủ (dashboard) · Tìm việc (yêu cầu mở) · Việc làm · Hồ sơ.
 *
 * [openRequestCount] hiển thị badge số yêu cầu mới phù hợp kỹ năng trên tab "Tìm việc".
 */
@Composable
fun WorkerBottomNavbar(
    selectedIndex: Int,
    onItemSelected: (Int) -> Unit,
    openRequestCount: Int = 0
) {
    val items = listOf(
        WorkerNavItem("Trang chủ", Icons.Filled.Dashboard, Icons.Outlined.Dashboard),
        WorkerNavItem("Tìm việc", Icons.Filled.Search, Icons.Outlined.Search),
        WorkerNavItem("Việc làm", Icons.Filled.Work, Icons.Outlined.WorkOutline),
        WorkerNavItem("Hồ sơ", Icons.Filled.Person, Icons.Outlined.Person)
    )

    NavigationBar(
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 8.dp
    ) {
        items.forEachIndexed { index, item ->
            val selected = selectedIndex == index
            NavigationBarItem(
                selected = selected,
                onClick = { onItemSelected(index) },
                icon = {
                    if (index == 1 && openRequestCount > 0) {
                        BadgedBox(
                            badge = {
                                Badge {
                                    Text(
                                        text = if (openRequestCount > 9) "9+" else "$openRequestCount",
                                        fontSize = 9.sp
                                    )
                                }
                            }
                        ) {
                            Icon(
                                imageVector = if (selected) item.activeIcon else item.inactiveIcon,
                                contentDescription = item.label
                            )
                        }
                    } else {
                        Icon(
                            imageVector = if (selected) item.activeIcon else item.inactiveIcon,
                            contentDescription = item.label
                        )
                    }
                },
                label = {
                    Text(
                        item.label,
                        fontSize = 11.sp,
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                        maxLines = 1
                    )
                },
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
