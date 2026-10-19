package app.oralyvo.mobile

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.oralyvo.mobile.data.AuthSession
import app.oralyvo.mobile.data.HistoryStore
import app.oralyvo.mobile.data.TokenStore
import app.oralyvo.mobile.data.TranscriptEntry
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class AppSmokeTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    @Test fun launchShowsUsableSignInControls() {
        compose.onNodeWithText("Oralyvo").assertIsDisplayed()
        compose.onNodeWithText("Reload sign-in").assertIsDisplayed()
    }
    @Test fun sessionsAreEncryptedAndHistoryIsAccountScoped() {
        val context = compose.activity.applicationContext
        val store = TokenStore(context)
        val first = UUID.randomUUID().toString()
        val second = UUID.randomUUID().toString()
        try {
            store.save(AuthSession("unique-private-access", "unique-private-refresh", first, "test@example.invalid", 9999999999L))
            assertEquals(first, store.session()?.userId)
            val raw = File(context.noBackupFilesDir, "oralyvo-session.enc").readBytes().toString(Charsets.ISO_8859_1)
            assertFalse(raw.contains("unique-private-access"))
            assertFalse(raw.contains("unique-private-refresh"))
            HistoryStore(context, first).add(TranscriptEntry("test-id", 1, "First account only"))
            assertEquals(1, HistoryStore(context, first).all().size)
            assertTrue(HistoryStore(context, second).all().isEmpty())
            store.clear()
            assertNull(store.session())
        } finally {
            store.clear()
            File(context.filesDir, "history-$first.json").delete()
            File(context.filesDir, "history-$second.json").delete()
        }
    }
}
