package com.example.firechat.ui.auth

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.activity.enableEdgeToEdge
import com.example.firechat.R
import com.example.firechat.data.repository.AuthRepository
import com.example.firechat.ui.conversations.ConversationsActivity
import com.example.firechat.ui.theme.ThemedActivity
import com.example.firechat.util.applySystemBarsPadding
import com.google.android.material.button.MaterialButton

/**
 * Pantalla de entrada de la app. Con sesión abierta pasa directo a las
 * conversaciones; sin sesión ofrece iniciar sesión o crear una cuenta.
 */
class WelcomeActivity : ThemedActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (AuthRepository().isLoggedIn) {
            startActivity(Intent(this, ConversationsActivity::class.java))
            finish()
            return
        }
        enableEdgeToEdge()
        setContentView(R.layout.activity_welcome)
        findViewById<View>(R.id.main).applySystemBarsPadding()

        findViewById<MaterialButton>(R.id.welcomeLoginButton).setOnClickListener {
            startActivity(Intent(this, LoginActivity::class.java))
        }
        findViewById<MaterialButton>(R.id.welcomeRegisterButton).setOnClickListener {
            startActivity(Intent(this, RegisterActivity::class.java))
        }
    }
}
