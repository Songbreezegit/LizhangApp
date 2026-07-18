package com.yangsong.lizhang.ui.screen

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedTextField
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
import com.yangsong.lizhang.core.util.CurrencyFormatter
import com.yangsong.lizhang.ui.viewmodel.ContactsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContactsScreen(viewModel: ContactsViewModel, onContactClick: (Long) -> Unit) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    Scaffold(topBar = { TopAppBar(title = { Text(stringResource(R.string.nav_contacts)) }) }) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
            OutlinedTextField(
                value = state.query,
                onValueChange = viewModel::updateQuery,
                label = { Text(stringResource(R.string.nav_search)) },
                modifier = Modifier.fillMaxWidth(),
            )
            LazyColumn {
                items(state.contacts, key = { it.contact.id }) { summary ->
                    Card(
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                            .clickable { onContactClick(summary.contact.id) },
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(summary.contact.name)
                            Text(stringResource(R.string.contact_net_amount, CurrencyFormatter.formatCents(summary.netInCents)))
                        }
                    }
                }
            }
        }
    }
}
