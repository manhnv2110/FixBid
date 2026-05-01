package com.example.fixbid.data

import com.example.fixbid.model.AppNotification
import com.example.fixbid.model.NotificationType

object SampleNotifications {
    val all = listOf(
        AppNotification(
            id = "1",
            type = NotificationType.UPCOMING_TASK,
            label = "Nhiệm vụ sắp tới",
            title = "Lắp TV treo tường",
            date = "12/06/2025",
            time = "14:30"
        ),
        AppNotification(
            id = "2",
            type = NotificationType.UPCOMING_TASK,
            label = "Nhiệm vụ sắp tới",
            title = "Tỉa cây",
            date = "15/06/2025",
            time = "10:00"
        ),
        AppNotification(
            id = "3",
            type = NotificationType.INVOICE,
            label = "Hóa đơn",
            title = "Dịch vụ sửa điện nước",
            date = "10/06/2025",
            time = "09:00"
        )
    )
}