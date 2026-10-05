package com.example.firechat.ui.users

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.asLiveData
import com.example.firechat.R
import com.example.firechat.data.model.User
import com.example.firechat.data.repository.AuthRepository
import com.example.firechat.data.repository.UserRepository
import com.example.firechat.data.repository.NicknameRepository
import kotlinx.coroutines.flow.catch

class UsersViewModel(application: Application) : AndroidViewModel(application) {
    val nicknames: LiveData<Map<String, String>> = NicknameRepository(application)
        .observe(AuthRepository().currentUserId.orEmpty()).asLiveData()

    private val _error = MutableLiveData<Int?>()
    val error: LiveData<Int?> = _error

    val users: LiveData<List<User>> = UserRepository()
        .observeUsers(excludeUid = AuthRepository().currentUserId.orEmpty())
        .catch { _error.value = R.string.error_loading_users }
        .asLiveData()
}
