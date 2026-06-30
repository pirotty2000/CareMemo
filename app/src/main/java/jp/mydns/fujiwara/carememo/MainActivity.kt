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
import jp.mydns.fujiwara.carememo.ui.screens.MainScreen
import jp.mydns.fujiwara.carememo.ui.screens.BatchInputScreen
import jp.mydns.fujiwara.carememo.ui.screens.DeletedUserListScreen
import jp.mydns.fujiwara.carememo.ui.screens.SettingsScreen
import jp.mydns.fujiwara.carememo.ui.screens.detail.*
import jp.mydns.fujiwara.carememo.ui.screens.detail.sub.*
import jp.mydns.fujiwara.carememo.ui.theme.CareMemoTheme
import jp.mydns.fujiwara.carememo.utils.PdfExporter
import jp.mydns.fujiwara.carememo.viewmodel.PersonDetailViewModel
import jp.mydns.fujiwara.carememo.viewmodel.BatchInputViewModel
import jp.mydns.fujiwara.carememo.viewmodel.PersonConditionViewModel
import jp.mydns.fujiwara.carememo.viewmodel.HealthRecordViewModel
import jp.mydns.fujiwara.carememo.viewmodel.PersonListViewModel
import jp.mydns.fujiwara.carememo.viewmodel.MedicationViewModel
import jp.mydns.fujiwara.carememo.viewmodel.SettingsViewModel
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
    val healthRepository = application.healthRepository
    val conditionRepository = application.conditionRepository
    val medicationRepository = application.medicationRepository
    val userSettingsRepository = application.userSettingsRepository
    val themeSetting by userSettingsRepository.themeSetting.collectAsState(initial = ThemeSetting.SYSTEM)

    CareMemoTheme(themeSetting = themeSetting) {
        val navController = rememberNavController()
        val scope = rememberCoroutineScope()

        val isBiometricEnabled by userSettingsRepository.isBiometricEnabled.collectAsState(initial = null)
        val lockTimeoutMinutes by userSettingsRepository.lockTimeoutMinutes.collectAsState(initial = 0)
        val lastActiveTime by userSettingsRepository.lastActiveTime.collectAsState(initial = 0L)
        
        var isAuthenticated by rememberSaveable { mutableStateOf(false) }

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

        LaunchedEffect(isBiometricEnabled, isAuthenticated) {
            if ((isBiometricEnabled == true) && !isAuthenticated) {
                val executor = ContextCompat.getMainExecutor(activity)
                val biometricPrompt = BiometricPrompt(activity, executor, object : BiometricPrompt.AuthenticationCallback() {
                    override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                        super.onAuthenticationSucceeded(result)
                        isAuthenticated = true
                    }
                    override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                        super.onAuthenticationError(errorCode, errString)
                        if (errorCode == BiometricPrompt.ERROR_USER_CANCELED || errorCode == BiometricPrompt.ERROR_NEGATIVE_BUTTON || errorCode == BiometricPrompt.ERROR_LOCKOUT || errorCode == BiometricPrompt.ERROR_LOCKOUT_PERMANENT || errorCode == BiometricPrompt.ERROR_NO_BIOMETRICS || errorCode == BiometricPrompt.ERROR_HW_NOT_PRESENT) {
                            activity.finish()
                        }
                    }
                })
                val promptInfo = BiometricPrompt.PromptInfo.Builder().setTitle("アプリ・ロック").setSubtitle("認証情報を入力してください").setAllowedAuthenticators(BIOMETRIC_STRONG or DEVICE_CREDENTIAL).build()
                biometricPrompt.authenticate(promptInfo)
            } else if (isBiometricEnabled == false) {
                isAuthenticated = true
            }
        }

        if (isAuthenticated || isBiometricEnabled == false) {
            NavHost(navController = navController, startDestination = "main") {
                composable("main") {
                    val listViewModel: PersonListViewModel = viewModel(factory = PersonListViewModel.Factory(personRepository, conditionRepository, userSettingsRepository))
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
                        onNavigateToSettings = { navController.navigate("settings") }
                    )
                }
                composable("restore") {
                    val listViewModel: PersonListViewModel = viewModel(factory = PersonListViewModel.Factory(personRepository, conditionRepository, userSettingsRepository))
                    DeletedUserListScreen(viewModel = listViewModel, onBack = { navController.popBackStack() })
                }
                composable("batch_input/{personId}", arguments = listOf(navArgument("personId") { type = NavType.IntType })) { backStackEntry ->
                    val personId = backStackEntry.arguments?.getInt("personId") ?: 0
                    val batchViewModel: BatchInputViewModel = viewModel(factory = BatchInputViewModel.Factory(personRepository, healthRepository, userSettingsRepository))
                    BatchInputScreen(viewModel = batchViewModel, personId = personId, onBack = { navController.popBackStack() })
                }
                composable("settings") {
                    val settingsViewModel: SettingsViewModel = viewModel(factory = SettingsViewModel.Factory(personRepository, userSettingsRepository))
                    SettingsScreen(viewModel = settingsViewModel, onNavigateToRestore = { navController.navigate("restore") }, onBack = { navController.popBackStack() })
                }
                composable("medication/{personId}", arguments = listOf(navArgument("personId") { type = NavType.IntType })) { backStackEntry ->
                    val personId = backStackEntry.arguments?.getInt("personId") ?: 0
                    val medicationViewModel: MedicationViewModel = viewModel(factory = MedicationViewModel.Factory(personRepository, medicationRepository, userSettingsRepository))
                    MedicationScreen(viewModel = medicationViewModel, personId = personId, widthSizeClass = widthSizeClass, onBack = { navController.popBackStack("main", inclusive = false) }, onNavigateToCategory = { category ->
                        navController.navigate(category.getRoute(personId)) { popUpTo("main") { saveState = true }; launchSingleTop = true; restoreState = true }
                    })
                }
                composable("observation/{personId}?query={query}", arguments = listOf(
                    navArgument("personId") { type = NavType.IntType },
                    navArgument("query") { type = NavType.StringType; nullable = true; defaultValue = "" }
                )) { backStackEntry ->
                    val personId = backStackEntry.arguments?.getInt("personId") ?: 0
                    val initialQuery = backStackEntry.arguments?.getString("query")?.let { if (it.isNotBlank()) URLDecoder.decode(it, StandardCharsets.UTF_8.toString()) else "" } ?: ""
                    val detailViewModel: PersonDetailViewModel = viewModel(factory = PersonDetailViewModel.Factory(personRepository, userSettingsRepository))
                    val conditionViewModel: PersonConditionViewModel = viewModel(factory = PersonConditionViewModel.Factory(personRepository, conditionRepository, userSettingsRepository))
                    LaunchedEffect(initialQuery) { if (initialQuery.isNotBlank()) conditionViewModel.updateSearchQuery(initialQuery) }
                    
                    ConditionDetailScreen(
                        viewModel = detailViewModel,
                        conditionViewModel = conditionViewModel,
                        personId = personId,
                        widthSizeClass = widthSizeClass,
                        onBack = { navController.popBackStack("main", inclusive = false) },
                        onNavigateToCategory = { category ->
                            navController.navigate(category.getRoute(personId)) { popUpTo("main") { saveState = true }; launchSingleTop = true; restoreState = true }
                        },
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
                composable("detail/{personId}/{categoryName}?query={query}", arguments = listOf(navArgument("personId") { type = NavType.IntType }, navArgument("categoryName") { type = NavType.StringType }, navArgument("query") { type = NavType.StringType; nullable = true; defaultValue = "" })) { backStackEntry ->
                    val personId = backStackEntry.arguments?.getInt("personId") ?: 0
                    val categoryName = backStackEntry.arguments?.getString("categoryName") ?: Category.BP_AND_PULSE.name
                    val category = Category.valueOf(categoryName)
                    val initialQuery = backStackEntry.arguments?.getString("query")?.let { if (it.isNotBlank()) URLDecoder.decode(it, StandardCharsets.UTF_8.toString()) else "" } ?: ""
                    val detailViewModel: PersonDetailViewModel = viewModel(factory = PersonDetailViewModel.Factory(personRepository, userSettingsRepository))
                    val conditionViewModel: PersonConditionViewModel = viewModel(factory = PersonConditionViewModel.Factory(personRepository, conditionRepository, userSettingsRepository))
                    val healthViewModel: HealthRecordViewModel = viewModel(factory = HealthRecordViewModel.Factory(personRepository, healthRepository, userSettingsRepository))
                    LaunchedEffect(initialQuery) { if (initialQuery.isNotBlank()) conditionViewModel.updateSearchQuery(initialQuery) }
                    UnifiedRecordScreen(
                        viewModel = detailViewModel,
                        conditionViewModel = conditionViewModel,
                        healthViewModel = healthViewModel,
                        initialCategoryType = category,
                        personId = personId,
                        widthSizeClass = widthSizeClass,
                        onBack = { navController.popBackStack("main", inclusive = false) },
                        onNavigateToGraphExpansion = { pId, cat, index -> navController.navigate("graphExpansion/$pId/${cat.name}/$index") },
                        onNavigateToCategory = { cat ->
                            navController.navigate(cat.getRoute(personId)) { 
                                popUpTo("main") { saveState = true }
                                launchSingleTop = true
                                restoreState = true 
                            }
                        }
                    )
                }
                composable("graphExpansion/{personId}/{categoryName}/{initialIndex}", arguments = listOf(navArgument("personId") { type = NavType.IntType }, navArgument("categoryName") { type = NavType.StringType }, navArgument("initialIndex") { type = NavType.IntType })) { backStackEntry ->
                    val personId = backStackEntry.arguments?.getInt("personId") ?: 0
                    val categoryName = backStackEntry.arguments?.getString("categoryName") ?: ""
                    val category = Category.valueOf(categoryName)
                    val initialIndex = backStackEntry.arguments?.getInt("initialIndex") ?: 0
                    val healthViewModel: HealthRecordViewModel = viewModel(factory = HealthRecordViewModel.Factory(personRepository, healthRepository, userSettingsRepository))
                    val detailViewModel: PersonDetailViewModel = viewModel(factory = PersonDetailViewModel.Factory(personRepository, userSettingsRepository))
                    GraphExpansionScreen(viewModel = detailViewModel, healthViewModel = healthViewModel, personId = personId, category = category, initialGraphIndex = initialIndex, onBack = { navController.popBackStack() })
                }
                composable("photoPreview/{uri}/{personId}/{conditionId}", arguments = listOf(navArgument("uri") { type = NavType.StringType }, navArgument("personId") { type = NavType.IntType }, navArgument("conditionId") { type = NavType.IntType })) { backStackEntry ->
                    val uri = Uri.parse(Uri.decode(backStackEntry.arguments?.getString("uri") ?: ""))
                    val personId = backStackEntry.arguments?.getInt("personId") ?: 0
                    val conditionId = backStackEntry.arguments?.getInt("conditionId") ?: 0
                    val conditionViewModel: PersonConditionViewModel = viewModel(factory = PersonConditionViewModel.Factory(personRepository, conditionRepository, userSettingsRepository))
                    val detailViewModel: PersonDetailViewModel = viewModel(factory = PersonDetailViewModel.Factory(personRepository, userSettingsRepository))
                    ConditionPhotoPreviewScreen(viewModel = detailViewModel, conditionViewModel = conditionViewModel, uri = uri, personId = personId, conditionId = conditionId, onBack = { navController.popBackStack() }, onSaved = { navController.popBackStack() })
                }
                composable("photoFull/{fileName}?caption={caption}", arguments = listOf(navArgument("fileName") { type = NavType.StringType }, navArgument("caption") { type = NavType.StringType; nullable = true; defaultValue = null })) { backStackEntry ->
                    val fileName = backStackEntry.arguments?.getString("fileName") ?: ""
                    val caption = backStackEntry.arguments?.getString("caption")
                    ConditionPhotoFullScreen(fileName = fileName, caption = caption, onBack = { navController.popBackStack() })
                }
            }
        }
    }
}
