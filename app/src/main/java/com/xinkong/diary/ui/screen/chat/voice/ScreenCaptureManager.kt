package com.xinkong.diary.ui.screen.chat.voice

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.Image
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import java.util.concurrent.atomic.AtomicBoolean

class ScreenCaptureManager(private val context: Context) {

    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null
    private val mediaProjectionManager = context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager

    fun initProjection(resultCode: Int, resultData: Intent) {
        if (mediaProjection != null) return
        mediaProjection = mediaProjectionManager.getMediaProjection(resultCode, resultData)
    }

    fun captureSingleFrame(width: Int, height: Int, densityDpi: Int, onCaptured: (Bitmap?) -> Unit) {
        if (mediaProjection == null) {
            onCaptured(null)
            return
        }

        val delivered = AtomicBoolean(false)
        val mainHandler = android.os.Handler(android.os.Looper.getMainLooper())
        val timeoutRunnable = Runnable {
            if (delivered.compareAndSet(false, true)) {
                releaseCaptureSession()
                onCaptured(null)
            }
        }

        // Ensure previous one-shot session is fully released before creating a new one.
        releaseCaptureSession()

        imageReader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2)
        virtualDisplay = mediaProjection?.createVirtualDisplay(
            "ScreenCapture",
            width,
            height,
            densityDpi,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            imageReader?.surface,
            null,
            null
        )

        imageReader?.setOnImageAvailableListener(object : ImageReader.OnImageAvailableListener {
            override fun onImageAvailable(reader: ImageReader?) {
                if (delivered.get()) return
                val image: Image = reader?.acquireLatestImage() ?: return
                
                try {
                    val planes = image.planes
                    val buffer = planes[0].buffer
                    val pixelStride = planes[0].pixelStride
                    val rowStride = planes[0].rowStride
                    val rowPadding = rowStride - pixelStride * width
                    
                    val bitmap = Bitmap.createBitmap(width + rowPadding / pixelStride, height, Bitmap.Config.ARGB_8888)
                    bitmap.copyPixelsFromBuffer(buffer)
                    
                    if (!delivered.compareAndSet(false, true)) {
                        return
                    }
                    
                    if (rowPadding == 0) {
                        onCaptured(bitmap)
                    } else {
                        val croppedBitmap = Bitmap.createBitmap(bitmap, 0, 0, width, height)
                        bitmap.recycle()
                        onCaptured(croppedBitmap)
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    if (delivered.compareAndSet(false, true)) {
                        onCaptured(null)
                    }
                } finally {
                    image.close()
                    mainHandler.removeCallbacks(timeoutRunnable)
                    releaseCaptureSession()
                }
            }
        }, mainHandler)

        // 部分机型首次帧回调不稳定，增加超时兜底避免无回调导致无响应
        mainHandler.postDelayed(timeoutRunnable, 1200)
    }

    fun stopProjection() {
        releaseCaptureSession()
        mediaProjection?.stop()
        mediaProjection = null
    }

    private fun releaseCaptureSession() {
        try {
            imageReader?.setOnImageAvailableListener(null, null)
        } catch (_: Exception) {
        }
        try {
            // Detach surface first to reduce BufferQueue abandoned noise on some ROMs.
            virtualDisplay?.surface = null
        } catch (_: Exception) {
        }
        try {
            virtualDisplay?.release()
        } catch (_: Exception) {
        }
        virtualDisplay = null
        try {
            imageReader?.close()
        } catch (_: Exception) {
        }
        imageReader = null
    }
}
