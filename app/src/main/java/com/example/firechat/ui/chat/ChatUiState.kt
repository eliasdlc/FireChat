package com.example.firechat.ui.chat

import androidx.annotation.StringRes

/** One-shot errors and the progress of a photo or video being sent (0 to 100, null when idle). */
data class ChatUiState(
    @StringRes val error: Int? = null,
    val uploadProgress: Int? = null
)
