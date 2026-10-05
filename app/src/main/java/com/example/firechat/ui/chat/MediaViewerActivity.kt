package com.example.firechat.ui.chat

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.MediaController
import android.widget.TextView
import android.widget.VideoView
import androidx.activity.enableEdgeToEdge
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import com.example.firechat.R
import com.example.firechat.data.media.OutgoingMedia
import com.example.firechat.data.model.Message
import com.example.firechat.data.repository.ChatMediaRepository
import com.example.firechat.ui.theme.ThemedActivity
import com.example.firechat.util.applySystemBarsPadding
import com.google.android.material.appbar.MaterialToolbar
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Shows a chat photo at full size, or downloads a chat video to the cache and
 * plays it. Files come through Storage rules, never through a public link.
 */
class MediaViewerActivity : ThemedActivity() {

    private val repository = ChatMediaRepository()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_media_viewer)
        findViewById<View>(R.id.main).applySystemBarsPadding()
        findViewById<MaterialToolbar>(R.id.toolbar).setNavigationOnClickListener { finish() }

        val url = intent.getStringExtra(EXTRA_URL).orEmpty()
        val status = findViewById<TextView>(R.id.viewerStatus)
        if (intent.getStringExtra(EXTRA_TYPE) == Message.TYPE_VIDEO) playVideo(url, status)
        else showImage(url, status)
    }

    override fun onResume() {
        super.onResume()
        // The viewer is always dark, so the system bar icons stay light in both modes.
        WindowInsetsControllerCompat(window, window.decorView).apply {
            isAppearanceLightStatusBars = false
            isAppearanceLightNavigationBars = false
        }
    }

    private fun showImage(url: String, status: TextView) {
        val image = findViewById<ImageView>(R.id.viewerImage)
        status.setText(R.string.media_loading)
        lifecycleScope.launch {
            try {
                val bytes = repository.bytes(url, OutgoingMedia.MAX_IMAGE_BYTES.toLong())
                val side = maxOf(resources.displayMetrics.widthPixels, resources.displayMetrics.heightPixels)
                image.setImageBitmap(withContext(Dispatchers.Default) { ChatMediaView.decode(bytes, side) })
                image.isVisible = true
                status.isVisible = false
            } catch (error: CancellationException) { throw error }
            catch (_: Exception) { status.setText(R.string.error_loading_media) }
        }
    }

    private fun playVideo(url: String, status: TextView) {
        val video = findViewById<VideoView>(R.id.viewerVideo)
        status.setText(R.string.media_loading)
        lifecycleScope.launch {
            try {
                val file = File(File(cacheDir, "chat-media").apply { mkdirs() }, "${url.hashCode().toUInt()}-${url.substringAfterLast('/')}")
                if (!file.exists()) {
                    val partial = File(file.path + ".part")
                    repository.download(url, partial)
                    check(partial.renameTo(file))
                }
                status.isVisible = false
                video.isVisible = true
                video.setMediaController(MediaController(this@MediaViewerActivity).also { it.setAnchorView(video) })
                video.setVideoPath(file.path)
                video.setOnPreparedListener { video.start() }
                video.setOnErrorListener { _, _, _ ->
                    status.setText(R.string.error_loading_media); status.isVisible = true; true
                }
            } catch (error: CancellationException) { throw error }
            catch (_: Exception) { status.setText(R.string.error_loading_media) }
        }
    }

    companion object {
        private const val EXTRA_URL = "extra_media_url"
        private const val EXTRA_TYPE = "extra_media_type"

        fun newIntent(context: Context, message: Message): Intent =
            Intent(context, MediaViewerActivity::class.java)
                .putExtra(EXTRA_URL, message.mediaUrl)
                .putExtra(EXTRA_TYPE, message.type)
    }
}
