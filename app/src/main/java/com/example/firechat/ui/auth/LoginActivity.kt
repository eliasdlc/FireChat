package com.example.firechat.ui.auth

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.example.firechat.ui.theme.ThemedActivity
import com.example.firechat.R
import com.example.firechat.ui.conversations.ConversationsActivity
import com.example.firechat.util.applySystemBarsPadding
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout

class LoginActivity : ThemedActivity() {

    private val viewModel: LoginViewModel by viewModels()

    private lateinit var emailLayout: TextInputLayout
    private lateinit var passwordLayout: TextInputLayout
    private lateinit var formError: TextView
    private lateinit var loginButton: MaterialButton

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_login)
        findViewById<View>(R.id.main).applySystemBarsPadding()

        emailLayout = findViewById(R.id.emailLayout)
        passwordLayout = findViewById(R.id.passwordLayout)
        formError = findViewById(R.id.formError)
        loginButton = findViewById(R.id.loginButton)
        val emailInput = findViewById<TextInputEditText>(R.id.emailInput)
        val passwordInput = findViewById<TextInputEditText>(R.id.passwordInput)
        val goToRegisterButton = findViewById<MaterialButton>(R.id.goToRegisterButton)
        findViewById<MaterialToolbar>(R.id.toolbar).setNavigationOnClickListener { finish() }

        val submit = {
            viewModel.login(
                email = emailInput.text.toString().trim(),
                password = passwordInput.text.toString()
            )
        }
        loginButton.setOnClickListener { submit() }
        passwordInput.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) submit()
            actionId == EditorInfo.IME_ACTION_DONE
        }
        // Cambia de formulario sin apilarlos: Atrás vuelve siempre a la bienvenida.
        goToRegisterButton.setOnClickListener {
            startActivity(Intent(this, RegisterActivity::class.java))
            finish()
        }

        viewModel.uiState.observe(this) { state -> render(state) }
    }

    private fun render(state: LoginUiState) {
        if (state.isLoggedIn) {
            openConversations()
            return
        }
        emailLayout.error = state.emailError?.let(::getString)
        passwordLayout.error = state.passwordError?.let(::getString)
        formError.text = state.formError?.let(::getString).orEmpty()

        loginButton.isEnabled = !state.isLoading
        loginButton.setText(if (state.isLoading) R.string.login_loading else R.string.login_button)
    }

    /** Abre las conversaciones en una tarea nueva para que la bienvenida y el login salgan de la pila. */
    private fun openConversations() {
        startActivity(
            Intent(this, ConversationsActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        )
    }
}
