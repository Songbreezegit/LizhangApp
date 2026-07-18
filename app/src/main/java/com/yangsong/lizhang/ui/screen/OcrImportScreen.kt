package com.yangsong.lizhang.ui.screen
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.*
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.yangsong.lizhang.R
import com.yangsong.lizhang.ui.component.*
import com.yangsong.lizhang.ui.theme.CoralContainer
@Composable fun OcrImportScreen(onBack:()->Unit){Scaffold(topBar={AppTopBar(stringResource(R.string.nav_ocr),onBack)}){p->Column(Modifier.fillMaxSize().padding(p).padding(16.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(16.dp)){PageIllustration(R.drawable.page_add_cat,Modifier.fillMaxWidth().height(210.dp));Card(shape=RoundedCornerShape(24.dp),colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.surface),elevation=CardDefaults.cardElevation(2.dp)){Column(Modifier.padding(18.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){Text(stringResource(R.string.ocr_intro_title),style=MaterialTheme.typography.titleLarge);Text(stringResource(R.string.ocr_intro_desc),color=MaterialTheme.colorScheme.onSurfaceVariant);Surface(shape=RoundedCornerShape(16.dp),color=CoralContainer){Row(Modifier.fillMaxWidth().padding(14.dp),verticalAlignment=Alignment.CenterVertically){Icon(Icons.Outlined.VerifiedUser,null);Spacer(Modifier.width(10.dp));Text(stringResource(R.string.ocr_pending_notice))}}}};Spacer(Modifier.weight(1f));PrimaryButton(stringResource(R.string.ocr_camera),{},Modifier.fillMaxWidth(),icon=Icons.Outlined.PhotoCamera);SecondaryButton(stringResource(R.string.ocr_album),{},Modifier.fillMaxWidth(),icon=Icons.Outlined.PhotoLibrary);Text(stringResource(R.string.feature_developing),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)}}}
