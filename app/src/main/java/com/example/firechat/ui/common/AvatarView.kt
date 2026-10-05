package com.example.firechat.ui.common

import android.content.Context
import android.graphics.BitmapFactory
import android.util.AttributeSet
import android.view.Gravity
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.example.firechat.R
import com.example.firechat.data.repository.ProfilePhotoRepository
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

class AvatarView(context: Context, attrs: AttributeSet? = null) : FrameLayout(context, attrs) {
    private val initial = TextView(context).apply {
        gravity = Gravity.CENTER
        setTextColor(ContextCompat.getColor(context, R.color.ink))
        textSize = 20f
    }
    private val image = ImageView(context).apply { scaleType = ImageView.ScaleType.CENTER_CROP }
    private var scope: CoroutineScope? = null
    private var job: Job? = null
    private var photoUrl: String? = null
    private var displayedUrl: String? = null

    init {
        setBackgroundResource(R.drawable.bg_avatar)
        clipToOutline = true
        addView(initial, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        addView(image, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
    }

    fun bind(name: String, url: String?) {
        initial.text = name.trim().take(1).uppercase().ifBlank { "?" }
        initial.textSize = if ((layoutParams?.width ?: 0) > 80 * resources.displayMetrics.density) 48f else 20f
        if (photoUrl != url) {
            photoUrl = url
            displayedUrl = null
            image.setImageDrawable(null)
            job?.cancel()
        }
        load()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        load()
    }

    override fun onDetachedFromWindow() {
        scope?.cancel()
        scope = null
        job = null
        super.onDetachedFromWindow()
    }

    private fun load() {
        val url = photoUrl?.takeIf { it.isNotBlank() } ?: return
        if (displayedUrl == url || job?.isActive == true) return
        job = scope?.launch {
            try {
                val bytes = FirebaseStorage.getInstance().getReferenceFromUrl(url)
                    .getBytes(ProfilePhotoRepository.MAX_BYTES.toLong()).await()
                val bitmap = withContext(Dispatchers.Default) {
                    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
                    val options = BitmapFactory.Options().apply { inSampleSize = 1 }
                    while (maxOf(bounds.outWidth, bounds.outHeight) / options.inSampleSize > 512) {
                        options.inSampleSize *= 2
                    }
                    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
                }
                if (url == photoUrl && bitmap != null) {
                    image.setImageBitmap(bitmap)
                    displayedUrl = url
                }
            } catch (error: CancellationException) { throw error }
            catch (_: Exception) { image.setImageDrawable(null) }
        }
    }
}
