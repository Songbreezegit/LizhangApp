package com.yangsong.lizhang.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yangsong.lizhang.R
import com.yangsong.lizhang.ui.navigation.AppDestination
import com.yangsong.lizhang.ui.viewmodel.HomeViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(viewModel: HomeViewModel, onNavigate: (AppDestination) -> Unit) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    Scaffold(topBar = { TopAppBar(title = { Text(stringResource(R.string.nav_home)) }) }) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(stringResource(R.string.home_contact_count, state.contactCount))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { onNavigate(AppDestination.AddGift) }) { Text(stringResource(R.string.nav_add_gift)) }
                Button(onClick = { onNavigate(AppDestination.Contacts) }) { Text(stringResource(R.string.nav_contacts)) }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { onNavigate(AppDestination.Search) }) { Text(stringResource(R.string.nav_search)) }
                Button(onClick = { onNavigate(AppDestination.Statistics) }) { Text(stringResource(R.string.nav_statistics)) }
                Button(onClick = { onNavigate(AppDestination.Settings) }) { Text(stringResource(R.string.nav_settings)) }
            }
            Text(stringResource(R.string.home_recent_record_count, state.recentRecords.size))
        }
    }
}
