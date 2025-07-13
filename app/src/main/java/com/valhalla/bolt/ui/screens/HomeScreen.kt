package com.valhalla.bolt.ui.screens

import android.util.Log
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import com.valhalla.bolt.model.navItems
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    modifier: Modifier = Modifier
) {
    var selectedNavItemIndex by remember { mutableIntStateOf(0) }

    val pagerState = rememberPagerState { navItems.size }
    val scope = rememberCoroutineScope()
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.currentPage }.collect { page ->
            selectedNavItemIndex = page
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        bottomBar = {
            NavigationBar {
                navItems.forEachIndexed { index, nItem ->
                    NavigationBarItem(
                        selected = nItem == navItems[selectedNavItemIndex],
                        onClick = {
                            selectedNavItemIndex = index
                            scope.launch {
                                pagerState.animateScrollToPage(index)
                            }
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
        }
    ) { innerPadding ->
        Column(modifier = modifier.padding(innerPadding)) {
            HorizontalPager(pagerState) { page ->
                when(navItems[page]){
                    navItems[0] -> {
                        FlashScreen(Modifier.fillMaxSize())
                    }
                    navItems[1] -> {
                        BackUpScreen(Modifier.fillMaxSize())
                    }
                    navItems[2] -> {
                        RestoreScreen(Modifier.fillMaxSize())
                    }
                    else -> {}
                }
            }
        }

    }


}

