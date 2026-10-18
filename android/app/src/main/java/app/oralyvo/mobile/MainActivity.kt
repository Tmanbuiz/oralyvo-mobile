package app.oralyvo.mobile

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

// Placeholder root activity -- proves the app builds and launches end to
// end via Codemagic CI (no local Android emulator/device verification done
// from this session). Sign-in, recording, and transcription land in
// follow-up commits once this skeleton is confirmed green in CI. Kept to
// text-only Compose widgets here on purpose -- no icon library dependency
// has been added yet, so no icon references until one is.
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            OralyvoScaffold()
        }
    }
}

@Composable
fun OralyvoScaffold() {
    MaterialTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text("Oralyvo", style = MaterialTheme.typography.headlineMedium)
                Text(
                    "Android app scaffold — more to come.",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}
