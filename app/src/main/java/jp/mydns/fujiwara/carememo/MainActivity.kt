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
 * Class：MainActivity
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
                
                val lockTimeoutMinutes by userSettingsRepository.lockTimeoutMinutes.collectAsStateWithLifecycle(initialValue = 5)
                var lastPausedTime by rememberSaveable { mutableLongStateOf(0L) }

                val requestAuthentication: (Int?, Int?, () -> Unit) -> Unit = { titleResId, subtitleResId, onSuccess ->
                    this@MainActivity.authenticate(titleResId, subtitleResId, onSuccess)
                }

                DisposableEffect(Lifecycle.Event.ON_PAUSE, Lifecycle.Event.ON_RESUME) {
                    val observer = LifecycleEventObserver { _, event ->
                        if (event == Lifecycle.Event.ON_PAUSE) {
                            lastPausedTime = System.currentTimeMillis()
                        } else if (event == Lifecycle.Event.ON_RESUME) {
                            if (isAuthenticated && (biometricEnabledState == true) && !userSettingsRepository.isLockBypassed) {
                                if (lockTimeoutMinutes == 0) {
                                    isAuthenticated = false
                                } else if (lockTimeoutMinutes > 0) {
                                    val elapsedMillis = System.currentTimeMillis() - lastPausedTime
                                    if (elapsedMillis > lockTimeoutMinutes * 60 * 1000) {
                                        isAuthenticated = false
                                    }
                                }
                            }
                            userSettingsRepository.isLockBypassed = false
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
                                        auditLogRepository))
                                MainScreen(viewModel = listViewModel, navController = navController)
                            }

                            composable<Destination.PersonEdit> {
                                val editViewModel: PersonEditViewModel =
                                    viewModel(factory = PersonEditViewModel.Factory(
                                        personRepository,
                                        userSettingsRepository,
                                        auditLogRepository))
                                PersonEditScreen(viewModel = editViewModel, navController = navController)
                            }

                            composable<Destination.MedicalContacts> {
                                val medicalViewModel: EmergencyContactEditViewModel =
                                    viewModel(factory = EmergencyContactEditViewModel.Factory(
                                        application.emergencyContactRepository,
                                        personRepository,
                                        userSettingsRepository,
                                        auditLogRepository))
                                EmergencyContactListScreen(viewModel = medicalViewModel, navController = navController)
                            }

                            composable<Destination.MedicalContactEdit> {
                                val medicalViewModel: EmergencyContactEditViewModel =
                                    viewModel(factory = EmergencyContactEditViewModel.Factory(
                                        application.emergencyContactRepository,
                                        personRepository,
                                        userSettingsRepository,
                                        auditLogRepository))
                                EmergencyContactEditScreen(viewModel = medicalViewModel, navController = navController)
                            }

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
                                            auditLogRepository))
                                    val conditionViewModel: PersonConditionViewModel =
                                        viewModel(parentEntry, factory = PersonConditionViewModel.Factory(
                                            personRepository,
                                            personSummaryRepository,
                                            conditionRepository,
                                            userSettingsRepository,
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
                                            auditLogRepository))
                                    val conditionViewModel: PersonConditionViewModel =
                                        viewModel(parentEntry, factory = PersonConditionViewModel.Factory(
                                            personRepository,
                                            personSummaryRepository,
                                            conditionRepository,
                                            userSettingsRepository,
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
                                            auditLogRepository))

                                    // 引数の同期
                                    LaunchedEffect(args) {
                                        conditionViewModel.setNavContext(
                                            personId = args.personId,
                                            conditionId = args.conditionId
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
                                    onRequireAuthentication = requestAuthentication,
                                    onCheckBiometricSupport = { this@MainActivity.isBiometricSupported() })
                            }

                            composable<Destination.AuditLog> {
                                val auditLogViewModel: AuditLogViewModel =
                                    viewModel(factory = AuditLogViewModel.Factory(
                                        auditLogRepository,
                                        userSettingsRepository))
                                AuditLogScreen(viewModel = auditLogViewModel, navController = navController)
                            }

                            composable<Destination.ArchiveManagement> {
                                val archiveViewModel: DeleteOrRestorePersonViewModel =
                                    viewModel(factory = DeleteOrRestorePersonViewModel.Factory(
                                        deleteOrRestorePersonRepository,
                                        userSettingsRepository,
                                        auditLogRepository))
                                DeleteOrRestorePersonScreen(viewModel = archiveViewModel, navController = navController)
                            }

                            composable<Destination.UnassignedPhotos> {
                                val unassignedViewModel: UnassignedPhotoViewModel =
                                    viewModel(factory = UnassignedPhotoViewModel.Factory(
                                        userSettingsRepository,
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
