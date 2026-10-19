package app.oralyvo.mobile.data

import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.tls.HandshakeCertificates
import okhttp3.tls.HeldCertificate
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.io.File
import java.util.UUID

class ApiClientTest {
    private lateinit var server: MockWebServer
    private lateinit var api: ApiClient
    private lateinit var store: MemorySession
    private class MemorySession(var value: AuthSession?) : SessionStore {
        override fun session() = value
        override fun save(session: AuthSession) { value = session }
    }
    @Before fun setUp() {
        val certificate = HeldCertificate.Builder().addSubjectAlternativeName("localhost").build()
        val serverCerts = HandshakeCertificates.Builder().heldCertificate(certificate).build()
        val clientCerts = HandshakeCertificates.Builder().addTrustedCertificate(certificate.certificate).build()
        server = MockWebServer()
        server.useHttps(serverCerts.sslSocketFactory(), false)
        server.start()
        store = MemorySession(AuthSession("old-access", "old-refresh", UUID.randomUUID().toString(), "test@example.invalid", Long.MAX_VALUE))
        api = ApiClient(store, server.url("").toString().trimEnd('/'), OkHttpClient.Builder()
            .sslSocketFactory(clientCerts.sslSocketFactory(), clientCerts.trustManager).build())
    }
    @After fun tearDown() { server.shutdown() }
    private fun respond(body: String, status: Int = 200) { server.enqueue(MockResponse().setResponseCode(status).setHeader("Content-Type", "application/json").setBody(body)) }

    @Test fun unauthorizedSaveRenewsAndRetriesTheSameIdempotentEntry() = runBlocking {
        respond("{\"error\":\"expired\"}", 401)
        respond(JSONObject().put("supabaseUrl", server.url("")).put("supabasePublishableKey", "public-test-key").toString())
        respond("{\"access_token\":\"new-access\",\"refresh_token\":\"new-refresh\",\"expires_in\":3600}")
        respond("{\"entry\":{}}", 201)
        val entry = TranscriptEntry(UUID.randomUUID().toString(), 1L, "Important notes", type = "summary")
        assertTrue(api.save(entry) is ApiResult.Success)
        val initial = server.takeRequest()
        assertEquals("/api/history", initial.path)
        assertEquals("Bearer old-access", initial.getHeader("Authorization"))
        assertEquals("/api/health", server.takeRequest().path)
        val renew = server.takeRequest()
        assertEquals("/auth/v1/token?grant_type=refresh_token", renew.path)
        assertEquals("old-refresh", JSONObject(renew.body.readUtf8()).getString("refresh_token"))
        val retry = server.takeRequest()
        assertEquals("Bearer new-access", retry.getHeader("Authorization"))
        assertEquals(initial.body.readUtf8(), retry.body.readUtf8())
        assertEquals("new-refresh", store.value?.refreshToken)
    }
    @Test fun transcriptionUploadsAudioWithRecognizedFilenameAndRejectsEmptySpeech() = runBlocking {
        val file = File.createTempFile("test-recording", ".m4a").apply { writeBytes(byteArrayOf(1, 2, 3)) }
        try {
            respond("{\"text\":\"\"}")
            val result = api.transcribe(file)
            assertTrue(result is ApiResult.Failure)
            assertTrue(file.exists())
            val request = server.takeRequest()
            assertEquals("/api/transcribe", request.path)
            val multipart = request.body.readUtf8()
            assertTrue(multipart.contains("filename=\"${file.name}\""))
            assertTrue(multipart.contains("audio/mp4"))
            assertTrue(multipart.contains("accurate"))
        } finally { file.delete() }
    }
    @Test fun translationUsesBackendContractAndReportsProviderFailure() = runBlocking {
        respond("{\"summary\":\"Bonjour\"}")
        assertEquals("Bonjour", (api.process("Hello", "translate", "French") as ApiResult.Success<String>).data)
        val body = JSONObject(server.takeRequest().body.readUtf8())
        assertEquals("translate", body.getString("mode"))
        assertEquals("French", body.getString("targetLanguage"))
        assertEquals("engine-1", body.getString("provider"))
        respond("{\"error\":\"AI service is busy\"}", 429)
        assertEquals("AI service is busy", (api.process("Hello", "summarize", "French") as ApiResult.Failure).message)
    }
    @Test fun historyPreservesLegacyIdentityForOfflineDeduplication() = runBlocking {
        val id = UUID.randomUUID().toString()
        respond("{\"entries\":[{\"id\":\"cloud-id\",\"legacy_id\":\"$id\",\"type\":\"translation\",\"content\":\"Bonjour\",\"created_at\":\"2026-10-19T12:00:00Z\"}]}")
        val entry = (api.history() as ApiResult.Success<List<TranscriptEntry>>).data.single()
        assertEquals(id, entry.id)
        assertTrue(entry.cloudSaved)
        assertEquals("translation", entry.type)
        assertEquals("Bearer old-access", server.takeRequest().getHeader("Authorization"))
    }
}
