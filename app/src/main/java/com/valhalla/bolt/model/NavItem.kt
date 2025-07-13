package com.valhalla.bolt.model

import androidx.compose.ui.graphics.vector.ImageVector
import com.valhalla.bolt.R

data class NavItem(
    val title: String = "Flash",
    val icon: Int = R.drawable.launcher_foreground,
)

val navItems = listOf(
    NavItem("Flash", R.drawable.flash_on),
    NavItem("BackUp", R.drawable.archive),
    NavItem("Restore", R.drawable.unarchive),
)