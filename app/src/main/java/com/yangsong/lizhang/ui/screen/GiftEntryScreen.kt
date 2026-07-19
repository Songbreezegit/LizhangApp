package com.yangsong.lizhang.ui.screen

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.yangsong.lizhang.R
import com.yangsong.lizhang.ui.component.*
import com.yangsong.lizhang.ui.navigation.AppDestination
import com.yangsong.lizhang.ui.theme.*

@Composable
fun GiftEntryScreen(onManual: () -> Unit, onOcr: () -> Unit, onNavigate: (AppDestination) -> Unit) {
    Scaffold(
        topBar = { AppTopBar(stringResource(R.string.nav_add_gift)) },
        bottomBar = { BottomNavBar(AppDestination.AddGift, onNavigate) },
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item { PageIllustration(R.drawable.page_add_cat, Modifier.fillMaxWidth().height(190.dp)) }
            item {
                Text(stringResource(R.string.entry_choose_title), style = MaterialTheme.typography.titleLarge)
                Text(stringResource(R.string.entry_choose_desc), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            item {
                GiftEntryOption(
                    title = stringResource(R.string.entry_manual_title),
                    description = stringResource(R.string.entry_manual_desc),
                    icon = Icons.Outlined.EditNote,
                    iconBackground = CoralContainer,
                    iconTint = CoralStrong,
                    onClick = onManual,
                )
            }
            item {
                GiftEntryOption(
                    title = stringResource(R.string.entry_ocr_title),
                    description = stringResource(R.string.entry_ocr_desc),
                    icon = Icons.Outlined.PhotoCamera,
                    iconBackground = MintContainer,
                    iconTint = MintPrimary,
                    onClick = onOcr,
                )
            }
        }
    }
}
