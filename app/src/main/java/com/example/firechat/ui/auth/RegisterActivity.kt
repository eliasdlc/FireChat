package com.example.firechat.ui.auth

import android.content.Intent
import android.os.Bundle
import android.view.ViewGroup
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import com.example.firechat.R
import com.example.firechat.ui.conversations.ConversationsActivity
import com.example.firechat.util.applySystemBarsPadding
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout

/** Formulario de creación de cuenta. */
class RegisterActivity : AppCompatActivity() {

    private lateinit var nameLayout: TextInputLayout
    private lateinit var emailLayout: TextInputLayout
    private lateinit var passwordLayout: TextInputLayout
    private lateinit var confirmLayout: TextInputLayout
    private lateinit var nameInput: TextInputEditText
    private lateinit var emailInput: TextInputEditText
    private lateinit var passwordInput: TextInputEditText
    private lateinit var confirmInput: TextInputEditText
    private lateinit var formError: TextView
    private lateinit var registerButton: MaterialButton
    private lateinit var goToLoginButton: MaterialButton

    private val viewModel: RegisterViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_register)
        findViewById<ViewGroup>(android.R.id.content).getChildAt(0).applySystemBarsPadding()

        nameLayout = findViewById(R.id.nameLayout)
        emailLayout = findViewById(R.id.emailLayout)
        passwordLayout = findViewById(R.id.passwordLayout)
        confirmLayout = findViewById(R.id.confirmLayout)
        nameInput = findViewById(R.id.nameInput)
        emailInput = findViewById(R.id.emailInput)
        passwordInput = findViewById(R.id.passwordInput)
        confirmInput = findViewById(R.id.confirmInput)
        formError = findViewById(R.id.formError)
        registerButton = findViewById(R.id.registerButton)
        goToLoginButton = findViewById(R.id.goToLoginButton)

        registerButton.setOnClickListener {
            viewModel.register(
                name = nameInput.text.toString(),
                email = emailInput.text.toString().trim(),
                password = passwordInput.text.toString(),
                confirmation = confirmInput.text.toString()
            )
        }
        goToLoginButton.setOnClickListener { finish() }

        viewModel.uiState.observe(this) { state -> render(state) }
    }

    private fun render(state: RegisterUiState) {
        if (state.isRegistered) {
            openConversations()
            return
        }
        nameLayout.error = state.nameError?.let(::getString)
        emailLayout.error = state.emailError?.let(::getString)
        passwordLayout.error = state.passwordError?.let(::getString)
        confirmLayout.error = state.confirmationError?.let(::getString)
        formError.text = state.formError?.let(::getString).orEmpty()

        registerButton.isEnabled = !state.isLoading
        registerButton.setText(
            if (state.isLoading) R.string.register_loading else R.string.register_button
        )
    }

    /**
     * Abre las conversaciones en una tarea nueva: así el login y el registro
     * salen de la pila y Atrás cierra la app en vez de volver al formulario.
     */
    private fun openConversations() {
        val intent = Intent(this, ConversationsActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        startActivity(intent)
    }
}
