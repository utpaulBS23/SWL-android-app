package com.phq.swl.pioms.domain.face_detection

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Rect
import android.net.Uri
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import com.phq.swl.pioms.domain.AppException
import com.phq.swl.pioms.domain.ErrorCode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class MLKitFaceDetector(
    private val context: Context
) : BaseFaceDetector() {

    private val realTimeOpts = FaceDetectorOptions.Builder()
        .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
        .build()
    private val realTimeFaceDetector = FaceDetection.getClient(realTimeOpts)

    private val highAccuracyOpts = FaceDetectorOptions.Builder()
        .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE)
        .build()
    private val highAccuracyFaceDetector = FaceDetection.getClient(highAccuracyOpts)

    override suspend fun getCroppedFace(imageUri: Uri): Result<Bitmap> =
        withContext(Dispatchers.IO) {
            val imageBitmap =
                getBitmapFromUri(context, imageUri) ?: return@withContext Result.failure<Bitmap>(
                    AppException(ErrorCode.FACE_DETECTOR_FAILURE),
                )

            // We need exactly one face in the image, in other cases, return the
            // necessary errors
            val faces = Tasks.await(highAccuracyFaceDetector.process(InputImage.fromBitmap(imageBitmap, 0)))
            if (faces.size > 1) {
                return@withContext Result.failure<Bitmap>(AppException(ErrorCode.MULTIPLE_FACES))
            } else if (faces.isEmpty()) {
                return@withContext Result.failure<Bitmap>(AppException(ErrorCode.NO_FACE))
            } else {
                // Validate the bounding box and
                // return the cropped face
                val rect = faces[0].boundingBox
                if (validateRect(imageBitmap, rect)) {
                    val croppedBitmap =
                        Bitmap.createBitmap(
                            imageBitmap,
                            rect.left,
                            rect.top,
                            rect.width(),
                            rect.height(),
                        )
                    return@withContext Result.success(croppedBitmap)
                } else {
                    return@withContext Result.failure<Bitmap>(
                        AppException(ErrorCode.FACE_DETECTOR_FAILURE),
                    )
                }
            }
        }

    override suspend fun getAllCroppedFaces(frameBitmap: Bitmap): List<Pair<Bitmap, Rect>> =
        withContext(
            Dispatchers.IO
        ) {
            return@withContext Tasks.await(realTimeFaceDetector.process(
                InputImage.fromBitmap(
                    frameBitmap,
                    0
                )
            )).filter { validateRect(frameBitmap, it.boundingBox) }
                .map { detection -> detection.boundingBox }
                .map { rect ->
                    val croppedBitmap =
                        Bitmap.createBitmap(
                            frameBitmap,
                            rect.left,
                            rect.top,
                            rect.width(),
                            rect.height(),
                        )
                    Pair(croppedBitmap, rect)
                }
        }
}