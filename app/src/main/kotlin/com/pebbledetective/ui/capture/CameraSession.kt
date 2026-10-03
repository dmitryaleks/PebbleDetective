package com.pebbledetective.ui.capture

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.util.Size
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.core.UseCaseGroup
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Binds the rear camera for preview plus stills, and takes the shot.
 *
 * Rear lens only. The subject is a pebble on the ground, so the rear camera
 * is the one that can actually see it; there is no flip control.
 */
class CameraSession(
    private val context: Context,
    private val previewView: PreviewView,
) {
    private var imageCapture: ImageCapture? = null
    private var provider: ProcessCameraProvider? = null

    suspend fun bind(owner: LifecycleOwner) {
        val cameraProvider = awaitCameraProvider()
        provider = cameraProvider

        val preview = Preview.Builder().build().apply {
            surfaceProvider = previewView.surfaceProvider
        }

        // Cap the still. A 50MP frame decoded to ARGB_8888 is ~200MB and will
        // simply run the app out of memory; this is ample for a keepsake and
        // makes the colour analysis instant.
        val resolution = ResolutionSelector.Builder()
            .setAspectRatioStrategy(AspectRatioStrategy.RATIO_4_3_FALLBACK_AUTO_STRATEGY)
            .setResolutionStrategy(
                ResolutionStrategy(
                    Size(1440, 1080),
                    ResolutionStrategy.FALLBACK_RULE_CLOSEST_LOWER_THEN_HIGHER,
                )
            )
            .build()

        val capture = ImageCapture.Builder()
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
            .setResolutionSelector(resolution)
            .build()
        imageCapture = capture

        val group = UseCaseGroup.Builder()
            .addUseCase(preview)
            .addUseCase(capture)
            .apply {
                // A shared ViewPort makes the still's crop match what the child
                // saw, so a tap maps onto the same pixels in the saved photo.
                previewView.viewPort?.let { setViewPort(it) }
            }
            .build()

        cameraProvider.unbindAll()
        cameraProvider.bindToLifecycle(owner, CameraSelector.DEFAULT_BACK_CAMERA, group)
    }

    /**
     * Takes one photograph and returns it already upright.
     *
     * Captures to memory rather than to a file on purpose. The file variant
     * records orientation in EXIF instead of rotating pixels, and
     * BitmapFactory ignores EXIF - so the still shown on screen and the bitmap
     * analysed for colour would silently disagree by 90 degrees on most
     * phones. Rotating here means one bitmap is both shown and analysed.
     */
    suspend fun capture(): Bitmap = suspendCancellableCoroutine { cont ->
        val capture = imageCapture ?: run {
            cont.resumeWithException(IllegalStateException("camera not bound"))
            return@suspendCancellableCoroutine
        }
        capture.takePicture(
            ContextCompat.getMainExecutor(context),
            object : ImageCapture.OnImageCapturedCallback() {
                override fun onCaptureSuccess(image: ImageProxy) {
                    val degrees = image.imageInfo.rotationDegrees
                    val raw = image.toBitmap()
                    image.close()
                    cont.resume(raw.uprightBy(degrees))
                }

                override fun onError(exception: ImageCaptureException) {
                    cont.resumeWithException(exception)
                }
            },
        )
    }

    /**
     * ProcessCameraProvider only exposes a ListenableFuture here, and
     * awaitInstance does not exist in CameraX 1.6.2, so bridge it by hand
     * rather than pull in concurrent-futures just for this.
     */
    private suspend fun awaitCameraProvider(): ProcessCameraProvider =
        suspendCancellableCoroutine { cont ->
            val future = ProcessCameraProvider.getInstance(context)
            future.addListener(
                {
                    runCatching { future.get() }
                        .onSuccess { cont.resume(it) }
                        .onFailure { cont.resumeWithException(it) }
                },
                ContextCompat.getMainExecutor(context),
            )
        }

    fun unbind() {
        provider?.unbindAll()
        imageCapture = null
    }
}

/** Rotates pixels so the stored image needs no EXIF to be read correctly. */
private fun Bitmap.uprightBy(degrees: Int): Bitmap {
    if (degrees == 0) return this
    val matrix = Matrix().apply { postRotate(degrees.toFloat()) }
    val rotated = Bitmap.createBitmap(this, 0, 0, width, height, matrix, true)
    if (rotated !== this) recycle()
    return rotated
}
