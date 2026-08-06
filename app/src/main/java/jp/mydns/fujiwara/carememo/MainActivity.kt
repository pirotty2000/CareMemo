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
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
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
            val themeSetting by userSettingsRepository.themeSetting.collectAsState(initial = ThemeSetting.SYSTEM)
            CareMemoTheme(themeSetting = themeSetting) {
                val navController = rememberNavController()
                val widthSizeClass = calculateWindowSizeClass(this).widthSizeClass
                var isAppLocked by rememberSaveable { mutableStateOf(false) }
                val isBiometricEnabled by userSettingsRepository.isBiometricEnabled.collectAsState(initial = false)
                val lockTimeoutMinutes by userSettingsRepository.lockTimeoutMinutes.collectAsState(initial = 5)
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
                    LockScreen(onUnlockRequest = { authenticate(onSuccess = { isAppLocked = false }) })
                } else {
                    NavHost(navController = navController, startDestination = Destination.Main) {

                        composable<Destination.Main> {
                            val listViewModel: PersonListViewModel = viewModel(factory = PersonListViewModel.Factory(personRepository, deleteOrRestorePersonRepository, personSummaryRepository, conditionRepository, application.emergencyContactRepository, userSettingsRepository, auditLogRepository))
                            MainScreen(viewModel = listViewModel, navController = navController)
                        }

                        composable<Destination.PersonEdit> {
                            val editViewModel: PersonEditViewModel = viewModel(factory = PersonEditViewModel.Factory(personRepository, userSettingsRepository, auditLogRepository))
                            PersonEditScreen(viewModel = editViewModel, navController = navController)
                        }

                        composable<Destination.MedicalContacts> {
                            val medicalViewModel: EmergencyContactEditViewModel = viewModel(factory = EmergencyContactEditViewModel.Factory(application.emergencyContactRepository, personRepository, userSettingsRepository, auditLogRepository))
                            EmergencyContactListScreen(viewModel = medicalViewModel, navController = navController)
                        }

                        composable<Destination.MedicalContactEdit> {
                            val medicalViewModel: EmergencyContactEditViewModel = viewModel(factory = EmergencyContactEditViewModel.Factory(application.emergencyContactRepository, personRepository, userSettingsRepository, auditLogRepository))
                            EmergencyContactEditScreen(viewModel = medicalViewModel, navController = navController)
                        }

                        composable<Destination.HealthDetail> {
                            val detailViewModel: PersonDetailUiStateViewModel = viewModel(factory = PersonDetailUiStateViewModel.Factory(personRepository, personSummaryRepository, userSettingsRepository, auditLogRepository))
                            val healthViewModel: PersonHealthViewModel = viewModel(factory = PersonHealthViewModel.Factory(personRepository, personSummaryRepository, healthRepository, userSettingsRepository, auditLogRepository))
                            PersonHealthScreen(detailViewModel = detailViewModel, healthViewModel = healthViewModel, navController = navController, widthSizeClass = widthSizeClass, onRequireAuthentication = requestAuthentication)
                        }

                        composable<Destination.BatchInput> {
                            val batchViewModel: BatchInputViewModel = viewModel(factory = BatchInputViewModel.Factory(personRepository, personSummaryRepository, healthRepository, userSettingsRepository, auditLogRepository))
                            BatchInputScreen(viewModel = batchViewModel, navController = navController)
                        }

                        composable<Destination.GraphExpansion> { backStackEntry ->
                            val args = backStackEntry.toRoute<Destination.GraphExpansion>()
                            val detailViewModel: PersonDetailUiStateViewModel = viewModel(factory = PersonDetailUiStateViewModel.Factory(personRepository, personSummaryRepository, userSettingsRepository, auditLogRepository))
                            val healthViewModel: PersonHealthViewModel = viewModel(factory = PersonHealthViewModel.Factory(personRepository, personSummaryRepository, healthRepository, userSettingsRepository, auditLogRepository))
                            GraphExpansionScreen(detailViewModel = detailViewModel, healthViewModel = healthViewModel, initialGraphIndex = args.initialIndex, navController = navController)
                        }

                        composable<Destination.ConditionDetail> {
                            val detailViewModel: PersonDetailUiStateViewModel = viewModel(factory = PersonDetailUiStateViewModel.Factory(personRepository, personSummaryRepository, userSettingsRepository, auditLogRepository))
                            val conditionViewModel: PersonConditionViewModel = viewModel(factory = PersonConditionViewModel.Factory(personRepository, personSummaryRepository, conditionRepository, userSettingsRepository, auditLogRepository, applicationContext))
                            PersonConditionScreen(detailViewModel = detailViewModel, conditionViewModel = conditionViewModel, navController = navController, widthSizeClass = widthSizeClass, onRequireAuthentication = requestAuthentication)
                        }

                        composable<Destination.PhotoPreview> {
                            val detailViewModel: PersonDetailUiStateViewModel = viewModel(factory = PersonDetailUiStateViewModel.Factory(personRepository, personSummaryRepository, userSettingsRepository, auditLogRepository))
                            val conditionViewModel: PersonConditionViewModel = viewModel(factory = PersonConditionViewModel.Factory(personRepository, personSummaryRepository, conditionRepository, userSettingsRepository, auditLogRepository, applicationContext))
                            ConditionPhotoPreviewScreen(detailViewModel = detailViewModel, conditionViewModel = conditionViewModel, navController = navController)
                        }

                        @Suppress("UNUSED_VARIABLE")
                        composable<Destination.PhotoFull> {
                            val conditionViewModel: PersonConditionViewModel = viewModel(factory = PersonConditionViewModel.Factory(personRepository, personSummaryRepository, conditionRepository, userSettingsRepository, auditLogRepository, applicationContext))
                            ConditionPhotoFullScreen(viewModel = conditionViewModel, navController = navController)
                        }

                        composable<Destination.MedicationDetail> {
                            val detailViewModel: PersonDetailUiStateViewModel = viewModel(factory = PersonDetailUiStateViewModel.Factory(personRepository, personSummaryRepository, userSettingsRepository, auditLogRepository))
                            val medicationViewModel: PersonMedicationViewModel = viewModel(factory = PersonMedicationViewModel.Factory(personRepository, personSummaryRepository, medicationRepository, userSettingsRepository, auditLogRepository))
                            PersonMedicationScreen(detailViewModel = detailViewModel, medicationViewModel = medicationViewModel, navController = navController, widthSizeClass = widthSizeClass, onRequireAuthentication = requestAuthentication)
                        }

                        composable<Destination.Settings> {
                            val settingsViewModel: SettingsViewModel = viewModel(factory = SettingsViewModel.Factory(appMaintenanceRepository, deleteOrRestorePersonRepository, auditLogRepository, userSettingsRepository))
                            SettingsScreen(viewModel = settingsViewModel, navController = navController, onRequireAuthentication = requestAuthentication)
                        }

                        composable<Destination.AuditLog> {
                            val auditLogViewModel: AuditLogViewModel = viewModel(factory = AuditLogViewModel.Factory(auditLogRepository, userSettingsRepository))
                            AuditLogScreen(viewModel = auditLogViewModel, navController = navController)
                        }

                        composable<Destination.ArchiveManagement> {
                            val archiveViewModel: DeleteOrRestorePersonViewModel = viewModel(factory = DeleteOrRestorePersonViewModel.Factory(deleteOrRestorePersonRepository, userSettingsRepository, auditLogRepository))
                            DeleteOrRestorePersonScreen(viewModel = archiveViewModel, navController = navController)
                        }

                        composable<Destination.OrphanedPhotos> {
                            val orphanedViewModel: OrphanedPhotoViewModel = viewModel(factory = OrphanedPhotoViewModel.Factory(userSettingsRepository, conditionRepository, applicationContext))
                            OrphanedPhotoManagementScreen(viewModel = orphanedViewModel, navController = navController)
                        }
                    }
                }
            }
        }
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
fun LockScreen(onUnlockRequest: () -> Unit) {
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(imageVector = Icons.Rounded.Lock, contentDescription = null, modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.height(24.dp))
            Text(text = "アプリがロックされています", style = MaterialTheme.typography.titleLarge)
            Spacer(modifier = Modifier.height(32.dp))
            Button(onClick = onUnlockRequest) { Text("解除する") }
        }
    }
}
