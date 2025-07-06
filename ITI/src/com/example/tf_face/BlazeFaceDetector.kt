package com.example.tf_face

import android.content.Context
import android.graphics.Bitmap
import android.graphics.RectF
import android.util.Log
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.support.common.FileUtil
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.sqrt
import kotlin.math.max
import kotlin.math.min

class BlazeFaceDetector(context: Context) {
    private val interpreter: Interpreter
    private val faceNetInterpreter: Interpreter
    private val inputSize = 128
    private val faceNetInputSize = 160

    init {
        val blazeFaceModel = FileUtil.loadMappedFile(context, "models/blaze_face_short_range.tflite")
        val faceNetModel = FileUtil.loadMappedFile(context, "models/facenet.tflite")
        val options = Interpreter.Options()
        interpreter = Interpreter(blazeFaceModel, options)
        faceNetInterpreter = Interpreter(faceNetModel, options)
    }

    fun detect(bitmap: Bitmap): List<RectF> {
        val resized = Bitmap.createScaledBitmap(bitmap, inputSize, inputSize, true)
        val input = convertBitmapToByteBuffer(resized, inputSize)

        val output0 = Array(1) { Array(896) { FloatArray(16) } }
        val output1 = Array(1) { Array(896) { FloatArray(1) } }

        interpreter.runForMultipleInputsOutputs(arrayOf(input), mapOf(0 to output0, 1 to output1))

        return nonMaximumSuppression(decodeDetections(output0[0], output1[0]))
    }

    suspend fun recognizeFace(bitmap: Bitmap, database: AppDatabase, threshold: Float = 0.5f): Pair<String?, Float>? {
        val faces = detect(bitmap)
        if (faces.isEmpty()) {
            Log.d("BlazeFaceDetector", "No faces detected for recognition")
            return null
        }

        val largestFace = faces.maxByOrNull { it.width() * it.height() } ?: return null
        val faceBitmap = cropFace(bitmap, largestFace)
        val embedding = getFaceEmbedding(faceBitmap)

        val knownFaces = database.faceDao().getAllFaces()
        Log.d("BlazeFaceDetector", "Number of known faces in database: ${knownFaces.size}")

        var bestMatch: Pair<String?, Float> = null to 0f
        knownFaces.forEach { knownFace ->
            val similarity = cosineSimilarity(embedding, knownFace.embedding)
            Log.d("BlazeFaceDetector", "Comparing with ${knownFace.name}, similarity: $similarity")
            if (similarity > threshold && similarity > bestMatch.second) {
                bestMatch = knownFace.name to similarity
            }
        }

        return bestMatch.takeIf { it.first != null }
    }

    private fun convertBitmapToByteBuffer(bitmap: Bitmap, size: Int): ByteBuffer {
        val buffer = ByteBuffer.allocateDirect(1 * size * size * 3 * 4)
        buffer.order(ByteOrder.nativeOrder())
        val pixels = IntArray(size * size)
        bitmap.getPixels(pixels, 0, size, 0, 0, size, size)
        for (i in pixels.indices) {
            val pixel = pixels[i]
            val r = (pixel shr 16 and 0xFF) / 255f
            val g = (pixel shr 8 and 0xFF) / 255f
            val b = (pixel and 0xFF) / 255f
//            if (i < 10) {
//                Log.d("FaceDetection", "Pixel $i: R=$r, G=$g, B=$b")
//            }
            buffer.putFloat(r)
            buffer.putFloat(g)
            buffer.putFloat(b)
        }
        buffer.rewind()
        return buffer
    }

    private fun decodeDetections(boxes: Array<FloatArray>, scores: Array<FloatArray>, threshold: Float = 0.5f): List<RectF> {
        val result = mutableListOf<RectF>()
        for (i in scores.indices) {
            val score = scores[i][0]
//            Log.d("FaceDetection", "Raw score $i: $score")
            if (score > threshold) {
                val box = boxes[i]
                // Assume normalized coordinates [0, 1]
                val x = box[0] * inputSize
                val y = box[1] * inputSize
                val w = box[2] * inputSize
                val h = box[3] * inputSize
//                Log.d("FaceDetection", "Raw box $i: x=$x, y=$y, w=$w, h=$h")
                val left = (x - w / 2).coerceIn(0f, inputSize.toFloat())
                val top = (y - h / 2).coerceIn(0f, inputSize.toFloat())
                val right = (x + w / 2).coerceIn(0f, inputSize.toFloat())
                val bottom = (y + h / 2).coerceIn(0f, inputSize.toFloat())
//                Log.d("FaceDetection", "Clamped box $i: left=$left, top=$top, right=$right, bottom=$bottom")
                result.add(RectF(left, top, right, bottom))
            }
        }
        return nonMaximumSuppression(result)
    }

    private fun nonMaximumSuppression(boxes: List<RectF>, iouThreshold: Float = 0.4f): List<RectF> {
        val selected = mutableListOf<RectF>()
        val sortedBoxes = boxes.sortedByDescending { it.width() * it.height() }
        val visited = BooleanArray(boxes.size)

        for (i in sortedBoxes.indices) {
            if (visited[i]) continue
            val boxA = sortedBoxes[i]
            selected.add(boxA)

            for (j in (i + 1) until sortedBoxes.size) {
                if (visited[j]) continue
                val boxB = sortedBoxes[j]
                if (iou(boxA, boxB) > iouThreshold) {
                    visited[j] = true
                }
            }
        }

        return selected
    }
    private fun iou(a: RectF, b: RectF): Float {
        val intersection = RectF(
            maxOf(a.left, b.left),
            maxOf(a.top, b.top),
            minOf(a.right, b.right),
            minOf(a.bottom, b.bottom)
        )
        val interArea = maxOf(0f, intersection.width()) * maxOf(0f, intersection.height())
        val unionArea = a.width() * a.height() + b.width() * b.height() - interArea
        return if (unionArea == 0f) 0f else interArea / unionArea
    }


    fun cropFace(bitmap: Bitmap, rect: RectF): Bitmap {
        // Add 50% margin around the detected face
        val margin = 0.5f
        val width = rect.width() * (1 + 2 * margin)
        val height = rect.height() * (1 + 2 * margin)
        val centerX = rect.centerX()
        val centerY = rect.centerY()

        val paddedRect = RectF(
            centerX - width / 2,
            centerY - height / 2,
            centerX + width / 2,
            centerY + height / 2
        )

        // Convert to bitmap coordinates
        val scaleX = bitmap.width.toFloat() / inputSize
        val scaleY = bitmap.height.toFloat() / inputSize

        val left = max(0f, paddedRect.left * scaleX).toInt()
        val top = max(0f, paddedRect.top * scaleY).toInt()
        val cropWidth = min(bitmap.width.toFloat(), paddedRect.width() * scaleX).toInt()
        val cropHeight = min(bitmap.height.toFloat(), paddedRect.height() * scaleY).toInt()

        // Ensure valid dimensions
        val validWidth = max(1, cropWidth)
        val validHeight = max(1, cropHeight)

        Log.d("BlazeFaceDetector", "Crop coordinates: left=$left, top=$top, width=$validWidth, height=$validHeight")

        return Bitmap.createBitmap(bitmap, left, top, validWidth, validHeight)
    }

    internal fun getFaceEmbedding(faceBitmap: Bitmap): FloatArray {
        Log.d("BlazeFaceDetector", "Input to FaceNet: width=${faceBitmap.width}, height=${faceBitmap.height}")

        val resized = Bitmap.createScaledBitmap(faceBitmap, faceNetInputSize, faceNetInputSize, true)
        val input = convertBitmapToByteBuffer(resized, faceNetInputSize)
        val output = Array(1) { FloatArray(128) }
        faceNetInterpreter.run(input, output)
        return output[0].normalize()
    }

    private fun cosineSimilarity(a: FloatArray, b: FloatArray): Float {
        var dot = 0f
        var normA = 0f
        var normB = 0f
        for (i in a.indices) {
            dot += a[i] * b[i]
            normA += a[i] * a[i]
            normB += b[i] * b[i]
        }
        return dot / (sqrt(normA.toDouble()) * sqrt(normB.toDouble())).toFloat()
    }

    private fun FloatArray.normalize(): FloatArray {
        val norm = sqrt(this.sumByDouble { it.toDouble() * it.toDouble() }).toFloat()
        return FloatArray(size) { this[it] / norm }
    }

    fun close() {
        interpreter.close()
        faceNetInterpreter.close()
    }
}
