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
            // PENDO: Using UNSIGNED uploads for security.
            // Create a preset in your Cloudinary Dashboard with detected tags enabled.
            MediaManager.get().upload(imageUri)
                .option("upload_preset", "modi_unsigned_preset") 
                .option("folder", "modi/$folder")
                .option("resource_type", "image")
                .callback(object : UploadCallback {
                    override fun onStart(requestId: String) {}
                    override fun onProgress(requestId: String, bytes: Long, totalBytes: Long) {}
                    
                    override fun onSuccess(requestId: String, resultData: Map<*, *>) {
                        continuation.resume(resultData)
                    }

                    override fun onError(requestId: String, error: ErrorInfo) {
                        continuation.resumeWithException(Exception("Cloudinary Error: ${error.description}"))
                    }

                    override fun onReschedule(requestId: String, error: ErrorInfo) {}
                }).dispatch()
        }
    }
}
