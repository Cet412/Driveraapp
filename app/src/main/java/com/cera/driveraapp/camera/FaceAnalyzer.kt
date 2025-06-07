package com.cera.driveraapp.camera

import android.annotation.SuppressLint
import android.content.Context
import android.util.Log
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceDetectorOptions
import com.google.mlkit.vision.face.FaceDetection
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * Advanced Face Analyzer with:
 * - Drowsiness detection (eyes closed)
 * - Head position tracking
 * - Visual feedback via callback
 * - Performance optimizations
 */
class FaceAnalyzer(
    private val context: Context,
    private val onStateUpdate: (FaceAnalysisResult) -> Unit
) : ImageAnalysis.Analyzer {

    // Configurable thresholds
    companion object {
        const val EYE_CLOSED_THRESHOLD = 0.3f  // 0-1 (lower = more sensitive)
        const val HEAD_TILT_THRESHOLD = 20f     // Degrees
        const val ANALYSIS_INTERVAL_MS = 1000L   // Min delay between processing
    }

    // Enhanced face detection options
    private val detectorOptions = FaceDetectorOptions.Builder()
        .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
        .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL)
        .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL)
        .setContourMode(FaceDetectorOptions.CONTOUR_MODE_NONE)
        .build()

    private val faceDetector = FaceDetection.getClient(detectorOptions)
    private var lastAnalysisTime = 0L

    // Result data structure
    data class FaceAnalysisResult(
        val isDrowsy: Boolean,
        val leftEyeOpenProbability: Float?,
        val rightEyeOpenProbability: Float?,
        val headEulerY: Float?,  // Head tilt left/right
        val headEulerZ: Float?,  // Head tilt up/down
        val error: String? = null
    )

    @SuppressLint("UnsafeOptInUsageError")
    override fun analyze(imageProxy: ImageProxy) {
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastAnalysisTime < ANALYSIS_INTERVAL_MS) {
            imageProxy.close()
            return
        }

        val mediaImage = imageProxy.image ?: run {
            onStateUpdate(FaceAnalysisResult(false, null, null, null, null, "No image data"))
            imageProxy.close()
            return
        }

        val image = InputImage.fromMediaImage(
            mediaImage,
            imageProxy.imageInfo.rotationDegrees
        )

        faceDetector.process(image)
            .addOnSuccessListener { faces ->
                if (faces.isEmpty()) {
                    onStateUpdate(FaceAnalysisResult(false, null, null, null, null, "No face detected"))
                } else {
                    processFaces(faces[0])  // Process only the primary face
                }
                imageProxy.close()
            }
            .addOnFailureListener { e ->
                onStateUpdate(FaceAnalysisResult(false, null, null, null, null, "Detection failed: ${e.message}"))
                imageProxy.close()
            }

        lastAnalysisTime = currentTime
    }

    private fun processFaces(face: Face) {
        // Eye state analysis
        val leftEyeOpen = face.leftEyeOpenProbability ?: -1f
        val rightEyeOpen = face.rightEyeOpenProbability ?: -1f

        // Head position analysis
        val headEulerY = face.headEulerAngleY  // Tilt left/right (-ve left, +ve right)
        val headEulerZ = face.headEulerAngleZ  // Nod up/down

        // Drowsiness logic
        val isDrowsy = when {
            leftEyeOpen < EYE_CLOSED_THRESHOLD &&
                    rightEyeOpen < EYE_CLOSED_THRESHOLD -> true
            abs(headEulerY) > HEAD_TILT_THRESHOLD -> true  // Head tilted sideways
            abs(headEulerZ) > HEAD_TILT_THRESHOLD -> true  // Head nodding
            else -> false
        }

        // Send comprehensive results
        onStateUpdate(
            FaceAnalysisResult(
                isDrowsy = isDrowsy,
                leftEyeOpenProbability = leftEyeOpen,
                rightEyeOpenProbability = rightEyeOpen,
                headEulerY = headEulerY,
                headEulerZ = headEulerZ
            )
        )

        // Log detailed metrics
        Log.d("FaceAnalysis", """
            Eyes: L=${"%.2f".format(leftEyeOpen)} R=${"%.2f".format(rightEyeOpen)}
            Head: Y=${"%.1f".format(headEulerY)}° Z=${"%.1f".format(headEulerZ)}°
            Drowsy: $isDrowsy
        """.trimIndent())
    }

    // Cleanup resources
    fun shutdown() {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                faceDetector.close()
            } catch (e: Exception) {
                Log.e("FaceAnalyzer", "Error closing detector: ${e.message}")
            }
        }
    }
}