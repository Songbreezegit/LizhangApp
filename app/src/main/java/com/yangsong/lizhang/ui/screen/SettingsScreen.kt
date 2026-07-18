package com.yangsong.lizhang.ui.screen
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.yangsong.lizhang.R
import com.yangsong.lizhang.ui.component.*
import com.yangsong.lizhang.ui.navigation.AppDestination
@Composable fun SettingsScreen(onNavigate:(AppDestination)->Unit){Scaffold(topBar={AppTopBar(stringResource(R.string.nav_settings))},bottomBar={BottomNavBar(AppDestination.Settings,onNavigate)}){p->LazyColumn(Modifier.fillMaxSize().padding(p),contentPadding=PaddingValues(horizontal=16.dp,vertical=8.dp),verticalArrangement=Arrangement.spacedBy(14.dp)){item{PageIllustration(R.drawable.page_settings_cat,Modifier.fillMaxWidth().height(155.dp))};item{SettingsGroup(stringResource(R.string.settings_data)){SettingsRow(Icons.Outlined.Backup,stringResource(R.string.settings_backup),onClick={});SettingsRow(Icons.Outlined.TableView,stringResource(R.string.settings_excel),onClick={});SettingsRow(Icons.Outlined.Description,stringResource(R.string.settings_csv),onClick={})}};item{SettingsGroup(stringResource(R.string.settings_display)){SettingsRow(Icons.Outlined.Palette,stringResource(R.string.settings_theme),stringResource(R.string.settings_follow_system),onClick={});SettingsRow(Icons.Outlined.DarkMode,stringResource(R.string.settings_dark),stringResource(R.string.settings_follow_system),onClick={},trailing={Switch(false,{})});SettingsRow(Icons.Outlined.TextFields,stringResource(R.string.settings_font),onClick={})}};item{SettingsGroup(stringResource(R.string.settings_about_group)){SettingsRow(Icons.Outlined.Info,stringResource(R.string.settings_about),stringResource(R.string.settings_version),onClick={});SettingsRow(Icons.Outlined.PrivacyTip,stringResource(R.string.settings_privacy),onClick={})}}}}}
@Composable private fun SettingsGroup(title:String,content:@Composable ColumnScope.()->Unit){Column{SectionHeader(title);Card(shape=RoundedCornerShape(24.dp),colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.surface),elevation=CardDefaults.cardElevation(2.dp)){Column(Modifier.padding(horizontal=16.dp),content=content)}}}
