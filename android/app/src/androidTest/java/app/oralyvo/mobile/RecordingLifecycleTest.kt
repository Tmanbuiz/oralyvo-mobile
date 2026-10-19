package app.oralyvo.mobile

import android.Manifest
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.lifecycle.Lifecycle
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.GrantPermissionRule
import app.oralyvo.mobile.ui.RecordScreen
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RecordingLifecycleTest {
    @get:Rule val permission: GrantPermissionRule = GrantPermissionRule.grant(Manifest.permission.RECORD_AUDIO)
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    @Test fun backgroundingStopsRecordingAndKeepsClipForExplicitTranscription() {
        compose.activity.runOnUiThread {
            compose.activity.setContent { MaterialTheme { RecordScreen(onViewHistory = {}, onSignOut = {}) } }
        }
        compose.onNodeWithText("Start recording").performClick()
        compose.onNodeWithText("Stop recording").assertIsDisplayed()
        // MediaRecorder needs real elapsed audio time; Compose's virtual clock
        // cannot synthesize AAC frames.
        Thread.sleep(1500)
        compose.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
        compose.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
        compose.onNodeWithText("Transcribe recording").assertIsDisplayed()
        compose.onNodeWithText("Recording ready").assertIsDisplayed()
    }
}
