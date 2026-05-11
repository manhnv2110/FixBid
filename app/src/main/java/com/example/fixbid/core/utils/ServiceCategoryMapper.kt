// core/utils/ServiceCategoryMapper.kt
package com.example.fixbid.core.utils

import com.example.fixbid.R
import com.example.fixbid.domain.model.ServiceCategory

object ServiceCategoryMapper {

    fun getIconRes(category: ServiceCategory): Int = when (category) {
        ServiceCategory.PLUMBING        -> R.drawable.ic_plumbing
        ServiceCategory.ELECTRICAL      -> R.drawable.ic_electrical
        ServiceCategory.CARPENTRY       -> R.drawable.ic_carpentry
        ServiceCategory.AIR_CONDITIONING -> R.drawable.ic_air_conditioning
        ServiceCategory.APPLIANCE_REPAIR -> R.drawable.ic_appliances
        ServiceCategory.CLEANING        -> R.drawable.ic_cleaning
        ServiceCategory.LOCKSMITH       -> R.drawable.ic_lock
        ServiceCategory.ROOFING         -> R.drawable.ic_roof
        ServiceCategory.OTHER           -> R.drawable.ic_other
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