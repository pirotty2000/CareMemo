package jp.mydns.fujiwara.carememo

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.biometric.BiometricPrompt
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import android.net.Uri
import jp.mydns.fujiwara.carememo.data.Category
import jp.mydns.fujiwara.carememo.ui.screens.*
import jp.mydns.fujiwara.carememo.ui.theme.CareMemoTheme
import jp.mydns.fujiwara.carememo.utils.PdfExporter
import jp.mydns.fujiwara.carememo.viewmodel.PersonDetailViewModel
import jp.mydns.fujiwara.carememo.viewmodel.PersonListViewModel
import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

class MainActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // アプリ起動時に古いPDFキャッシュを掃除
        PdfExporter.clearOldExports(this)

        enableEdgeToEdge()
        setContent {
            CareMemoTheme {
                CareMemoApp(this)
            }
        }
    }
}

@Composable
fun CareMemoApp(activity: FragmentActivity) {
    val navController = rememberNavController()
    val context = LocalContext.current
    val application = context.applicationContext as CareMemoApplication
    val repository = application.repository
    val userSettingsRepository = application.userSettingsRepository

    val isBiometricEnabled by userSettingsRepository.isBiometricEnabled.collectAsState(initial = null)
    var isAuthenticated by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(isBiometricEnabled) {
        if (isBiometricEnabled == true && !isAuthenticated) {
            val executor = ContextCompat.getMainExecutor(activity)
            val biometricPrompt = BiometricPrompt(activity, executor,
                object : BiometricPrompt.AuthenticationCallback() {
                    override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                        super.onAuthenticationSucceeded(result)
                        isAuthenticated = true
                    }

                    override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                        super.onAuthenticationError(errorCode, errString)
                        // キャンセルされた場合やエラーの場合はアプリを終了する（セキュリティのため）
                        if (errorCode == BiometricPrompt.ERROR_USER_CANCELED || 
                            errorCode == BiometricPrompt.ERROR_NEGATIVE_BUTTON ||
                            errorCode == BiometricPrompt.ERROR_LOCKOUT ||
                            errorCode == BiometricPrompt.ERROR_LOCKOUT_PERMANENT) {
                            activity.finish()
                        }
                    }
                })

            val promptInfo = BiometricPrompt.PromptInfo.Builder()
                .setTitle("生体認証")
                .setSubtitle("指紋または顔認証でロックを解除してください")
                .setNegativeButtonText("終了")
                .build()

            biometricPrompt.authenticate(promptInfo)
        } else if (isBiometricEnabled == false) {
            isAuthenticated = true
        }
    }

    // 認証済み、あるいは設定がオフの場合のみコンテンツを表示
    if (isAuthenticated || isBiometricEnabled == false) {
        NavHost(navController = navController, startDestination = "main") {
        composable("main") {
            val listViewModel: PersonListViewModel = viewModel(
                factory = PersonListViewModel.Factory(repository, userSettingsRepository)
            )
            MainScreen(
                viewModel = listViewModel,
                onNavigateToDetail = { personId, category ->
                    val query = listViewModel.searchQuery.value
                    val encodedQuery = if (query.isNotBlank()) URLEncoder.encode(query, StandardCharsets.UTF_8.toString()) else ""
                    navController.navigate("detail/$personId/${category.name}?query=$encodedQuery")
                },
                onNavigateToSettings = {
                    navController.navigate("settings")
                }
            )
        }
        composable("restore") {
            val listViewModel: PersonListViewModel = viewModel(
                factory = PersonListViewModel.Factory(repository, userSettingsRepository)
            )
            DeletedUserListScreen(
                viewModel = listViewModel,
                onBack = { navController.popBackStack() }
            )
        }
        composable("settings") {
            val listViewModel: PersonListViewModel = viewModel(
                factory = PersonListViewModel.Factory(repository, userSettingsRepository)
            )
            SettingsScreen(
                viewModel = listViewModel,
                onNavigateToRestore = {
                    navController.navigate("restore")
                },
                onBack = { navController.popBackStack() }
            )
        }
        composable(
            route = "detail/{personId}/{categoryName}?query={query}",
            arguments = listOf(
                navArgument("personId") { type = NavType.IntType },
                navArgument("categoryName") { type = NavType.StringType },
                navArgument("query") { 
                    type = NavType.StringType
                    nullable = true
                    defaultValue = ""
                }
            )
        ) { backStackEntry ->
            val personId = backStackEntry.arguments?.getInt("personId") ?: 0
            val categoryName = backStackEntry.arguments?.getString("categoryName") ?: Category.CONDITION_AT_VISIT.name
            val category = Category.valueOf(categoryName)
            val initialQuery = backStackEntry.arguments?.getString("query")?.let { 
                if (it.isNotBlank()) URLDecoder.decode(it, StandardCharsets.UTF_8.toString()) else ""
            } ?: ""
            
            val detailViewModel: PersonDetailViewModel = viewModel(
                factory = PersonDetailViewModel.Factory(repository, userSettingsRepository)
            )
            
            // 検索キーワードの初期セット
            LaunchedEffect(initialQuery) {
                if (initialQuery.isNotBlank()) {
                    detailViewModel.updateSearchQuery(initialQuery)
                }
            }

            UnifiedRecordScreen(
                viewModel = detailViewModel,
                initialCategoryType = category,
                personId = personId,
                onBack = { navController.popBackStack() },
                onNavigateToConditionDetail = { pId, cId ->
                    navController.navigate("conditionDetail/$pId/$cId")
                },
                onNavigateToHealthRecordDetail = { pId, cat, rId ->
                    navController.navigate("healthRecordDetail/$pId/${cat.name}/$rId")
                },
                onNavigateToGraphExpansion = { pId, cat, index ->
                    navController.navigate("graphExpansion/$pId/${cat.name}/$index")
                }
            )
        }
        composable(
            route = "conditionDetail/{personId}/{conditionId}",
            arguments = listOf(
                navArgument("personId") { type = NavType.IntType },
                navArgument("conditionId") { type = NavType.IntType }
            )
        ) { backStackEntry ->
            val personId = backStackEntry.arguments?.getInt("personId") ?: 0
            val conditionId = backStackEntry.arguments?.getInt("conditionId") ?: 0
            val detailViewModel: PersonDetailViewModel = viewModel(
                factory = PersonDetailViewModel.Factory(repository, userSettingsRepository)
            )
            ConditionDetailScreen(
                viewModel = detailViewModel,
                personId = personId,
                conditionId = conditionId,
                onBack = { navController.popBackStack() },
                onNavigateToPhotoPreview = { uri, pId, cId ->
                    val encodedUri = Uri.encode(uri.toString())
                    navController.navigate("photoPreview/$encodedUri/$pId/$cId")
                },
                onNavigateToFullScreen = { fileName ->
                    navController.navigate("photoFull/$fileName")
                }
            )
        }
        composable(
            route = "healthRecordDetail/{personId}/{categoryName}/{recordId}",
            arguments = listOf(
                navArgument("personId") { type = NavType.IntType },
                navArgument("categoryName") { type = NavType.StringType },
                navArgument("recordId") { type = NavType.IntType }
            )
        ) { backStackEntry ->
            val personId = backStackEntry.arguments?.getInt("personId") ?: 0
            val categoryName = backStackEntry.arguments?.getString("categoryName") ?: ""
            val category = Category.valueOf(categoryName)
            val recordId = backStackEntry.arguments?.getInt("recordId") ?: 0
            val detailViewModel: PersonDetailViewModel = viewModel(
                factory = PersonDetailViewModel.Factory(repository, userSettingsRepository)
            )
            HealthRecordDetailScreen(
                viewModel = detailViewModel,
                personId = personId,
                category = category,
                recordId = recordId,
                onBack = { navController.popBackStack() }
            )
        }
        composable(
            route = "graphExpansion/{personId}/{categoryName}/{initialIndex}",
            arguments = listOf(
                navArgument("personId") { type = NavType.IntType },
                navArgument("categoryName") { type = NavType.StringType },
                navArgument("initialIndex") { type = NavType.IntType }
            )
        ) { backStackEntry ->
            val personId = backStackEntry.arguments?.getInt("personId") ?: 0
            val categoryName = backStackEntry.arguments?.getString("categoryName") ?: ""
            val category = Category.valueOf(categoryName)
            val initialIndex = backStackEntry.arguments?.getInt("initialIndex") ?: 0
            val detailViewModel: PersonDetailViewModel = viewModel(
                factory = PersonDetailViewModel.Factory(repository, userSettingsRepository)
            )
            GraphExpansionScreen(
                viewModel = detailViewModel,
                personId = personId,
                category = category,
                initialGraphIndex = initialIndex,
                onBack = { navController.popBackStack() }
            )
        }
        composable(
            route = "photoPreview/{uri}/{personId}/{conditionId}",
            arguments = listOf(
                navArgument("uri") { type = NavType.StringType },
                navArgument("personId") { type = NavType.IntType },
                navArgument("conditionId") { type = NavType.IntType }
            )
        ) { backStackEntry ->
            val uri = Uri.parse(Uri.decode(backStackEntry.arguments?.getString("uri") ?: ""))
            val personId = backStackEntry.arguments?.getInt("personId") ?: 0
            val conditionId = backStackEntry.arguments?.getInt("conditionId") ?: 0
            val detailViewModel: PersonDetailViewModel = viewModel(
                factory = PersonDetailViewModel.Factory(repository, userSettingsRepository)
            )
            ConditionPhotoPreviewScreen(
                viewModel = detailViewModel,
                uri = uri,
                personId = personId,
                conditionId = conditionId,
                onBack = { navController.popBackStack() },
                onSaved = { navController.popBackStack() }
            )
        }
        composable(
            route = "photoFull/{fileName}",
            arguments = listOf(navArgument("fileName") { type = NavType.StringType })
        ) { backStackEntry ->
            val fileName = backStackEntry.arguments?.getString("fileName") ?: ""
            ConditionPhotoFullScreen(
                fileName = fileName,
                onBack = { navController.popBackStack() }
            )
        }
    }
  }
}
