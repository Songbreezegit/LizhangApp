package com.yangsong.lizhang.ui.screen

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.yangsong.lizhang.R
import com.yangsong.lizhang.ui.component.PlaceholderContent
import com.yangsong.lizhang.ui.navigation.AppDestination

@Composable
fun PlaceholderScreen(destination: AppDestination, onBack: () -> Unit) {
    val title = when (destination) {
        AppDestination.AddGift -> stringResource(R.string.nav_add_gift)
        AppDestination.Statistics -> stringResource(R.string.nav_statistics)
        AppDestination.Search -> stringResource(R.string.nav_search)
        AppDestination.Settings -> stringResource(R.string.nav_settings)
        else -> ""
    }
    PlaceholderContent(title, onBack)
}
