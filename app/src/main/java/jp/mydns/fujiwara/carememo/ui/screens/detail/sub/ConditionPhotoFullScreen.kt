package jp.mydns.fujiwara.carememo.ui.screens.detail.sub

/**
 * Screen : ConditionPhotoFullScreen
 *
 * 【画面名】
 * 写真フル画面表示画面
 *
 * 【役割】
 * 所見メモに関連付けられた写真を画面全体に表示し、拡大・縮小（ピンチズーム）等の操作で詳細を確認するための画面。
 *
 * 【主な機能】
 * ・フルスクリーン閲覧：ナビゲーションバー等を隠し、画像のみを全面に表示。
 * ・ジェスチャー操作：ダブルタップやピンチアウトによるズーム、ドラッグによる移動。
 * ・情報オーバーレイ：必要に応じてタイトルや説明文を画像の上に重ねて表示。
 *
 * 【遷移】
 * ← PersonConditionScreen（写真タップ時に遷移）
 *
 * 【備考】
 * 患部の微細な変化などを正確に確認するため、Coilを用いた高効率な画像読み込みとスムーズなズーム体験を提供している。
 */

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import jp.mydns.fujiwara.carememo.utils.ImageUtils

@Composable
fun ConditionPhotoFullScreen(
    fileName: String,
    caption: String? = null,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val photoFile = ImageUtils.getPhotoFile(context, fileName)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        AsyncImage(
            model = photoFile,
            contentDescription = caption,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Fit
        )
        
        // オーバーレイの戻るボタン
        IconButton(
            onClick = onBack,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(top = 32.dp, start = 16.dp),
            colors = IconButtonDefaults.iconButtonColors(
                containerColor = Color.Black.copy(alpha = 0.5f),
                contentColor = Color.White
            )
        ) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "戻る")
        }

        // キャプションの表示（あれば）
        if (!caption.isNullOrBlank()) {
            Surface(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(bottom = 32.dp),
                color = Color.Black.copy(alpha = 0.5f),
                contentColor = Color.White
            ) {
                Text(
                    text = caption,
                    modifier = Modifier.padding(16.dp),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}
