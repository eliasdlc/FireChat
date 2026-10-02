package com.example.firechat.ui.users

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.asLiveData
import com.example.firechat.R
import com.example.firechat.data.model.User
import com.example.firechat.data.repository.UserRepository
import kotlinx.coroutines.flow.catch

class UsersViewModel : ViewModel() {

    private val _error = MutableLiveData<Int?>()
    val error: LiveData<Int?> = _error

    val users: LiveData<List<User>> = UserRepository()
        .observeUsers(excludeUid = UserRepository.LOCAL_USER_ID)
        .catch { _error.value = R.string.error_loading_users }
        .asLiveData()
}
