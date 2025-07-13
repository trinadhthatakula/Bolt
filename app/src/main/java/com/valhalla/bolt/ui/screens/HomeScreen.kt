package com.valhalla.bolt.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import com.valhalla.bolt.model.navItems

@OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    modifier: Modifier = Modifier
) {
    var selectedNavItem by remember { mutableStateOf(navItems.first()) }
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        bottomBar = {
            NavigationBar {
                navItems.forEach { nItem ->
                    NavigationBarItem(
                        selected = nItem == selectedNavItem,
                        onClick = {
                            selectedNavItem = nItem
                        },
                        icon = {
                            Icon(painterResource(nItem.icon), nItem.title)
                        },
                        label = {
                            Text(nItem.title)
                        }
                    )
                }
            }
        }) { innerPadding ->
        Column(modifier = modifier.padding(innerPadding)) {
            if (selectedNavItem == navItems[0]) {
                FlashScreen(

                )
            }
        }
    }
}

