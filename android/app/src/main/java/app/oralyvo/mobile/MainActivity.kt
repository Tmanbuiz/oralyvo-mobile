package app.oralyvo.mobile

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import app.oralyvo.mobile.data.TokenStore
import app.oralyvo.mobile.ui.AuthScreen
import app.oralyvo.mobile.ui.HistoryScreen
import app.oralyvo.mobile.ui.RecordScreen

/**
 * Root activity. Wires the three screens built so far -- sign-in, record,
 * history -- together with Navigation Compose. The start destination is
 * decided once at launch from whatever TokenStore already has saved; there
 * is no splash/loading screen for that check since reading a couple of
 * SharedPreferences keys is effectively instant.
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            OralyvoApp()
        }
    }
}

private object Routes {
    const val AUTH = "auth"
    const val RECORD = "record"
    const val HISTORY = "history"
}

@Composable
fun OralyvoApp() {
    val context = LocalContext.current
    val tokenStore = remember { TokenStore(context) }
    val navController = rememberNavController()
    val startDestination = if (tokenStore.isSignedIn()) Routes.RECORD else Routes.AUTH

    MaterialTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            NavHost(navController = navController, startDestination = startDestination) {
                composable(Routes.AUTH) {
                    AuthScreen(
                        onAuthenticated = {
                            navController.navigate(Routes.RECORD) {
                                popUpTo(Routes.AUTH) { inclusive = true }
                            }
                        }
                    )
                }
                composable(Routes.RECORD) {
                    RecordScreen(
                        onViewHistory = { navController.navigate(Routes.HISTORY) },
                        onSignOut = {
                            navController.navigate(Routes.AUTH) {
                                popUpTo(Routes.RECORD) { inclusive = true }
                            }
                        }
                    )
                }
                composable(Routes.HISTORY) {
                    HistoryScreen(onBack = { navController.popBackStack() })
                }
            }
        }
    }
}
