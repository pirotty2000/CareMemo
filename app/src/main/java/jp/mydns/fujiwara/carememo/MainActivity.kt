package jp.mydns.fujiwara.carememo

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import jp.mydns.fujiwara.carememo.data.Category
import jp.mydns.fujiwara.carememo.ui.screens.MainScreen
import jp.mydns.fujiwara.carememo.ui.screens.UnifiedRecordScreen
import jp.mydns.fujiwara.carememo.ui.theme.CareMemoTheme
import jp.mydns.fujiwara.carememo.viewmodel.PersonDetailViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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

    NavHost(navController = navController, startDestination = "main") {
        composable("main") {
            MainScreen(
                onNavigateToDetail = { personId, category ->
                    navController.navigate("detail/$personId/${category.name}")
                }
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
            val detailViewModel: PersonDetailViewModel = viewModel()
            
            // 画面遷移確認用のサンプルデータをロード
            LaunchedEffect(category) {
                detailViewModel.loadSampleData(category)
            }

            UnifiedRecordScreen(
                viewModel = detailViewModel,
                initialCategoryType = category,
                personId = personId,
                onBack = { navController.popBackStack() }
            )
        }
    }
}
