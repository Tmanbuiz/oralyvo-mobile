package app.oralyvo.mobile.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import app.oralyvo.mobile.data.ApiClient
import app.oralyvo.mobile.data.ApiResult
import app.oralyvo.mobile.data.TokenStore
import kotlinx.coroutines.launch

/**
 * Sign-in / sign-up screen. A Turnstile token is required before either
 * call is attempted -- the Worker rejects auth requests without one -- so
 * the widget is embedded right on this screen rather than gated behind a
 * separate step, and the submit button stays disabled until a token has
 * come back from it.
 */
@Composable
fun AuthScreen(onAuthenticated: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val apiClient = remember { ApiClient() }
    val tokenStore = remember { TokenStore(context) }

    var isSignUp by remember { mutableStateOf(false) }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var turnstileToken by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Oralyvo",
            style = MaterialTheme.typography.headlineMedium
        )
        Text(
            text = if (isSignUp) "Create an account" else "Sign in to continue",
            style = MaterialTheme.typography.bodyMedium
        )

        OutlinedTextField(
            value = email,
            onValueChange = { email = it },
            label = { Text("Email") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp)
        )

        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("Password") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp)
        )

        // The widget renders a compact interactive challenge (usually just a
        // checkbox, occasionally a short puzzle) -- a fixed height box is
        // enough room for either without the rest of the screen jumping
        // around once it resolves.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(80.dp)
                .padding(top = 16.dp)
        ) {
            TurnstileWebView(
                modifier = Modifier.fillMaxSize(),
                onToken = { token ->
                    turnstileToken = token
                    errorMessage = null
                },
                onError = { code ->
                    turnstileToken = null
                    errorMessage = "Verification failed ($code). Please retry."
                },
                onExpired = {
                    turnstileToken = null
                }
            )
        }

        errorMessage?.let { message ->
            Text(
                text = message,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(top = 8.dp)
            )
        }

        Button(
            enabled = !isLoading && email.isNotBlank() && password.isNotBlank() && turnstileToken != null,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp),
            onClick = {
                val token = turnstileToken ?: return@Button
                isLoading = true
                errorMessage = null
                scope.launch {
                    val result = if (isSignUp) {
                        apiClient.signUp(email, password, token)
                    } else {
                        apiClient.signIn(email, password, token)
                    }
                    isLoading = false
                    when (result) {
                        is ApiResult.Success -> {
                            tokenStore.save(result.data)
                            onAuthenticated()
                        }
                        is ApiResult.Failure -> {
                            errorMessage = result.message
                        }
                    }
                }
            }
        ) {
            if (isLoading) {
                CircularProgressIndicator(modifier = Modifier.height(20.dp))
            } else {
                Text(if (isSignUp) "Sign up" else "Sign in")
            }
        }

        TextButton(onClick = { isSignUp = !isSignUp }) {
            Text(
                if (isSignUp) "Already have an account? Sign in"
                else "Need an account? Sign up"
            )
        }
    }
}
