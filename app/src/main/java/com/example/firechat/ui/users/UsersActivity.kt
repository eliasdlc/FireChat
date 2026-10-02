package com.example.firechat.ui.users

import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.firechat.R
import com.example.firechat.ui.chat.ChatActivity
import com.example.firechat.util.applySystemBarsPadding
import com.google.android.material.appbar.MaterialToolbar

class UsersActivity : AppCompatActivity() {

    private val viewModel: UsersViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_users)
        findViewById<View>(R.id.main).applySystemBarsPadding()

        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
        val userList = findViewById<RecyclerView>(R.id.userList)
        val emptyState = findViewById<TextView>(R.id.emptyState)
        toolbar.setNavigationOnClickListener { finish() }

        val adapter = UserAdapter { user ->
            startActivity(ChatActivity.newIntent(this, user.uid, user.name))
            finish()
        }
        userList.layoutManager = LinearLayoutManager(this)
        userList.adapter = adapter

        viewModel.users.observe(this) { list ->
            adapter.submitList(list)
            emptyState.isVisible = list.isEmpty()
        }
        viewModel.error.observe(this) { error ->
            if (error != null) {
                emptyState.setText(error)
                emptyState.isVisible = true
            }
        }
    }
}
