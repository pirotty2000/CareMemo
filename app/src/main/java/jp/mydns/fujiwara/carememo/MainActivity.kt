package jp.mydns.fujiwara.carememo

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import jp.mydns.fujiwara.carememo.data.Category
import jp.mydns.fujiwara.carememo.ui.screens.DeletedUserListScreen
import jp.mydns.fujiwara.carememo.ui.screens.MainScreen
import jp.mydns.fujiwara.carememo.ui.screens.SettingsScreen
import jp.mydns.fujiwara.carememo.ui.screens.UnifiedRecordScreen
import jp.mydns.fujiwara.carememo.ui.theme.CareMemoTheme
import jp.mydns.fujiwara.carememo.utils.PdfExporter
import jp.mydns.fujiwara.carememo.viewmodel.PersonDetailViewModel
import jp.mydns.fujiwara.carememo.viewmodel.PersonListViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // アプリ起動時に古いPDFキャッシュを掃除
        PdfExporter.clearOldExports(this)

        enableEdgeToEdge()
        setContent {
            CareMemoTheme {
                CareMemoApp()
            }
        }
    }
}

@Composable
fun CareMemoApp() {
    val navController = rememberNavController()
    val context = LocalContext.current
    val application = context.applicationContext as CareMemoApplication
    val repository = application.repository
    val userSettingsRepository = application.userSettingsRepository

    NavHost(navController = navController, startDestination = "main") {
        composable("main") {
            val listViewModel: PersonListViewModel = viewModel(
                factory = PersonListViewModel.Factory(repository, userSettingsRepository)
            )
            MainScreen(
                viewModel = listViewModel,
                onNavigateToDetail = { personId, category ->
                    navController.navigate("detail/$personId/${category.name}")
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
            route = "detail/{personId}/{categoryName}",
            arguments = listOf(
                navArgument("personId") { type = NavType.IntType },
                navArgument("categoryName") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val personId = backStackEntry.arguments?.getInt("personId") ?: 0
            val categoryName = backStackEntry.arguments?.getString("categoryName") ?: Category.CONDITION_AT_VISIT.name
            val category = Category.valueOf(categoryName)
            
            val detailViewModel: PersonDetailViewModel = viewModel(
                factory = PersonDetailViewModel.Factory(repository, userSettingsRepository)
            )
            
            UnifiedRecordScreen(
                viewModel = detailViewModel,
                initialCategoryType = category,
                personId = personId,
                onBack = { navController.popBackStack() }
            )
        }
    }
}
