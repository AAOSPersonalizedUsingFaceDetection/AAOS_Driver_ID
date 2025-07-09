package com.example.tf_face

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.view.TextureView
import android.widget.ImageView
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import android.content.pm.UserInfo
import kotlinx.coroutines.withContext
import kotlinx.coroutines.delay

class CaptureFramesActivity : AppCompatActivity() {
    private var cameraHelper: CameraHelper? = null
    private var textureView: TextureView? = null
    private var captureButton: Button? = null
    private var statusText: TextView? = null
    private var databaseInitializer: DatabaseInitializer? = null
    private var faceDetector: BlazeFaceDetector? = null
    private var currentFrame = 0
    private val instructions = listOf(
        "Position 1: Face in the middle",
        "Position 2: Face tilted right",
        "Position 3: Face tilted left",
        "Position 4: Face tilted up",
        "Position 5: Face tilted down"
    )
    private var faceOverlay: ImageView? = null
    private var faceOverlayView: FaceOverlayView? = null
    private val overlayImages = listOf(
        R.drawable.center,
        R.drawable.right,
        R.drawable.left,
        R.drawable.up,
        R.drawable.down
    )
    private val CAMERA_PERMISSION_REQUEST_CODE = 101
    private val detectionScope = CoroutineScope(Dispatchers.Default)
    private var isDetectionActive = true


    private var userManagerHelper: UserManagerHelper? = null
    private var userInfo: UserInfo? = null
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // applyTheme()
        try {
            setContentView(R.layout.activity_capture_frames)
        } catch (e: Exception) {
            Log.e("CaptureFramesActivity", "Failed to set content view", e)
            finish()
            return
        }

        // Initialize views
        textureView = findViewById(R.id.cameraTextureView)
        captureButton = findViewById(R.id.btnCapturePosition)
        statusText = findViewById(R.id.statusTextView)
        faceOverlayView = findViewById(R.id.faceOverlayView)
        faceOverlay = findViewById(R.id.faceOverlayImageView)

        if (textureView == null || captureButton == null || statusText == null) {
            Log.e("CaptureFramesActivity", "Failed to initialize views")
            finish()
            return
        }

        databaseInitializer = DatabaseInitializer(this)
        faceDetector = BlazeFaceDetector(this)
        userManagerHelper = UserManagerHelper(this)

        val userName = intent.getStringExtra("user_name") ?: "user_${System.currentTimeMillis()}"

        // Initialize camera
        cameraHelper = CameraHelper(this, textureView!!)

        // Check camera permission
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.CAMERA), CAMERA_PERMISSION_REQUEST_CODE)
        } else {
            initialize()
        }

        // Set initial instruction
        statusText?.text = instructions[currentFrame]
        faceOverlay?.setImageResource(overlayImages[currentFrame])

        captureButton?.setOnClickListener {
            captureFrame(userName)
        }
    }
    

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == CAMERA_PERMISSION_REQUEST_CODE && grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            initialize()
        } else {
            statusText?.text = "Camera permission denied"
        }
    }

    private fun initialize() {
        CoroutineScope(Dispatchers.Main).launch {
            try {
                databaseInitializer?.initializeDatabaseIfNeeded()
                cameraHelper?.startCamera()
                startFaceDetectionLoop()
                showNextInstruction()
            } catch (e: Exception) {
                statusText?.text = "Error initializing: ${e.message}"
            }
        }
    }
    private fun startFaceDetectionLoop() {
        detectionScope.launch {
            while (isDetectionActive) {
                val bitmap = textureView?.bitmap
                if (bitmap != null) {
                    val faces = faceDetector?.detect(bitmap) ?: emptyList()
                    withContext(Dispatchers.Main) {
                        faceOverlayView?.setFaces(faces, bitmap.width, bitmap.height)
                    }
                }
                delay(100)
            }
        }
    }

    private fun showNextInstruction() {
        if (currentFrame < instructions.size) {
            statusText?.text = instructions[currentFrame]
            faceOverlay?.setImageResource(overlayImages[currentFrame])
        }
    }

    private fun captureFrame(userName: String) {
        CoroutineScope(Dispatchers.Main).launch {
            try {
                val bitmap = textureView?.bitmap ?: run {
                    Log.w("CaptureFramesActivity", "Bitmap is null")
                    statusText?.text = "Failed to capture frame"
                    return@launch
                }
                val faces = faceDetector?.detect(bitmap) ?: emptyList()
                if (faces.isEmpty()) {
                    Log.w("CaptureFramesActivity", "No faces detected")
                    statusText?.text = "No face detected. Please try again."
                    return@launch
                }

                val largestFace = faces.maxByOrNull { it.width() * it.height() } ?: return@launch
                val croppedFace = faceDetector?.cropFace(bitmap, largestFace) ?: return@launch
                val imageName = "${userName}_${currentFrame + 1}.jpeg"

                // Get the theme from intent extras
                val theme = intent.getStringExtra("user_theme") ?: "light"
                val success = databaseInitializer?.addFace(userName, croppedFace, imageName, theme) ?: false

                if (success) {
                    currentFrame++
                    Log.d("CaptureFramesActivity", "Captured frame $currentFrame for $userName")
                    statusText?.text = "Captured frame $currentFrame for $userName"
                    if (currentFrame >= 5) {
                        userInfo = userManagerHelper?.createNewUser(userName)
                        if (userInfo == null) {
                            Log.e("CaptureFramesActivity", "Failed to create user")
                            statusText?.text = "Failed to create user"
                            return@launch
                        } else {
                            val user_id = userInfo?.id ?: 0
                            userManagerHelper?.switchUser(user_id)
                            Log.d("CaptureFramesActivity", "Switched to user ID: ${userInfo?.id}")
                        }
                    } else {
                        showNextInstruction()
                    }
                } else {
                    Log.w("CaptureFramesActivity", "Failed to capture frame $currentFrame")
                    statusText?.text = "Failed to capture frame $currentFrame for $userName"
                }
            } catch (e: Exception) {
                Log.e("CaptureFramesActivity", "Error capturing frame", e)
                statusText?.text = "Error capturing frame: ${e.message}"
            }
        }
    }

    // private fun applyTheme() {
    //     val sharedPreferences = getSharedPreferences("AppPrefs", Context.MODE_PRIVATE)
    //     val theme = sharedPreferences.getString("theme", "light")
    //     when (theme) {
    //         "dark" -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
    //         else -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
    //     }
    // }

    override fun onDestroy() {
        super.onDestroy()
        faceDetector?.close()
    }

}