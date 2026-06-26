package jp.mydns.fujiwara.carememo.ui.screens

import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import jp.mydns.fujiwara.carememo.ui.components.PersonHeaderTitle
import jp.mydns.fujiwara.carememo.utils.DateTimeUtils
import jp.mydns.fujiwara.carememo.viewmodel.PersonDetailViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConditionPhotoPreviewScreen(
    viewModel: PersonDetailViewModel,
    uri: Uri,
    personId: Int,
    conditionId: Int,
    onBack: () -> Unit,
    onSaved: () -> Unit
) {
    val context = LocalContext.current
    val isProcessing by viewModel.isProcessing.collectAsState()
    val currentPerson by viewModel.currentPerson.collectAsState()
    val isNameMaskingEnabled by viewModel.isNameMaskingEnabled.collectAsState()

    // キャプションの初期値を現在の日時に設定
    var caption by remember { 
        mutableStateOf(DateTimeUtils.getCurrentPhotoCaption())
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    PersonHeaderTitle(
                        person = currentPerson,
                        isNameMaskingEnabled = isNameMaskingEnabled,
                        defaultTitle = "写真の確認"
                    )
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier.fillMaxSize().padding(paddingValues).padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            AsyncImage(
                model = coil.request.ImageRequest.Builder(context)
                    .data(uri)
                    .crossfade(true)
                    .memoryCachePolicy(coil.request.CachePolicy.DISABLED)
                    .diskCachePolicy(coil.request.CachePolicy.DISABLED)
                    .build(),
                contentDescription = "プレビュー",
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentScale = ContentScale.Fit
            )
            
            OutlinedTextField(
                value = caption,
                onValueChange = { caption = it },
                label = { Text("キャプション") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                enabled = !isProcessing
            )

            if (isProcessing) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator()
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("画像を保存用に最適化しています...")
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    OutlinedButton(
                        onClick = onBack,
                        modifier = Modifier.weight(1f),
                        enabled = !isProcessing
                    ) {
                        Text("キャンセル")
                    }
                    Button(
                        onClick = {
                            viewModel.processAndSavePhoto(context, uri, personId, conditionId, caption)
                            onSaved()
                        },
                        modifier = Modifier.weight(1f),
                        enabled = !isProcessing
                    ) {
                        Text("保存する")
                    }
                }
            }
        }
    }
}
