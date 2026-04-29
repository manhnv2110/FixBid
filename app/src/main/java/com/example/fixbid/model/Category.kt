package com.example.fixbid.model

import androidx.annotation.DrawableRes

data class Category(
    val id: Int,
    val name: String,
    @DrawableRes val iconRes: Int
)