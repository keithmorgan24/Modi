package com.keith.modi

import android.content.Context
import android.net.Uri
import com.cloudinary.android.MediaManager
import com.cloudinary.android.callback.ErrorInfo
import com.cloudinary.android.callback.UploadCallback
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

object CloudinaryHelper {

    /**
     * Uploads an image to Cloudinary and returns the secure URL.
     * Following 'pendo' principles: Uses signed uploads or specific folders for security.
     */
    suspend fun uploadImage(context: Context, imageUri: Uri, folder: String = "properties"): Map<*, *> {
        return suspendCancellableCoroutine { continuation ->
            try {
                // PENDO: Safety check to prevent IllegalStateException if init failed
                val manager = try { MediaManager.get() } catch (e: Exception) { null }
                if (manager == null) {
                    continuation.resumeWithException(Exception("Cloudinary not initialized. Check your API keys."))
                    return@suspendCancellableCoroutine
                }

                manager.upload(imageUri)
                    .unsigned("modi_unsigned_preset")
                    .option("folder", "modi/$folder")
                    .option("resource_type", "image")
                    .callback(object : UploadCallback {
                        override fun onStart(requestId: String) {}
                        override fun onProgress(requestId: String, bytes: Long, totalBytes: Long) {}
                        
                        override fun onSuccess(requestId: String, resultData: Map<*, *>) {
                            if (!continuation.isCompleted) {
                                continuation.resume(resultData)
                            }
                        }

                        override fun onError(requestId: String, error: ErrorInfo) {
                            if (!continuation.isCompleted) {
                                continuation.resumeWithException(Exception("Cloudinary Error: ${error.description}"))
                            }
                        }

                        override fun onReschedule(requestId: String, error: ErrorInfo) {
                            // PENDO: Handle recoverable network errors by failing fast for the UI
                            if (!continuation.isCompleted) {
                                continuation.resumeWithException(Exception("Upload rescheduled: ${error.description}"))
                            }
                        }
                    }).dispatch()
            } catch (e: Exception) {
                if (!continuation.isCompleted) {
                    continuation.resumeWithException(e)
                }
            }
        }
    }
}
