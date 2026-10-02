package com.example.firechat.ui.chat

import android.content.Context
import android.content.Intent
import androidx.appcompat.app.AppCompatActivity

class ChatActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_USER_ID = "extra_user_id"
        const val EXTRA_USER_NAME = "extra_user_name"

        fun newIntent(context: Context, userId: String, userName: String): Intent =
            Intent(context, ChatActivity::class.java)
                .putExtra(EXTRA_USER_ID, userId)
                .putExtra(EXTRA_USER_NAME, userName)
    }
}
