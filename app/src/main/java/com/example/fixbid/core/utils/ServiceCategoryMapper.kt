// core/utils/ServiceCategoryMapper.kt
package com.example.fixbid.core.utils

import com.example.fixbid.R
import com.example.fixbid.domain.model.ServiceCategory

object ServiceCategoryMapper {

    fun getIconRes(category: ServiceCategory, isDark: Boolean = false): Int = when (category) {
        ServiceCategory.PLUMBING        -> if (isDark) R.drawable.ic_plumbing_dark else R.drawable.ic_plumbing
        ServiceCategory.ELECTRICAL      -> if (isDark) R.drawable.ic_electrical_dark else R.drawable.ic_electrical
        ServiceCategory.CARPENTRY       -> if (isDark) R.drawable.ic_carpentry_dark else R.drawable.ic_carpentry
        ServiceCategory.AIR_CONDITIONING -> if (isDark) R.drawable.ic_air_conditioning_dark else R.drawable.ic_air_conditioning
        ServiceCategory.APPLIANCE_REPAIR -> if (isDark) R.drawable.ic_appliances_dark else R.drawable.ic_appliances
        ServiceCategory.CLEANING        -> if (isDark) R.drawable.ic_cleaning_dark else R.drawable.ic_cleaning
        ServiceCategory.LOCKSMITH       -> if (isDark) R.drawable.ic_lock_dark else R.drawable.ic_lock
        ServiceCategory.ROOFING         -> if (isDark) R.drawable.ic_roof_dark else R.drawable.ic_roof
        ServiceCategory.OTHER           -> if (isDark) R.drawable.ic_other_dark else R.drawable.ic_other
    }

    val homeCategories = listOf(
        ServiceCategory.PLUMBING,
        ServiceCategory.ELECTRICAL,
        ServiceCategory.CARPENTRY,
        ServiceCategory.AIR_CONDITIONING,
        ServiceCategory.APPLIANCE_REPAIR,
        ServiceCategory.CLEANING,
        ServiceCategory.LOCKSMITH,
        ServiceCategory.ROOFING,
        ServiceCategory.OTHER
    )
}