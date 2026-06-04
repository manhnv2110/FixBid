package com.example.fixbid.core.components

import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * One destination in [AppBottomBar].
 *
 * [badgeCount] drives a small count badge on the icon (e.g. unread chats, new
 * job requests). Zero hides the badge.
 */
data class BottomNavDestination(
    val label: String,
    val activeIcon: ImageVector,
    val inactiveIcon: ImageVector,
    val badgeCount: Int = 0
)

/**
 * The single bottom navigation bar used across the whole app.
 *
 * Previously customer and worker shells each shipped their own bar — one a
 * hand-rolled `Surface`+`Row`, the other a real `NavigationBar` — so the two
 * halves of the app looked and behaved differently. This component is the one
 * Material 3 source of truth: callers only supply their [destinations] and the
 * selected index.
 */
@Composable
fun AppBottomBar(
    destinations: List<BottomNavDestination>,
    selectedIndex: Int,
    onItemSelected: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    NavigationBar(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 3.dp
    ) {
        destinations.forEachIndexed { index, item ->
            val selected = selectedIndex == index
            NavigationBarItem(
                selected = selected,
                onClick = { onItemSelected(index) },
                icon = {
                    val icon = if (selected) item.activeIcon else item.inactiveIcon
                    if (item.badgeCount > 0) {
                        BadgedBox(
                            badge = {
                                Badge {
                                    Text(
                                        text = if (item.badgeCount > 9) "9+" else "${item.badgeCount}",
                                        fontSize = 9.sp
                                    )
                                }
                            }
                        ) {
                            Icon(imageVector = icon, contentDescription = item.label)
                        }
                    } else {
                        Icon(imageVector = icon, contentDescription = item.label)
                    }
                },
                label = {
                    Text(
                        text = item.label,
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
