package com.example.firechat.ui.chat

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.text.format.DateUtils
import android.util.AttributeSet
import android.util.LruCache
import android.view.LayoutInflater
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.core.view.isVisible
import com.example.firechat.R
import com.example.firechat.data.media.OutgoingMedia
import com.example.firechat.data.model.Message
import com.example.firechat.data.repository.ChatMediaRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The picture inside a photo or video bubble. Keeps the media's aspect ratio
 * within the bubble limits and loads the gs:// image (the thumbnail for videos)
 * while attached; videos show a play badge and their duration.
 */
class ChatMediaView(context: Context, attrs: AttributeSet? = null) : FrameLayout(context, attrs) {
    private val image: ImageView
    private val play: ImageView
    private val duration: TextView
    private var scope: CoroutineScope? = null
    private var job: Job? = null
    private var url: String? = null
    private var aspect = 1f

    init {
        LayoutInflater.from(context).inflate(R.layout.view_chat_media, this, true)
        image = findViewById(R.id.mediaImage)
        play = findViewById(R.id.mediaPlay)
        duration = findViewById(R.id.mediaDuration)
    }

    fun bind(message: Message) {
        val video = message.type == Message.TYPE_VIDEO
        play.isVisible = video
        duration.isVisible = video && message.durationMs > 0
        duration.text = DateUtils.formatElapsedTime(message.durationMs / 1000)
        aspect = if (message.mediaWidth > 0 && message.mediaHeight > 0)
            message.mediaHeight.toFloat() / message.mediaWidth else 1f
        contentDescription = context.getString(if (video) R.string.message_video_open else R.string.message_image_open)
        val next = if (video) message.thumbUrl else message.mediaUrl
        if (next != url) {
            url = next
            job?.cancel()
            image.setImageBitmap(next?.let { cache.get(it) })
        }
        requestLayout()
        load()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val preferred = resources.getDimensionPixelSize(R.dimen.message_image_size)
        val width = if (MeasureSpec.getMode(widthMeasureSpec) == MeasureSpec.UNSPECIFIED) preferred
            else minOf(MeasureSpec.getSize(widthMeasureSpec), preferred)
        val height = (width * aspect).toInt().coerceIn(
            resources.getDimensionPixelSize(R.dimen.message_media_min_height),
            resources.getDimensionPixelSize(R.dimen.message_media_max_height)
        )
        super.onMeasure(
            MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY),
            MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY)
        )
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
        val target = url?.takeIf { it.isNotBlank() } ?: return
        if (cache.get(target) != null || job?.isActive == true) return
        val maxSide = resources.getDimensionPixelSize(R.dimen.message_image_size) * 2
        job = scope?.launch {
            try {
                val bytes = ChatMediaRepository().bytes(target, OutgoingMedia.MAX_IMAGE_BYTES.toLong())
                val bitmap = withContext(Dispatchers.Default) { decode(bytes, maxSide) } ?: return@launch
                cache.put(target, bitmap)
                if (url == target) image.setImageBitmap(bitmap)
            } catch (error: CancellationException) { throw error }
            catch (_: Exception) { /* The bubble keeps its neutral placeholder. */ }
        }
    }

    companion object {
        /** Decoded bubble images, keyed by gs:// reference and bounded by bytes. */
        private val cache = object : LruCache<String, Bitmap>(24 * 1024 * 1024) {
            override fun sizeOf(key: String, value: Bitmap) = value.byteCount
        }

        fun decode(bytes: ByteArray, maxSide: Int): Bitmap? {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
            val options = BitmapFactory.Options().apply { inSampleSize = 1 }
            while (maxOf(bounds.outWidth, bounds.outHeight) / (options.inSampleSize * 2) >= maxSide) options.inSampleSize *= 2
            return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
        }
    }
}
