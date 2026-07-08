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
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
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
import jp.mydns.fujiwara.carememo.data.ThemeSetting
import jp.mydns.fujiwara.carememo.ui.screens.main.MainScreen
import jp.mydns.fujiwara.carememo.ui.screens.main.PersonEditScreen
import jp.mydns.fujiwara.carememo.ui.screens.health.BatchInputScreen
import jp.mydns.fujiwara.carememo.ui.screens.health.GraphExpansionScreen
import jp.mydns.fujiwara.carememo.ui.screens.health.PersonHealthScreen
import jp.mydns.fujiwara.carememo.ui.screens.condition.PersonConditionScreen
import jp.mydns.fujiwara.carememo.ui.screens.condition.ConditionPhotoFullScreen
import jp.mydns.fujiwara.carememo.ui.screens.condition.ConditionPhotoPreviewScreen
import jp.mydns.fujiwara.carememo.ui.screens.medication.PersonMedicationScreen
import jp.mydns.fujiwara.carememo.ui.screens.settings.AuditLogScreen
import jp.mydns.fujiwara.carememo.ui.screens.settings.DeleteOrRestorePersonScreen
import jp.mydns.fujiwara.carememo.ui.screens.settings.SettingsScreen
import jp.mydns.fujiwara.carememo.ui.theme.CareMemoTheme
import jp.mydns.fujiwara.carememo.utils.PdfExporter
import jp.mydns.fujiwara.carememo.viewmodel.PersonDetailViewModel
import jp.mydns.fujiwara.carememo.viewmodel.BatchInputViewModel
import jp.mydns.fujiwara.carememo.viewmodel.PersonConditionViewModel
import jp.mydns.fujiwara.carememo.viewmodel.PersonHealthViewModel
import jp.mydns.fujiwara.carememo.viewmodel.PersonListViewModel
import jp.mydns.fujiwara.carememo.viewmodel.PersonEditViewModel
import jp.mydns.fujiwara.carememo.viewmodel.PersonMedicationViewModel
import jp.mydns.fujiwara.carememo.viewmodel.DeleteOrRestorePersonViewModel
import jp.mydns.fujiwara.carememo.viewmodel.SettingsViewModel
import jp.mydns.fujiwara.carememo.data.repository.AuditLogRepository
import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

class MainActivity : FragmentActivity() {
    @OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // セキュリティ保護
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        
        // 履歴画面での識別性向上
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

        PdfExporter.clearOldExports(this)

        enableEdgeToEdge()
        setContent {
            val windowSize = calculateWindowSizeClass(this)
            CareMemoApp(this, windowSize.widthSizeClass)
        }
    }
}

@Composable
fun CareMemoApp(activity: FragmentActivity, widthSizeClass: WindowWidthSizeClass) {
    val context = LocalContext.current
    val application = context.applicationContext as CareMemoApplication
    val personRepository = application.personRepository
    val deleteOrRestorePersonRepository = application.deleteOrRestorePersonRepository
    val personSummaryRepository = application.personSummaryRepository
    val appMaintenanceRepository = application.appMaintenanceRepository
    val healthRepository = application.healthRepository
    val conditionRepository = application.conditionRepository
    val medicationRepository = application.medicationRepository
    val auditLogRepository = application.auditLogRepository
    val userSettingsRepository = application.userSettingsRepository
    val themeSetting by userSettingsRepository.themeSetting.collectAsState(initial = ThemeSetting.SYSTEM)

    CareMemoTheme(themeSetting = themeSetting) {
        val navController = rememberNavController()
        val scope = rememberCoroutineScope()

        val isBiometricEnabled by userSettingsRepository.isBiometricEnabled.collectAsState(initial = false)
        val isBiometricSettingInitialized by userSettingsRepository.isBiometricSettingInitialized.collectAsState(initial = true)
        val lockTimeoutMinutes by userSettingsRepository.lockTimeoutMinutes.collectAsState(initial = 0)
        val lastActiveTime by userSettingsRepository.lastActiveTime.collectAsState(initial = 0L)
        
        var isAuthenticated by rememberSaveable { mutableStateOf(false) }

        // 初回起動時の生体認証設定の自動最適化
        LaunchedEffect(isBiometricSettingInitialized) {
            if (!isBiometricSettingInitialized) {
                val biometricManager = androidx.biometric.BiometricManager.from(activity)
                val status = biometricManager.canAuthenticate(BIOMETRIC_STRONG or DEVICE_CREDENTIAL)
                if (status == androidx.biometric.BiometricManager.BIOMETRIC_SUCCESS) {
                    // 認証可能なデバイスならデフォルトON（Security by Default）
                    userSettingsRepository.setBiometricEnabled(true)
                } else {
                    // 非対応または未設定ならOFF
                    userSettingsRepository.setBiometricEnabled(false)
                }
            }
        }

        // アプリ・ロック：
        // 「最後にアプリを閉じてからどれくらい時間が経過したか」を判定し、必要であれば再認証（生体認証）を要求する
        DisposableEffect(activity) {
            val observer = LifecycleEventObserver { _, event ->
                when (event) {
                    Lifecycle.Event.ON_STOP -> {
                        scope.launch { userSettingsRepository.setLastActiveTime(System.currentTimeMillis()) }
                    }
                    Lifecycle.Event.ON_START -> {
                        if ((isBiometricEnabled == true) && isAuthenticated) {
                            if (userSettingsRepository.isLockBypassed) {
                                userSettingsRepository.isLockBypassed = false
                                return@LifecycleEventObserver
                            }
                            if (lockTimeoutMinutes != -1) {
                                val elapsedMillis = System.currentTimeMillis() - lastActiveTime
                                val timeoutMillis = lockTimeoutMinutes * 60 * 1000L
                                if (elapsedMillis > timeoutMillis) isAuthenticated = false
                            }
                        }
                    }
                    else -> {}
                }
            }
            activity.lifecycle.addObserver(observer)
            onDispose { activity.lifecycle.removeObserver(observer) }
        }

        // アプリ・ロック
        // 実際にロック画面を表示
        LaunchedEffect(isBiometricEnabled, isAuthenticated) {
            if ((isBiometricEnabled == true) && !isAuthenticated) {
                val biometricManager = androidx.biometric.BiometricManager.from(activity)
                val canAuth = biometricManager.canAuthenticate(BIOMETRIC_STRONG or DEVICE_CREDENTIAL)
                
                // デバイスが認証不可能な状態（ハードウェア故障、セキュリティ設定の削除など）に陥っている場合
                if (canAuth != androidx.biometric.BiometricManager.BIOMETRIC_SUCCESS) {
                    if (canAuth == androidx.biometric.BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE || 
                        canAuth == androidx.biometric.BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE ||
                        canAuth == androidx.biometric.BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED ||
                        canAuth == androidx.biometric.BiometricManager.BIOMETRIC_ERROR_SECURITY_UPDATE_REQUIRED) {
                        
                        // ロックアウトを避けるため、認証成功とみなして設定をOFFにする
                        isAuthenticated = true
                        userSettingsRepository.setBiometricEnabled(false)
                        return@LaunchedEffect
                    }
                }

                val executor = ContextCompat.getMainExecutor(activity)
                val biometricPrompt = BiometricPrompt(activity, executor, object : BiometricPrompt.AuthenticationCallback() {
                    override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                        super.onAuthenticationSucceeded(result)
                        isAuthenticated = true
                    }
                    override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                        super.onAuthenticationError(errorCode, errString)
                        if (errorCode == BiometricPrompt.ERROR_USER_CANCELED || errorCode == BiometricPrompt.ERROR_NEGATIVE_BUTTON || errorCode == BiometricPrompt.ERROR_LOCKOUT || errorCode == BiometricPrompt.ERROR_LOCKOUT_PERMANENT || errorCode == BiometricPrompt.ERROR_NO_BIOMETRICS || errorCode == BIOMETRIC_STRONG) {
                            activity.finish()
                        }
                    }
                })
                val promptInfo = BiometricPrompt.PromptInfo.Builder()
                    .setTitle("アプリ・ロック").setSubtitle("認証情報を入力してください")
                    .setAllowedAuthenticators(BIOMETRIC_STRONG or DEVICE_CREDENTIAL).build()
                biometricPrompt.authenticate(promptInfo)
            } else if (isBiometricEnabled == false) {
                isAuthenticated = true
            }
        }

        if (isAuthenticated || isBiometricEnabled == false) {
            // 認証要求を処理する共通関数
            val requestAuthentication: (Int?, Int?, () -> Unit) -> Unit = { titleResId, subtitleResId, onSuccess ->
                val executor = ContextCompat.getMainExecutor(activity)
                val biometricPrompt = BiometricPrompt(
                    activity, executor, object : BiometricPrompt.AuthenticationCallback() {
                    override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                        super.onAuthenticationSucceeded(result)
                        onSuccess()
                    }
                })
                val title = titleResId?.let { context.getString(it) } ?: context.getString(R.string.security_auth_title)
                val subtitle = subtitleResId?.let { context.getString(it) } ?: context.getString(R.string.security_auth_reason_change_settings)
                
                val promptInfo = BiometricPrompt.PromptInfo.Builder()
                    .setTitle(title)
                    .setSubtitle(subtitle)
                    .setAllowedAuthenticators(BIOMETRIC_STRONG or DEVICE_CREDENTIAL)
                    .build()
                biometricPrompt.authenticate(promptInfo)
            }

            NavHost(navController = navController, startDestination = "main") {

                // ---------------------------------------------
                // ---------- 「利用者一覧」(MainScreen) ----------
                // ---------------------------------------------
                composable("main") {
                    val listViewModel: PersonListViewModel = viewModel(
                        factory = PersonListViewModel.Factory(
                            personRepository,
                            deleteOrRestorePersonRepository,
                            personSummaryRepository,
                            conditionRepository,
                            userSettingsRepository))
                    MainScreen(
                        viewModel = listViewModel, 
                        onNavigateToDetail = { personId, category ->
                            val query = listViewModel.searchQuery.value
                            val encodedQuery = if (query.isNotBlank()) URLEncoder.encode(query, StandardCharsets.UTF_8.toString()) else ""
                            navController.navigate(category.getRoute(personId, encodedQuery))
                        }, 
                        onNavigateToBatchInput = { personId ->
                            navController.navigate("batch_input/$personId")
                        },
                        onNavigateToAddPerson = {
                            navController.navigate("person_edit/-1")
                        },
                        onNavigateToEditPerson = { personId ->
                            navController.navigate("person_edit/$personId")
                        },
                        onNavigateToSettings = { navController.navigate("settings") }
                    )
                }

                // ---------- 「利用者情報の登録・編集」 ----------
                composable("person_edit/{personId}", arguments = listOf(
                    navArgument("personId") { type = NavType.IntType }
                )) { backStackEntry ->
                    val personId = backStackEntry.arguments?.getInt("personId") ?: -1
                    val editViewModel: PersonEditViewModel = viewModel(
                        factory = PersonEditViewModel.Factory(
                            personId, personRepository, userSettingsRepository))
                    PersonEditScreen(
                        viewModel = editViewModel,
                        onBack = { navController.popBackStack() }
                    )
                }

                // --------------------------------------------------------
                // ----- 利用者詳細データ（各カテゴリの閲覧・登録・修正・削除） -----
                // --------------------------------------------------------

                // ---------- 「身長・体重」「バイタル」「血糖値・HbA1c」 ----------
                composable("detail/{personId}/{categoryName}?query={query}", arguments = listOf(
                    navArgument("personId") { type = NavType.IntType },
                    navArgument("categoryName") { type = NavType.StringType },
                    navArgument("query") { type = NavType.StringType; nullable = true; defaultValue = "" })) { backStackEntry ->
                    val personId = backStackEntry.arguments?.getInt("personId") ?: 0
                    val categoryName = backStackEntry.arguments?.getString("categoryName") ?: Category.BP_AND_PULSE.name
                    val category = Category.valueOf(categoryName)
                    val detailViewModel: PersonDetailViewModel = viewModel(
                        factory = PersonDetailViewModel.Factory(
                            personRepository, personSummaryRepository, userSettingsRepository))
                    val healthViewModel: PersonHealthViewModel = viewModel(
                        factory = PersonHealthViewModel.Factory(
                            personRepository, personSummaryRepository, healthRepository, userSettingsRepository))
                    PersonHealthScreen(
                        viewModel = detailViewModel,
                        healthViewModel = healthViewModel,
                        initialCategoryType = category,
                        personId = personId,
                        widthSizeClass = widthSizeClass,
                        onRequireAuthentication = requestAuthentication,
                        onBack = { navController.popBackStack("main", inclusive = false) },
                        onNavigateToGraphExpansion = { pId, cat, index -> navController.navigate("graphExpansion/$pId/${cat.name}/$index") },
                        onNavigateToCategory = { cat ->
                            navController.navigate(cat.getRoute(personId)) {
                                popUpTo("main")
                                launchSingleTop = true
                            }
                        }
                    )
                }
                // ---------- 「身長・体重」「バイタル」「血糖値・HbA1c」のグラフ拡大 ----------
                composable("graphExpansion/{personId}/{categoryName}/{initialIndex}", arguments = listOf(
                    navArgument("personId") { type = NavType.IntType },
                    navArgument("categoryName") { type = NavType.StringType },
                    navArgument("initialIndex") { type = NavType.IntType })) { backStackEntry ->
                    val personId = backStackEntry.arguments?.getInt("personId") ?: 0
                    val categoryName = backStackEntry.arguments?.getString("categoryName") ?: ""
                    val category = Category.valueOf(categoryName)
                    val initialIndex = backStackEntry.arguments?.getInt("initialIndex") ?: 0
                    val healthViewModel: PersonHealthViewModel = viewModel(
                        factory = PersonHealthViewModel.Factory(
                            personRepository, personSummaryRepository, healthRepository, userSettingsRepository))
                    val detailViewModel: PersonDetailViewModel = viewModel(
                        factory = PersonDetailViewModel.Factory(
                            personRepository, personSummaryRepository, userSettingsRepository))
                    GraphExpansionScreen(viewModel = detailViewModel,
                        healthViewModel = healthViewModel,
                        personId = personId,
                        category = category,
                        initialGraphIndex = initialIndex,
                        onBack = { navController.popBackStack() })
                }

                // ---------- 「身長・体重」「バイタル」「血糖値・HbA1c」の一括入力 ----------
                composable("batch_input/{personId}", arguments = listOf(
                    navArgument("personId") { type = NavType.IntType })) { backStackEntry ->
                    val personId = backStackEntry.arguments?.getInt("personId") ?: 0
                    val batchViewModel: BatchInputViewModel = viewModel(
                        factory = BatchInputViewModel.Factory(
                            personRepository, personSummaryRepository, healthRepository, userSettingsRepository))
                    BatchInputScreen(viewModel = batchViewModel, personId = personId, onBack = { navController.popBackStack() })
                }

                // ---------- 「所見メモ」 ----------
                composable("condition/{personId}?query={query}", arguments = listOf(
                    navArgument("personId") { type = NavType.IntType },
                    navArgument("query") { type = NavType.StringType; nullable = true; defaultValue = "" }
                )) { backStackEntry ->
                    val personId = backStackEntry.arguments?.getInt("personId") ?: 0
                    val initialQuery = backStackEntry.arguments?.getString("query")?.let {
                        if (it.isNotBlank()) URLDecoder.decode(it, StandardCharsets.UTF_8.toString()) else "" } ?: ""
                    val detailViewModel: PersonDetailViewModel = viewModel(
                        factory = PersonDetailViewModel.Factory(
                            personRepository, personSummaryRepository, userSettingsRepository))
                    val conditionViewModel: PersonConditionViewModel = viewModel(
                        factory = PersonConditionViewModel.Factory(
                            personRepository, personSummaryRepository, conditionRepository, userSettingsRepository))

                    PersonConditionScreen(
                        viewModel = detailViewModel,
                        conditionViewModel = conditionViewModel,
                        personId = personId,
                        initialQuery = initialQuery,
                        widthSizeClass = widthSizeClass,
                        onRequireAuthentication = requestAuthentication,
                        onBack = { navController.popBackStack("main", inclusive = false) },
                        onNavigateToCategory = { category ->
                            navController.navigate(category.getRoute(personId)) {
                                popUpTo("main")
                                launchSingleTop = true
                            }
                        },
                        onNavigateToPhotoPreview = { uri, pId, cId ->
                            val encodedUri = Uri.encode(uri.toString())
                            navController.navigate("photoPreview/$encodedUri/$pId/$cId")
                        },
                        onNavigateToFullScreen = { conditionId, photoId ->
                            navController.navigate("photoFull/$conditionId/$photoId")
                        }
                    )
                }

                // ---------- 「所見メモ」の写真プレビュー ----------
                composable("photoPreview/{uri}/{personId}/{conditionId}", arguments = listOf(
                    navArgument("uri") { type = NavType.StringType },
                    navArgument("personId") { type = NavType.IntType },
                    navArgument("conditionId") { type = NavType.IntType })) { backStackEntry ->
                    val uri = Uri.parse(Uri.decode(backStackEntry.arguments?.getString("uri") ?: ""))
                    val personId = backStackEntry.arguments?.getInt("personId") ?: 0
                    val conditionId = backStackEntry.arguments?.getInt("conditionId") ?: 0
                    val conditionViewModel: PersonConditionViewModel = viewModel(factory = PersonConditionViewModel.Factory(personRepository, personSummaryRepository, conditionRepository, userSettingsRepository))
                    val detailViewModel: PersonDetailViewModel = viewModel(factory = PersonDetailViewModel.Factory(personRepository, personSummaryRepository, userSettingsRepository))
                    ConditionPhotoPreviewScreen(viewModel = detailViewModel, conditionViewModel = conditionViewModel, uri = uri, personId = personId, conditionId = conditionId, onBack = { navController.popBackStack() }, onSaved = { navController.popBackStack() })
                }

                // ---------- 「所見メモ」の写真表示 ----------
                composable(
                    "photoFull/{conditionId}/{initialPhotoId}",
                    arguments = listOf(
                        navArgument("conditionId") { type = NavType.IntType },
                        navArgument("initialPhotoId") { type = NavType.IntType }
                    )
                ) { backStackEntry ->
                    val conditionId = backStackEntry.arguments?.getInt("conditionId") ?: 0
                    val initialPhotoId = backStackEntry.arguments?.getInt("initialPhotoId") ?: 0
                    val conditionViewModel: PersonConditionViewModel = viewModel(factory = PersonConditionViewModel.Factory(personRepository, personSummaryRepository, conditionRepository, userSettingsRepository))

                    ConditionPhotoFullScreen(
                        conditionId = conditionId,
                        initialPhotoId = initialPhotoId,
                        viewModel = conditionViewModel,
                        onBack = { navController.popBackStack() }
                    )
                }

                // ---------- 「服薬管理」 ----------
                composable("medication/{personId}", arguments = listOf(navArgument("personId") { type = NavType.IntType })) { backStackEntry ->
                    val personId = backStackEntry.arguments?.getInt("personId") ?: 0
                    val detailViewModel: PersonDetailViewModel = viewModel(factory = PersonDetailViewModel.Factory(personRepository, personSummaryRepository, userSettingsRepository))
                    val medicationViewModel: PersonMedicationViewModel = viewModel(factory = PersonMedicationViewModel.Factory(personRepository, personSummaryRepository, medicationRepository, userSettingsRepository))
                    PersonMedicationScreen(
                        viewModel = detailViewModel,
                        medicationViewModel = medicationViewModel,
                        personId = personId,
                        widthSizeClass = widthSizeClass,
                        onRequireAuthentication = requestAuthentication,
                        onBack = { navController.popBackStack("main", inclusive = false) },
                        onNavigateToCategory = { category ->
                        navController.navigate(category.getRoute(personId)) {
                            popUpTo("main")
                            launchSingleTop = true
                        }
                    })
                }

                // -------------------------------------------------------------
                // ----- 設定・管理画面（アプリケーションの設定、利用修了者管理など） -----
                // -------------------------------------------------------------

                // ---------- 「設定・管理」 ----------
                composable("settings") {
                    val settingsViewModel: SettingsViewModel = viewModel(factory = SettingsViewModel.Factory(appMaintenanceRepository, deleteOrRestorePersonRepository, auditLogRepository, userSettingsRepository))
                    SettingsScreen(
                        viewModel = settingsViewModel, 
                        onNavigateToArchiveManagement = { mode ->
                            navController.navigate("archive_management/${mode.name}")
                        },
                        onNavigateToAuditLog = {
                            navController.navigate("audit_log")
                        },
                        onRequireAuthentication = requestAuthentication,
                        onBack = { navController.popBackStack() }
                    )
                }

                // ---------- 設定：操作ログ参照 ----------
                composable("audit_log") {
                    val settingsViewModel: SettingsViewModel = viewModel(factory = SettingsViewModel.Factory(appMaintenanceRepository, deleteOrRestorePersonRepository, auditLogRepository, userSettingsRepository))
                    AuditLogScreen(
                        viewModel = settingsViewModel,
                        onBack = { navController.popBackStack() }
                    )
                }

                // ---------- 設定：終了利用者の復帰・抹消 ----------
                composable("archive_management/{mode}") { backStackEntry ->
                    val modeName = backStackEntry.arguments?.getString("mode") ?: DeleteOrRestorePersonViewModel.OperationMode.RESTORE.name
                    val mode = DeleteOrRestorePersonViewModel.OperationMode.valueOf(modeName)
                    val archiveViewModel: DeleteOrRestorePersonViewModel = viewModel(
                        factory = DeleteOrRestorePersonViewModel.Factory(
                            deleteOrRestorePersonRepository, userSettingsRepository))
                    DeleteOrRestorePersonScreen(
                        viewModel = archiveViewModel,
                        mode = mode,
                        onBack = { navController.popBackStack() }
                    )
                }



            }
        }
    }
}
