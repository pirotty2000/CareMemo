package jp.mydns.fujiwara.carememo

import android.app.ActivityManager
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.biometric.BiometricPrompt
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch
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
import jp.mydns.fujiwara.carememo.viewmodel.PersonConditionViewModel
import jp.mydns.fujiwara.carememo.viewmodel.HealthRecordViewModel
import jp.mydns.fujiwara.carememo.viewmodel.PersonListViewModel
import jp.mydns.fujiwara.carememo.viewmodel.MedicationViewModel
import jp.mydns.fujiwara.carememo.viewmodel.SettingsViewModel
import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

class MainActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // セキュリティ保護: 履歴画面でのスクリーンショットや中身の表示を禁止
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        
        // 履歴画面での識別性を向上させる（アプリ名と色を明示）
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val taskDescription = ActivityManager.TaskDescription.Builder()
                .setLabel(getString(R.string.app_name))
                .setIcon(R.mipmap.ic_launcher)
                .setPrimaryColor(0xFF6650A4.toInt())
                .build()
            setTaskDescription(taskDescription)
        } else {
            @Suppress("DEPRECATION")
            val taskDescription = ActivityManager.TaskDescription(
                getString(R.string.app_name),
                R.mipmap.ic_launcher,
                0xFF6650A4.toInt(),
            )
            setTaskDescription(taskDescription)
        }

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
    val scope = rememberCoroutineScope()

    val isBiometricEnabled by userSettingsRepository.isBiometricEnabled.collectAsState(initial = null)
    val lockTimeoutMinutes by userSettingsRepository.lockTimeoutMinutes.collectAsState(initial = 0)
    val lastActiveTime by userSettingsRepository.lastActiveTime.collectAsState(initial = 0L)
    
    var isAuthenticated by rememberSaveable { mutableStateOf(false) }

    // ライフサイクル監視による自動ロック判定
    DisposableEffect(activity) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_STOP -> {
                    // アプリがバックグラウンドに回った時刻を保存
                    scope.launch {
                        userSettingsRepository.setLastActiveTime(System.currentTimeMillis())
                    }
                }
                Lifecycle.Event.ON_START -> {
                    // フォアグラウンドに戻った際にタイムアウト判定
                    if ((isBiometricEnabled == true) && isAuthenticated) {
                        // 外部アプリ呼び出し（ファイル選択等）からの復帰時はロックをスキップ
                        if (userSettingsRepository.isLockBypassed) {
                            userSettingsRepository.isLockBypassed = false
                            return@LifecycleEventObserver
                        }

                        if (lockTimeoutMinutes != -1) { // -1 は「ロックしない」
                            val elapsedMillis = System.currentTimeMillis() - lastActiveTime
                            val timeoutMillis = lockTimeoutMinutes * 60 * 1000L
                            
                            if (elapsedMillis > timeoutMillis) {
                                isAuthenticated = false
                            }
                        }
                    }
                }
                else -> {}
            }
        }
        activity.lifecycle.addObserver(observer)
        onDispose {
            activity.lifecycle.removeObserver(observer)
        }
    }

    LaunchedEffect(isBiometricEnabled, isAuthenticated) {
        if ((isBiometricEnabled == true) && !isAuthenticated) {
            val executor = ContextCompat.getMainExecutor(activity)
            val biometricPrompt = BiometricPrompt(
                activity,
                executor,
                object : BiometricPrompt.AuthenticationCallback() {
                    override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                        super.onAuthenticationSucceeded(result)
                        isAuthenticated = true
                    }

                    override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                        super.onAuthenticationError(errorCode, errString)
                        // キャンセルされた場合やエラーの場合はアプリを終了する（セキュリティのため）
                        // DEVICE_CREDENTIAL を含めている場合、ユーザーがシステム側でキャンセルするとここに来る
                        if (errorCode == BiometricPrompt.ERROR_USER_CANCELED || 
                            errorCode == BiometricPrompt.ERROR_NEGATIVE_BUTTON ||
                            errorCode == BiometricPrompt.ERROR_LOCKOUT ||
                            errorCode == BiometricPrompt.ERROR_LOCKOUT_PERMANENT ||
                            errorCode == BiometricPrompt.ERROR_NO_BIOMETRICS ||
                            errorCode == BiometricPrompt.ERROR_HW_NOT_PRESENT) {
                            activity.finish()
                        }
                    }
                })

            val promptInfo = BiometricPrompt.PromptInfo.Builder()
                .setTitle("アプリ・ロック")
                .setSubtitle("認証情報を入力してロックを解除してください")
                .setAllowedAuthenticators(BIOMETRIC_STRONG or DEVICE_CREDENTIAL)
                // DEVICE_CREDENTIAL を許可する場合、NegativeButtonText は設定不可
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
                    if (category == Category.MEDICATION) {
                        navController.navigate("medication/$personId")
                    } else {
                        val query = listViewModel.searchQuery.value
                        val encodedQuery = if (query.isNotBlank()) URLEncoder.encode(query, StandardCharsets.UTF_8.toString()) else ""
                        navController.navigate("detail/$personId/${category.name}?query=$encodedQuery")
                    }
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
            val settingsViewModel: SettingsViewModel = viewModel(
                factory = SettingsViewModel.Factory(repository, userSettingsRepository)
            )
            SettingsScreen(
                viewModel = settingsViewModel,
                onNavigateToRestore = {
                    navController.navigate("restore")
                },
                onBack = { navController.popBackStack() }
            )
        }
        composable(
            route = "medication/{personId}",
            arguments = listOf(navArgument("personId") { type = NavType.IntType })
        ) { backStackEntry ->
            val personId = backStackEntry.arguments?.getInt("personId") ?: 0
            val medicationViewModel: MedicationViewModel = viewModel(
                factory = MedicationViewModel.Factory(repository, userSettingsRepository)
            )
            MedicationScreen(
                viewModel = medicationViewModel,
                personId = personId,
                onBack = { navController.popBackStack("main", inclusive = false) },
                onNavigateToCategory = { category ->
                    // クエリパラメータを含めたルートを指定
                    navController.navigate("detail/$personId/${category.name}?query=") {
                        popUpTo("main") { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                }
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
            val conditionViewModel: PersonConditionViewModel = viewModel(
                factory = PersonConditionViewModel.Factory(repository, userSettingsRepository)
            )

            // 検索キーワードの初期セット
            LaunchedEffect(initialQuery) {
                if (initialQuery.isNotBlank()) {
                    detailViewModel.updateSearchQuery(initialQuery)
                }
            }

            UnifiedRecordScreen(
                viewModel = detailViewModel,
                conditionViewModel = conditionViewModel,
                initialCategoryType = category,
                personId = personId,
                onBack = { navController.popBackStack("main", inclusive = false) },
                onNavigateToConditionDetail = { pId, cId ->
                    navController.navigate("conditionDetail/$pId/$cId")
                },
                onNavigateToHealthRecordDetail = { pId, cat, rId ->
                    navController.navigate("healthRecordDetail/$pId/${cat.name}/$rId")
                },
                onNavigateToGraphExpansion = { pId, cat, index ->
                    navController.navigate("graphExpansion/$pId/${cat.name}/$index")
                },
                onNavigateToMedication = { pId ->
                    navController.navigate("medication/$pId") {
                        popUpTo("main") { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
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
            val conditionViewModel: PersonConditionViewModel = viewModel(
                factory = PersonConditionViewModel.Factory(repository, userSettingsRepository)
            )
            val detailViewModel: PersonDetailViewModel = viewModel(
                factory = PersonDetailViewModel.Factory(repository, userSettingsRepository)
            )
            ConditionDetailScreen(
                viewModel = detailViewModel,
                conditionViewModel = conditionViewModel,
                personId = personId,
                conditionId = conditionId,
                onBack = { navController.popBackStack() },
                onNavigateToPhotoPreview = { uri, pId, cId ->
                    val encodedUri = Uri.encode(uri.toString())
                    navController.navigate("photoPreview/$encodedUri/$pId/$cId")
                },
                onNavigateToFullScreen = { fileName, caption ->
                    val encodedCaption = caption?.let { URLEncoder.encode(it, "UTF-8") } ?: ""
                    navController.navigate("photoFull/$fileName?caption=$encodedCaption")
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
            val healthViewModel: HealthRecordViewModel = viewModel(
                factory = HealthRecordViewModel.Factory(repository, userSettingsRepository)
            )
            val detailViewModel: PersonDetailViewModel = viewModel(
                factory = PersonDetailViewModel.Factory(repository, userSettingsRepository)
            )
            HealthRecordDetailScreen(
                viewModel = detailViewModel,
                healthViewModel = healthViewModel,
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
            val healthViewModel: HealthRecordViewModel = viewModel(
                factory = HealthRecordViewModel.Factory(repository, userSettingsRepository)
            )
            val detailViewModel: PersonDetailViewModel = viewModel(
                factory = PersonDetailViewModel.Factory(repository, userSettingsRepository)
            )
            GraphExpansionScreen(
                viewModel = detailViewModel,
                healthViewModel = healthViewModel,
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
            val conditionViewModel: PersonConditionViewModel = viewModel(
                factory = PersonConditionViewModel.Factory(repository, userSettingsRepository)
            )
            val detailViewModel: PersonDetailViewModel = viewModel(
                factory = PersonDetailViewModel.Factory(repository, userSettingsRepository)
            )
            ConditionPhotoPreviewScreen(
                viewModel = detailViewModel,
                conditionViewModel = conditionViewModel,
                uri = uri,
                personId = personId,
                conditionId = conditionId,
                onBack = { navController.popBackStack() },
                onSaved = { navController.popBackStack() }
            )
        }
        composable(
            route = "photoFull/{fileName}?caption={caption}",
            arguments = listOf(
                navArgument("fileName") { type = NavType.StringType },
                navArgument("caption") { 
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                }
            )
        ) { backStackEntry ->
            val fileName = backStackEntry.arguments?.getString("fileName") ?: ""
            val caption = backStackEntry.arguments?.getString("caption")
            ConditionPhotoFullScreen(
                fileName = fileName,
                caption = caption,
                onBack = { navController.popBackStack() }
            )
        }
    }
  }
}
