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
import androidx.compose.material3.*
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import jp.mydns.fujiwara.carememo.data.ThemeSetting
import jp.mydns.fujiwara.carememo.ui.navigation.Destination
import jp.mydns.fujiwara.carememo.ui.screens.main.*
import jp.mydns.fujiwara.carememo.ui.screens.health.*
import jp.mydns.fujiwara.carememo.ui.screens.condition.*
import jp.mydns.fujiwara.carememo.ui.screens.medication.*
import jp.mydns.fujiwara.carememo.ui.screens.settings.*
import jp.mydns.fujiwara.carememo.ui.theme.CareMemoTheme
import jp.mydns.fujiwara.carememo.viewmodel.*

class MainActivity : FragmentActivity() {
    @OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
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

        enableEdgeToEdge()

        setContent {
            val themeSetting by userSettingsRepository.themeSetting.collectAsStateWithLifecycle(initialValue = ThemeSetting.SYSTEM)
            CareMemoTheme(themeSetting = themeSetting) {
                val navController = rememberNavController()
                val widthSizeClass = calculateWindowSizeClass(this).widthSizeClass
                var isAppLocked by rememberSaveable { mutableStateOf(false) }
                val isBiometricEnabled by userSettingsRepository.isBiometricEnabled.collectAsStateWithLifecycle(initialValue = false)
                val lockTimeoutMinutes by userSettingsRepository.lockTimeoutMinutes.collectAsStateWithLifecycle(initialValue = 5)
                var lastPausedTime by rememberSaveable { mutableLongStateOf(0L) }

                val requestAuthentication: (Int?, Int?, () -> Unit) -> Unit = { titleResId, subtitleResId, onSuccess ->
                    authenticate(titleResId, subtitleResId, onSuccess)
                }

                DisposableEffect(Lifecycle.Event.ON_PAUSE, Lifecycle.Event.ON_RESUME) {
                    val observer = LifecycleEventObserver { _, event ->
                        if (event == Lifecycle.Event.ON_PAUSE) {
                            lastPausedTime = System.currentTimeMillis()
                        } else if (event == Lifecycle.Event.ON_RESUME) {
                            if (isBiometricEnabled && !userSettingsRepository.isLockBypassed) {
                                if (lockTimeoutMinutes == 0) isAppLocked = true
                                else if (lockTimeoutMinutes > 0) {
                                    val elapsedMillis = System.currentTimeMillis() - lastPausedTime
                                    if (elapsedMillis > lockTimeoutMinutes * 60 * 1000) isAppLocked = true
                                }
                            }
                            userSettingsRepository.isLockBypassed = false
                        }
                    }
                    lifecycle.addObserver(observer)
                    onDispose { lifecycle.removeObserver(observer) }
                }

                if (isAppLocked) {
                    // ロック画面を表示
                    LockScreen(onUnlockRequest = { authenticate(onSuccess = { isAppLocked = false }) })
                } else {
                    NavHost(navController = navController, startDestination = Destination.Main) {

                        // SCR-M-001 利用者一覧画面
                        composable<Destination.Main> {
                            val listViewModel: PersonListViewModel =
                                viewModel(factory = PersonListViewModel.Factory(
                                    personRepository,
                                    deleteOrRestorePersonRepository,
                                    personSummaryRepository,
                                    conditionRepository,
                                    application.emergencyContactRepository,
                                    userSettingsRepository,
                                    auditLogRepository))
                            MainScreen(viewModel = listViewModel, navController = navController)
                        }

                        // SCR-M-002 利用者登録・編集画面
                        composable<Destination.PersonEdit> {
                            val editViewModel: PersonEditViewModel =
                                viewModel(factory = PersonEditViewModel.Factory(
                                    personRepository,
                                    userSettingsRepository,
                                    auditLogRepository))
                            PersonEditScreen(viewModel = editViewModel, navController = navController)
                        }

                        // SCR-M-003 緊急連絡先・管理画面
                        composable<Destination.MedicalContacts> {
                            val medicalViewModel: EmergencyContactEditViewModel =
                                viewModel(factory = EmergencyContactEditViewModel.Factory(
                                    application.emergencyContactRepository,
                                    personRepository,
                                    userSettingsRepository,
                                    auditLogRepository))
                            EmergencyContactListScreen(viewModel = medicalViewModel, navController = navController)
                        }

                        // SCR-M-004 緊急連絡先・登録編集画面
                        composable<Destination.MedicalContactEdit> {
                            val medicalViewModel: EmergencyContactEditViewModel =
                                viewModel(factory = EmergencyContactEditViewModel.Factory(
                                    application.emergencyContactRepository,
                                    personRepository,
                                    userSettingsRepository,
                                    auditLogRepository))
                            EmergencyContactEditScreen(viewModel = medicalViewModel, navController = navController)
                        }

                        // SCR-PH-001 健康管理(身長・体重、バイタル、血糖値・HbA1c)
                        composable<Destination.HealthDetail> {
                            val detailViewModel: PersonDetailUiStateViewModel =
                                viewModel(factory = PersonDetailUiStateViewModel.Factory(
                                    personRepository,
                                    personSummaryRepository,
                                    userSettingsRepository,
                                    auditLogRepository))
                            val healthViewModel: PersonHealthViewModel =
                                viewModel(factory = PersonHealthViewModel.Factory(
                                    personRepository,
                                    personSummaryRepository,
                                    healthRepository,
                                    userSettingsRepository,
                                    auditLogRepository))
                            PersonHealthScreen(
                                detailViewModel = detailViewModel,
                                healthViewModel = healthViewModel,
                                navController = navController,
                                widthSizeClass = widthSizeClass,
                                onRequireAuthentication = requestAuthentication)
                        }

                        // SCR-PH-002 一括入力
                        composable<Destination.BatchInput> {
                            val batchViewModel: BatchInputViewModel =
                                viewModel(factory = BatchInputViewModel.Factory(
                                    personRepository,
                                    personSummaryRepository,
                                    healthRepository,
                                    userSettingsRepository,
                                    auditLogRepository))
                            BatchInputScreen(viewModel = batchViewModel, navController = navController)
                        }

                        // SCR-PH-003 グラフ拡大表示
                        composable<Destination.GraphExpansion> { backStackEntry ->
                            val args = backStackEntry.toRoute<Destination.GraphExpansion>()
                            val detailViewModel: PersonDetailUiStateViewModel =
                                viewModel(factory = PersonDetailUiStateViewModel.Factory(
                                    personRepository,
                                    personSummaryRepository,
                                    userSettingsRepository,
                                    auditLogRepository))
                            val healthViewModel: PersonHealthViewModel =
                                viewModel(factory = PersonHealthViewModel.Factory(
                                    personRepository,
                                    personSummaryRepository,
                                    healthRepository,
                                    userSettingsRepository,
                                    auditLogRepository))
                            GraphExpansionScreen(
                                detailViewModel = detailViewModel,
                                healthViewModel = healthViewModel,
                                initialGraphIndex = args.initialIndex,
                                navController = navController)
                        }

                        // SCR-PC-001 所見メモ
                        composable<Destination.ConditionDetail> {
                            val detailViewModel: PersonDetailUiStateViewModel =
                                viewModel(factory = PersonDetailUiStateViewModel.Factory(
                                    personRepository,
                                    personSummaryRepository,
                                    userSettingsRepository,
                                    auditLogRepository))
                            val conditionViewModel: PersonConditionViewModel =
                                viewModel(factory = PersonConditionViewModel.Factory(
                                    personRepository,
                                    personSummaryRepository,
                                    conditionRepository,
                                    userSettingsRepository,
                                    auditLogRepository,
                                    applicationContext))
                            PersonConditionScreen(
                                detailViewModel = detailViewModel,
                                conditionViewModel = conditionViewModel,
                                navController = navController,
                                widthSizeClass = widthSizeClass,
                                onRequireAuthentication = requestAuthentication)
                        }

                        // SCR-PC-002 所見メモ・写真プレビュー画面
                        composable<Destination.PhotoPreview> {
                            val detailViewModel: PersonDetailUiStateViewModel =
                                viewModel(factory = PersonDetailUiStateViewModel.Factory(
                                    personRepository,
                                    personSummaryRepository,
                                    userSettingsRepository,
                                    auditLogRepository))
                            val conditionViewModel: PersonConditionViewModel =
                                viewModel(factory = PersonConditionViewModel.Factory(
                                    personRepository,
                                    personSummaryRepository,
                                    conditionRepository,
                                    userSettingsRepository,
                                    auditLogRepository,
                                    applicationContext))
                            ConditionPhotoPreviewScreen(
                                detailViewModel = detailViewModel,
                                conditionViewModel = conditionViewModel,
                                navController = navController)
                        }

                        // SCR-PC-003 所見メモ・写真全画面表示
                        @Suppress("UNUSED_VARIABLE")
                        composable<Destination.PhotoFull> {
                            val conditionViewModel: PersonConditionViewModel =
                                viewModel(factory = PersonConditionViewModel.Factory(
                                    personRepository,
                                    personSummaryRepository,
                                    conditionRepository,
                                    userSettingsRepository,
                                    auditLogRepository,
                                    applicationContext))
                            ConditionPhotoFullScreen(viewModel = conditionViewModel, navController = navController)
                        }

                        // SCR-PM-001 服薬管理
                        composable<Destination.MedicationDetail> {
                            val detailViewModel: PersonDetailUiStateViewModel =
                                viewModel(factory = PersonDetailUiStateViewModel.Factory(
                                    personRepository,
                                    personSummaryRepository,
                                    userSettingsRepository,
                                    auditLogRepository))
                            val medicationViewModel: PersonMedicationViewModel =
                                viewModel(factory = PersonMedicationViewModel.Factory(
                                    personRepository,
                                    personSummaryRepository,
                                    medicationRepository,
                                    userSettingsRepository,
                                    auditLogRepository))
                            PersonMedicationScreen(
                                detailViewModel = detailViewModel,
                                medicationViewModel = medicationViewModel,
                                navController = navController,
                                widthSizeClass = widthSizeClass,
                                onRequireAuthentication = requestAuthentication)
                        }

                        // SCR-S-001 設定
                        composable<Destination.Settings> {
                            val settingsViewModel: SettingsViewModel =
                                viewModel(factory = SettingsViewModel.Factory(
                                    appMaintenanceRepository,
                                    deleteOrRestorePersonRepository,
                                    auditLogRepository,
                                    userSettingsRepository))
                            SettingsScreen(
                                viewModel = settingsViewModel,
                                navController = navController,
                                onRequireAuthentication = requestAuthentication)
                        }

                        // SCR-S-002 設定・監査ログ
                        composable<Destination.AuditLog> {
                            val auditLogViewModel: AuditLogViewModel =
                                viewModel(factory = AuditLogViewModel.Factory(
                                    auditLogRepository,
                                    userSettingsRepository))
                            AuditLogScreen(viewModel = auditLogViewModel, navController = navController)
                        }

                        // SCR-S-003 設定・終了利用者管理
                        composable<Destination.ArchiveManagement> {
                            val archiveViewModel: DeleteOrRestorePersonViewModel =
                                viewModel(factory = DeleteOrRestorePersonViewModel.Factory(
                                    deleteOrRestorePersonRepository,
                                    userSettingsRepository,
                                    auditLogRepository))
                            DeleteOrRestorePersonScreen(viewModel = archiveViewModel, navController = navController)
                        }

                        // SCR-S-004 設定・迷子写真管理
                        composable<Destination.OrphanedPhotos> {
                            val orphanedViewModel: OrphanedPhotoViewModel =
                                viewModel(factory = OrphanedPhotoViewModel.Factory(
                                    userSettingsRepository,
                                    conditionRepository,
                                    applicationContext))
                            OrphanedPhotoManagementScreen(viewModel = orphanedViewModel, navController = navController)
                        }
                    }
                }
            }
        }
    }

    /**
     * セキュリティ保護の実行、認証方式の統合管理、コールバック制御
     */
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
