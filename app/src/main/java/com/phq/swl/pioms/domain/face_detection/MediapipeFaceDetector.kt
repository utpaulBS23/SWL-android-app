package com.phq.swl.pioms.domain.face_detection

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Rect
import android.net.Uri
import androidx.core.graphics.toRect
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.facedetector.FaceDetector
import com.phq.swl.pioms.domain.AppException
import com.phq.swl.pioms.domain.ErrorCode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

// Utility class for interacting with Mediapipe's Face Detector
// See https://ai.google.dev/edge/mediapipe/solutions/vision/face_detector/android
class MediapipeFaceDetector(
    private val context: Context,
) : BaseFaceDetector() {
    // The model is stored in the assets folder
    private val modelName = "blaze_face_short_range.tflite"
    private val baseOptions = BaseOptions.builder().setModelAssetPath(modelName).build()
    private val faceDetectorOptions =
        FaceDetector.FaceDetectorOptions
            .builder()
            .setBaseOptions(baseOptions)
            .setRunningMode(RunningMode.IMAGE)
            .build()
    private val faceDetector = FaceDetector.createFromOptions(context, faceDetectorOptions)

    override suspend fun getCroppedFace(imageUri: Uri): Result<Bitmap> =
        withContext(Dispatchers.IO) {
            val imageBitmap =
                getBitmapFromUri(context, imageUri) ?: return@withContext Result.failure<Bitmap>(
                    AppException(ErrorCode.FACE_DETECTOR_FAILURE),
                )

            // We need exactly one face in the image, in other cases, return the
            // necessary errors
            val faces = faceDetector.detect(BitmapImageBuilder(imageBitmap).build()).detections()
            if (faces.size > 1) {
                return@withContext Result.failure<Bitmap>(AppException(ErrorCode.MULTIPLE_FACES))
            } else if (faces.size == 0) {
                return@withContext Result.failure<Bitmap>(AppException(ErrorCode.NO_FACE))
            } else {
                // Validate the bounding box and
                // return the cropped face
                val rect = faces[0].boundingBox().toRect()
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

    // Detects multiple faces from the `frameBitmap`
    // and returns pairs of (croppedFace , boundingBoxRect)
    // Used by ImageVectorUseCase.kt
    override suspend fun getAllCroppedFaces(frameBitmap: Bitmap): List<Pair<Bitmap, Rect>> =
        withContext(Dispatchers.IO) {
            return@withContext faceDetector
                .detect(BitmapImageBuilder(frameBitmap).build())
                .detections()
                .filter { validateRect(frameBitmap, it.boundingBox().toRect()) }
                .map { detection -> detection.boundingBox().toRect() }
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
