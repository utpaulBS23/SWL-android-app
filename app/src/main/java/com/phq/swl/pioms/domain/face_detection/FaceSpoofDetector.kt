package com.phq.swl.pioms.domain.face_detection

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Rect
import android.util.Log
import androidx.core.graphics.get
import androidx.core.graphics.set
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.koin.core.annotation.Single
import org.tensorflow.lite.DataType
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.gpu.CompatibilityList
import org.tensorflow.lite.gpu.GpuDelegate
import org.tensorflow.lite.support.common.FileUtil
import org.tensorflow.lite.support.common.ops.CastOp
import org.tensorflow.lite.support.image.ImageProcessor
import org.tensorflow.lite.support.image.TensorImage
import java.io.FileInputStream
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import kotlin.math.exp
import kotlin.time.DurationUnit
import kotlin.time.measureTime

/*

Utility class for interacting with FaceSpoofDetector

- It uses the MiniFASNet model from https://github.com/minivision-ai/Silent-Face-Anti-Spoofing
- The preprocessing methods are derived from
https://github.com/serengil/deepface/blob/master/deepface/models/spoofing/FasNet.py
- The model weights are in the PyTorch format. To convert them to the TFLite format,
  check the notebook linked in the README of the project
- An instance of this class is injected in ImageVectorUseCase.kt

*/
@Single
class FaceSpoofDetector(
    context: Context,
    useGpu: Boolean = false,
    useXNNPack: Boolean = false,
    useNNAPI: Boolean = false,
) {
    companion object {
        private const val TAG = "FaceSpoofDetector"
    }

    data class FaceSpoofResult(
        val isSpoof: Boolean,
        val score: Float,
        val timeMillis: Long,
    )

    private val scale1 = 2.7f
    private val scale2 = 4.0f
    private val inputImageDim = 80
    private val outputDim = 3

    private var firstModelInterpreter: Interpreter? = null
    private var secondModelInterpreter: Interpreter? = null
    private val imageTensorProcessor =
        ImageProcessor
            .Builder()
            .add(CastOp(DataType.FLOAT32))
            .build()

    init {
        // Initialize TFLiteInterpreter
        val interpreterOptions =
            Interpreter.Options().apply {
                // Add the GPU Delegate if supported.
                // See -> https://www.tensorflow.org/lite/performance/gpu#android
                if (useGpu) {
                    if (CompatibilityList().isDelegateSupportedOnThisDevice) {
                        addDelegate(GpuDelegate(CompatibilityList().bestOptionsForThisDevice))
                    }
                } else {
                    // Number of threads for computation
                    numThreads = 4
                }
                useXNNPACK = useXNNPack
                this.useNNAPI = useNNAPI
            }
        val firstModelBuffer = loadModelBuffer(context, "spoof_model_scale_2_7.tflite")
        val secondModelBuffer = loadModelBuffer(context, "spoof_model_scale_4_0.tflite")

        if (firstModelBuffer != null && secondModelBuffer != null) {
            firstModelInterpreter = Interpreter(firstModelBuffer, interpreterOptions)
            secondModelInterpreter = Interpreter(secondModelBuffer, interpreterOptions)
        } else {
            Log.e(
                TAG,
                "Spoof models are missing. Expected in assets or files dir; spoof detection will be skipped.",
            )
        }
    }

    suspend fun detectSpoof(
        frameImage: Bitmap,
        faceRect: Rect,
    ): FaceSpoofResult =
        withContext(Dispatchers.Default) {
            val firstInterpreter = firstModelInterpreter
            val secondInterpreter = secondModelInterpreter
            if (firstInterpreter == null || secondInterpreter == null) {
                return@withContext FaceSpoofResult(
                    isSpoof = false,
                    score = 0f,
                    timeMillis = 0L,
                )
            }

            // Crop the images and scale the bounding boxes
            // with the given two constants
            // and perform RGB -> BGR conversion
            val croppedImage1 =
                crop(
                    origImage = frameImage,
                    bbox = faceRect,
                    bboxScale = scale1,
                    targetWidth = inputImageDim,
                    targetHeight = inputImageDim,
                )
            for (i in 0 until croppedImage1.width) {
                for (j in 0 until croppedImage1.height) {
                    croppedImage1[i, j] =
                        Color.rgb(
                            Color.blue(croppedImage1[i, j]),
                            Color.green(croppedImage1[i, j]),
                            Color.red(croppedImage1[i, j]),
                        )
                }
            }
            val croppedImage2 =
                crop(
                    origImage = frameImage,
                    bbox = faceRect,
                    bboxScale = scale2,
                    targetWidth = inputImageDim,
                    targetHeight = inputImageDim,
                )
            for (i in 0 until croppedImage2.width) {
                for (j in 0 until croppedImage2.height) {
                    croppedImage2[i, j] =
                        Color.rgb(
                            Color.blue(croppedImage2[i, j]),
                            Color.green(croppedImage2[i, j]),
                            Color.red(croppedImage2[i, j]),
                        )
                }
            }
            val input1 = imageTensorProcessor.process(TensorImage.fromBitmap(croppedImage1)).buffer
            val input2 = imageTensorProcessor.process(TensorImage.fromBitmap(croppedImage2)).buffer
            val output1 = arrayOf(FloatArray(outputDim))
            val output2 = arrayOf(FloatArray(outputDim))

            val time =
                measureTime {
                    firstInterpreter.run(input1, output1)
                    secondInterpreter.run(input2, output2)
                }.toLong(DurationUnit.MILLISECONDS)

            val output =
                softMax(output1[0]).zip(softMax(output2[0])).map {
                    (it.first + it.second)
                }
            val label = output.indexOf(output.max())
            val iSpoof = label != 1
            val score = output[label] / 2f

            return@withContext FaceSpoofResult(isSpoof = iSpoof, score = score, timeMillis = time)
        }

    private fun loadModelBuffer(
        context: Context,
        modelFileName: String,
    ): MappedByteBuffer? {
        runCatching {
            return FileUtil.loadMappedFile(context, modelFileName)
        }.onFailure {
            Log.w(TAG, "Model not found in assets: $modelFileName (${it.message})")
        }

        val fallbackFiles =
            listOf(
                context.filesDir.resolve(modelFileName),
                context.noBackupFilesDir.resolve(modelFileName),
                context.cacheDir.resolve(modelFileName),
            )
        for (file in fallbackFiles) {
            if (!file.exists()) continue
            runCatching {
                FileInputStream(file).channel.use { channel ->
                    return channel.map(FileChannel.MapMode.READ_ONLY, 0, channel.size())
                }
            }.onFailure {
                Log.w(TAG, "Failed to map model from ${file.absolutePath}: ${it.message}")
            }
        }
        return null
    }

    private fun softMax(x: FloatArray): FloatArray {
        val exp = x.map { exp(it) }
        val expSum = exp.sum()
        return exp.map { it / expSum }.toFloatArray()
    }

    private fun crop(
        origImage: Bitmap,
        bbox: Rect,
        bboxScale: Float,
        targetWidth: Int,
        targetHeight: Int,
    ): Bitmap {
        val srcWidth = origImage.width
        val srcHeight = origImage.height
        val scaledBox = getScaledBox(srcWidth, srcHeight, bbox, bboxScale)
        val croppedBitmap =
            Bitmap.createBitmap(
                origImage,
                scaledBox.left,
                scaledBox.top,
                scaledBox.width(),
                scaledBox.height(),
            )
        return Bitmap.createScaledBitmap(croppedBitmap, targetWidth, targetHeight, true)
    }

    private fun getScaledBox(
        srcWidth: Int,
        srcHeight: Int,
        box: Rect,
        bboxScale: Float,
    ): Rect {
        val x = box.left
        val y = box.top
        val w = box.width()
        val h = box.height()
        val scale = floatArrayOf((srcHeight - 1f) / h, (srcWidth - 1f) / w, bboxScale).min()
        val newWidth = w * scale
        val newHeight = h * scale
        val centerX = w / 2 + x
        val centerY = h / 2 + y
        var topLeftX = centerX - newWidth / 2
        var topLeftY = centerY - newHeight / 2
        var bottomRightX = centerX + newWidth / 2
        var bottomRightY = centerY + newHeight / 2
        if (topLeftX < 0) {
            bottomRightX -= topLeftX
            topLeftX = 0f
        }
        if (topLeftY < 0) {
            bottomRightY -= topLeftY
            topLeftY = 0f
        }
        if (bottomRightX > srcWidth - 1) {
            topLeftX -= (bottomRightX - (srcWidth - 1))
            bottomRightX = (srcWidth - 1).toFloat()
        }
        if (bottomRightY > srcHeight - 1) {
            topLeftY -= (bottomRightY - (srcHeight - 1))
            bottomRightY = (srcHeight - 1).toFloat()
        }
        return Rect(topLeftX.toInt(), topLeftY.toInt(), bottomRightX.toInt(), bottomRightY.toInt())
    }
}
