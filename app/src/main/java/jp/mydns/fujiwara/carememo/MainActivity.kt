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
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.SecurityUpdateWarning
import androidx.compose.material3.*
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.navigation
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import jp.mydns.fujiwara.carememo.data.ThemeSetting
import jp.mydns.fujiwara.carememo.logic.feature.SecurityLogic
import jp.mydns.fujiwara.carememo.logic.feature.SecurityStatus
import jp.mydns.fujiwara.carememo.ui.navigation.Destination
import jp.mydns.fujiwara.carememo.ui.screens.main.*
import jp.mydns.fujiwara.carememo.ui.screens.health.*
import jp.mydns.fujiwara.carememo.ui.screens.condition.*
import jp.mydns.fujiwara.carememo.ui.screens.medication.*
import jp.mydns.fujiwara.carememo.ui.screens.settings.*
import jp.mydns.fujiwara.carememo.ui.theme.CareMemoTheme
import jp.mydns.fujiwara.carememo.viewmodel.*

/**
 * Activity：MainActivity
 *
 * 【役割】
 * CareMemo アプリの唯一の Activity であり、UI の構成、ナビゲーション、およびセキュリティ制御（アプリロック）を統括します。
 * Composable 群のエントリポイントとして機能し、アプリケーション全体の画面遷移グラフ（NavHost）を定義します。
 *
 * 【主要な機能】
 * ・ナビゲーション管理：`NavHost` と `Destination` (Type-safe) を使用した画面遷移制御。
 * ・セキュリティ制御：生体認証（BiometricPrompt）によるアプリロックおよび機密情報保護（FLAG_SECURE）。
 * ・アダプティブ UI：`WindowSizeClass` を計測し、画面幅に応じたレイアウト（Phone/Tablet）の切り替えを支援。
 * ・依存性の注入：`CareMemoApplication` からリポジトリを取得し、ViewModel Factory 経由で各 ViewModel へ注入。
 * ・ライフサイクル監視：アプリのフォアグラウンド復帰時にロック状態を適切に更新。
 *
 * 【全体像：画面構成 (UI Composition)】
 *
 * ■ MainActivity (★本クラス)
 * │
 * ├─ [ 認証層 ]
 * │    ├─ SecurityWarningScreen (セキュリティ警告：設定不備時)
 * │    └─ LockScreen (アプリロック画面：認証待ち)
 * │
 * └─ [コンテンツ層：NavHost]
 *      ├─ MainScreen (利用者一覧)
 *      ├─ PersonEditScreen (登録・編集)
 *      ├─ HealthDetail (健康記録：詳細)
 *      ├─ ConditionDetail (所見メモ：詳細)
 *      └─ SettingsScreen (設定)
 */
class MainActivity : FragmentActivity() {
    @OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val taskDescription = ActivityManager.TaskDescription.Builder()
                .setLabel(getString(R.string.app_name)).setIcon(R.mipmap.ic_launcher).setPrimaryColor(0xFF6650A4.toInt()).build()
            setTaskDescription(taskDescription)
        } else {
            @Suppress("DEPRECATION")
            val taskDescription = ActivityManager.TaskDescription(getString(R.string.app_name), R.mipmap.ic_launcher, 0xFF6650A4.toInt())
            setTaskDescription(taskDescription)
        }

        val application = application as CareMemoApplication
        val personRepository = application.personRepository
        val deleteOrRestorePersonRepository = application.deleteOrRestorePersonRepository
        val personSummaryRepository = application.personSummaryRepository
        val healthRepository = application.healthRepository
        val conditionRepository = application.conditionRepository
        val medicationRepository = application.medicationRepository
        val userSettingsRepository = application.userSettingsRepository
        val auditLogRepository = application.auditLogRepository
        val appMaintenanceRepository = application.appMaintenanceRepository
        val securitySession = application.securitySession

        enableEdgeToEdge()

        // テストモードフラグの取得（UIシナリオテスト用バイパス）
        val isTestMode = intent.getBooleanExtra("IS_TEST_MODE", false)

        setContent {
            val isBiometricEnabledFlow = userSettingsRepository.isBiometricEnabled
            val biometricEnabledState by isBiometricEnabledFlow.collectAsStateWithLifecycle(initialValue = null)
            
            var isAuthenticated by rememberSaveable { mutableStateOf(false) }

            val securityStatus = remember(biometricEnabledState, isAuthenticated) {
                SecurityLogic.determineStatus(
                    isConfigLoaded = biometricEnabledState != null,
                    isBiometricSupported = this@MainActivity.isBiometricSupported(),
                    isBiometricEnabled = biometricEnabledState ?: false,
                    isAuthenticated = isAuthenticated,
                    isTestMode = isTestMode
                )
            }

            splashScreen.setKeepOnScreenCondition {
                securityStatus == SecurityStatus.INITIALIZING
            }

                val themeSetting by userSettingsRepository.themeSetting.collectAsStateWithLifecycle(initialValue = ThemeSetting.SYSTEM)
            CareMemoTheme(themeSetting = themeSetting) {
                val navController = rememberNavController()
                val widthSizeClass = calculateWindowSizeClass(this).widthSizeClass
                
                val requestAuthentication: (Int?, Int?, () -> Unit) -> Unit = { titleResId, subtitleResId, onSuccess ->
                    this@MainActivity.authenticate(titleResId, subtitleResId, onSuccess)
                }

                DisposableEffect(Lifecycle.Event.ON_PAUSE, Lifecycle.Event.ON_RESUME) {
                    val observer = LifecycleEventObserver { _, event ->
                        if (event == Lifecycle.Event.ON_RESUME) {
                            if (isAuthenticated && (biometricEnabledState == true) && !securitySession.isLockBypassed) {
                                // 即時ロック（バックグラウンドから復帰した際に必ずロックする）
                                isAuthenticated = false
                            }
                            securitySession.isLockBypassed = false
                        }
                    }
                    lifecycle.addObserver(observer)
                    onDispose { lifecycle.removeObserver(observer) }
                }

                when (securityStatus) {
                    SecurityStatus.INITIALIZING -> {
                        Box(Modifier.fillMaxSize())
                    }
                    SecurityStatus.UNSECURED -> {
                        SecurityWarningScreen()
                    }
                    SecurityStatus.LOCKED -> {
                        LockScreen(onUnlockRequest = { this@MainActivity.authenticate(onSuccess = { isAuthenticated = true }) })
                    }
                    SecurityStatus.UNLOCKED -> {
                        NavHost(navController = navController, startDestination = Destination.Main) {

                            composable<Destination.Main> {
                                val listViewModel: PersonListViewModel =
                                    viewModel(factory = PersonListViewModel.Factory(
                                        personRepository,
                                        deleteOrRestorePersonRepository,
                                        personSummaryRepository,
                                        conditionRepository,
                                        application.emergencyContactRepository,
                                        userSettingsRepository,
                                        securitySession,
                                        auditLogRepository))
                                MainScreen(viewModel = listViewModel, navController = navController)
                            }

                            composable<Destination.PersonEdit> {
                                val editViewModel: PersonEditViewModel =
                                    viewModel(factory = PersonEditViewModel.Factory(
                                        personRepository,
                                        userSettingsRepository,
                                        securitySession,
                                        auditLogRepository))
                                PersonEditScreen(viewModel = editViewModel, navController = navController)
                            }

                            composable<Destination.MedicalContacts> {
                                val medicalViewModel: EmergencyContactEditViewModel =
                                    viewModel(factory = EmergencyContactEditViewModel.Factory(
                                        application.emergencyContactRepository,
                                        personRepository,
                                        userSettingsRepository,
                                        securitySession,
                                        auditLogRepository))
                                EmergencyContactListScreen(viewModel = medicalViewModel, navController = navController)
                            }

                            composable<Destination.MedicalContactEdit> {
                                val medicalViewModel: EmergencyContactEditViewModel =
                                    viewModel(factory = EmergencyContactEditViewModel.Factory(
                                        application.emergencyContactRepository,
                                        personRepository,
                                        userSettingsRepository,
                                        securitySession,
                                        auditLogRepository))
                                EmergencyContactEditScreen(viewModel = medicalViewModel, navController = navController)
                            }

                            composable<Destination.HealthDetail> {
                                val detailViewModel: PersonDetailUiStateViewModel =
                                    viewModel(factory = PersonDetailUiStateViewModel.Factory(
                                        personRepository,
                                        personSummaryRepository,
                                        userSettingsRepository,
                                        securitySession,
                                        auditLogRepository))
                                val healthViewModel: PersonHealthViewModel =
                                    viewModel(factory = PersonHealthViewModel.Factory(
                                        personRepository,
                                        personSummaryRepository,
                                        healthRepository,
                                        userSettingsRepository,
                                        securitySession,
                                        auditLogRepository))
                                PersonHealthScreen(
                                    detailViewModel = detailViewModel,
                                    healthViewModel = healthViewModel,
                                    navController = navController,
                                    widthSizeClass = widthSizeClass,
                                    onRequireAuthentication = requestAuthentication)
                            }

                            composable<Destination.BatchInput> {
                                val batchViewModel: BatchInputViewModel =
                                    viewModel(factory = BatchInputViewModel.Factory(
                                        personRepository,
                                        personSummaryRepository,
                                        healthRepository,
                                        userSettingsRepository,
                                        securitySession,
                                        auditLogRepository))
                                BatchInputScreen(viewModel = batchViewModel, navController = navController)
                            }

                            composable<Destination.GraphExpansion> { backStackEntry ->
                                val args = backStackEntry.toRoute<Destination.GraphExpansion>()
                                val detailViewModel: PersonDetailUiStateViewModel =
                                    viewModel(factory = PersonDetailUiStateViewModel.Factory(
                                        personRepository,
                                        personSummaryRepository,
                                        userSettingsRepository,
                                        securitySession,
                                        auditLogRepository))
                                val healthViewModel: PersonHealthViewModel =
                                    viewModel(factory = PersonHealthViewModel.Factory(
                                        personRepository,
                                        personSummaryRepository,
                                        healthRepository,
                                        userSettingsRepository,
                                        securitySession,
                                        auditLogRepository))
                                GraphExpansionScreen(
                                    detailViewModel = detailViewModel,
                                    healthViewModel = healthViewModel,
                                    initialGraphIndex = args.initialIndex,
                                    navController = navController)
                            }

                            navigation<Destination.ConditionDetailRoot>(startDestination = Destination.ConditionDetail(personId = "", categoryName = "")) {
                                composable<Destination.ConditionDetail> { backStackEntry ->
                                    val args = backStackEntry.toRoute<Destination.ConditionDetail>()
                                    val parentEntry = remember(backStackEntry) {
                                        navController.getBackStackEntry<Destination.ConditionDetailRoot>()
                                    }
                                    val detailViewModel: PersonDetailUiStateViewModel =
                                        viewModel(factory = PersonDetailUiStateViewModel.Factory(
                                            personRepository,
                                            personSummaryRepository,
                                            userSettingsRepository,
                                            securitySession,
                                            auditLogRepository))
                                    val conditionViewModel: PersonConditionViewModel =
                                        viewModel(parentEntry, factory = PersonConditionViewModel.Factory(
                                            personRepository,
                                            personSummaryRepository,
                                            conditionRepository,
                                            userSettingsRepository,
                                            securitySession,
                                            auditLogRepository))
                                    
                                    // 引数の同期
                                    LaunchedEffect(args) {
                                        conditionViewModel.setNavContext(personId = args.personId, previewUri = null)
                                        args.query?.let { conditionViewModel.updateSearchQuery(it) }
                                    }

                                    PersonConditionScreen(
                                        detailViewModel = detailViewModel,
                                        conditionViewModel = conditionViewModel,
                                        navController = navController,
                                        widthSizeClass = widthSizeClass,
                                        onRequireAuthentication = requestAuthentication)
                                }

                                composable<Destination.PhotoPreview> { backStackEntry ->
                                    val args = backStackEntry.toRoute<Destination.PhotoPreview>()
                                    val parentEntry = remember(backStackEntry) {
                                        navController.getBackStackEntry<Destination.ConditionDetailRoot>()
                                    }
                                    val detailViewModel: PersonDetailUiStateViewModel =
                                        viewModel(factory = PersonDetailUiStateViewModel.Factory(
                                            personRepository,
                                            personSummaryRepository,
                                            userSettingsRepository,
                                            securitySession,
                                            auditLogRepository))
                                    val conditionViewModel: PersonConditionViewModel =
                                        viewModel(parentEntry, factory = PersonConditionViewModel.Factory(
                                            personRepository,
                                            personSummaryRepository,
                                            conditionRepository,
                                            userSettingsRepository,
                                            securitySession,
                                            auditLogRepository))

                                    // 引数の同期
                                    LaunchedEffect(args) {
                                        conditionViewModel.setNavContext(
                                            personId = args.personId,
                                            conditionId = args.conditionId,
                                            previewUri = args.uri
                                        )
                                    }

                                    ConditionPhotoPreviewScreen(
                                        detailViewModel = detailViewModel,
                                        conditionViewModel = conditionViewModel,
                                        navController = navController)
                                }

                                @Suppress("UNUSED_VARIABLE")
                                composable<Destination.PhotoFull> { backStackEntry ->
                                    val args = backStackEntry.toRoute<Destination.PhotoFull>()
                                    val parentEntry = remember(backStackEntry) {
                                        navController.getBackStackEntry<Destination.ConditionDetailRoot>()
                                    }
                                    val conditionViewModel: PersonConditionViewModel =
                                        viewModel(parentEntry, factory = PersonConditionViewModel.Factory(
                                            personRepository,
                                            personSummaryRepository,
                                            conditionRepository,
                                            userSettingsRepository,
                                            securitySession,
                                            auditLogRepository))

                                    // 引数の同期
                                    LaunchedEffect(args) {
                                        conditionViewModel.setNavContext(
                                            personId = args.personId,
                                            conditionId = args.conditionId,
                                            initialPhotoId = args.initialPhotoId
                                        )
                                    }

                                    ConditionPhotoFullScreen(viewModel = conditionViewModel, navController = navController)
                                }
                            }

                            composable<Destination.MedicationDetail> {
                                val detailViewModel: PersonDetailUiStateViewModel =
                                    viewModel(factory = PersonDetailUiStateViewModel.Factory(
                                        personRepository,
                                        personSummaryRepository,
                                        userSettingsRepository,
                                        securitySession,
                                        auditLogRepository))
                                val medicationViewModel: PersonMedicationViewModel =
                                    viewModel(factory = PersonMedicationViewModel.Factory(
                                        personRepository,
                                        personSummaryRepository,
                                        medicationRepository,
                                        userSettingsRepository,
                                        securitySession,
                                        auditLogRepository))
                                PersonMedicationScreen(
                                    detailViewModel = detailViewModel,
                                    medicationViewModel = medicationViewModel,
                                    navController = navController,
                                    widthSizeClass = widthSizeClass,
                                    onRequireAuthentication = requestAuthentication)
                            }

                            composable<Destination.Settings> {
                                val settingsViewModel: SettingsViewModel =
                                    viewModel(factory = SettingsViewModel.Factory(
                                        appMaintenanceRepository,
                                        deleteOrRestorePersonRepository,
                                        auditLogRepository,
                                        userSettingsRepository,
                                        securitySession))
                                SettingsScreen(
                                    viewModel = settingsViewModel,
                                    navController = navController,
                                    onRequireAuthentication = requestAuthentication,
                                    onCheckBiometricSupport = { this@MainActivity.isBiometricSupported() })
                            }

                            composable<Destination.AuditLog> {
                                val auditLogViewModel: AuditLogViewModel =
                                    viewModel(factory = AuditLogViewModel.Factory(
                                        auditLogRepository,
                                        userSettingsRepository,
                                        securitySession))
                                AuditLogScreen(viewModel = auditLogViewModel, navController = navController)
                            }

                            composable<Destination.ArchiveManagement> {
                                val archiveViewModel: DeleteOrRestorePersonViewModel =
                                    viewModel(factory = DeleteOrRestorePersonViewModel.Factory(
                                        deleteOrRestorePersonRepository,
                                        userSettingsRepository,
                                        securitySession,
                                        auditLogRepository))
                                DeleteOrRestorePersonScreen(viewModel = archiveViewModel, navController = navController)
                            }

                            composable<Destination.UnassignedPhotos> {
                                val unassignedViewModel: UnassignedPhotoViewModel =
                                    viewModel(factory = UnassignedPhotoViewModel.Factory(
                                        userSettingsRepository,
                                        securitySession,
                                        conditionRepository))
                                UnassignedPhotoManagementScreen(viewModel = unassignedViewModel, navController = navController)
                            }
                        }
                    }
                }
            }
        }
    }

    private fun isBiometricSupported(): Boolean {
        val biometricManager = androidx.biometric.BiometricManager.from(this)
        val result = biometricManager.canAuthenticate(BIOMETRIC_STRONG or DEVICE_CREDENTIAL)
        return result != androidx.biometric.BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED &&
                result != androidx.biometric.BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE
    }

    private fun authenticate(titleResId: Int? = null, subtitleResId: Int? = null, onSuccess: () -> Unit) {
        val executor = ContextCompat.getMainExecutor(this)
        val biometricPrompt = BiometricPrompt(this, executor, object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                super.onAuthenticationSucceeded(result)
                onSuccess()
            }
        })
        val title = titleResId?.let { getString(it) } ?: getString(R.string.security_auth_title)
        val subtitle = subtitleResId?.let { getString(it) } ?: getString(R.string.security_auth_reason_change_settings)
        val promptInfo = BiometricPrompt.PromptInfo.Builder().setTitle(title).setSubtitle(subtitle).setAllowedAuthenticators(BIOMETRIC_STRONG or DEVICE_CREDENTIAL).build()
        biometricPrompt.authenticate(promptInfo)
    }
}

@Composable
fun SecurityWarningScreen() {
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.errorContainer) {
        Column(
            modifier = Modifier.fillMaxSize().padding(32.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(imageVector = Icons.Rounded.SecurityUpdateWarning, contentDescription = null, modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.error)
            Spacer(modifier = Modifier.height(24.dp))
            Text(text = stringResource(R.string.security_warning_title), style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onErrorContainer, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
            Spacer(modifier = Modifier.height(16.dp))
            Text(text = stringResource(R.string.security_warning_message), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onErrorContainer, textAlign = TextAlign.Center)
        }
    }
}

@Composable
fun LockScreen(onUnlockRequest: () -> Unit) {
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(imageVector = Icons.Rounded.Lock, contentDescription = null, modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.height(24.dp))
            Text(text = stringResource(R.string.main_lock_title), style = MaterialTheme.typography.titleLarge)
            Spacer(modifier = Modifier.height(32.dp))
            Button(onClick = onUnlockRequest) { Text(stringResource(R.string.main_lock_unlock_button)) }
        }
    }
}
