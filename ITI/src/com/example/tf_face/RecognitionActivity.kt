package com.example.tf_face

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.view.TextureView
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean
import android.content.pm.UserInfo


class RecognitionActivity : AppCompatActivity() {
    private var faceDetector: BlazeFaceDetector? = null
    private var database: AppDatabase? = null
    private var cameraHelper: CameraHelper? = null
    private var textureView: TextureView? = null
    private var faceOverlayView: FaceOverlayView? = null
    private var greetingText: TextView? = null
    private val isProcessing = AtomicBoolean(false)
    private val coroutineScope = CoroutineScope(Dispatchers.Default)
    private val CAMERA_PERMISSION_REQUEST_CODE = 102

    private val isDialogVisible = AtomicBoolean(false)

    private var userManagerHelper: UserManagerHelper? = null
    private var userInfo: UserInfo? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        applyTheme()
        try {
            setContentView(R.layout.activity_recognition)
        } catch (e: Exception) {
            Log.e("RecognitionActivity", "Failed to set content view", e)
            finish()
            return
        }

        // Initialize views
        textureView = findViewById(R.id.cameraTextureView)
        faceOverlayView = findViewById(R.id.faceOverlayView)
        greetingText = findViewById(R.id.greetingTextView)

        if (textureView == null || faceOverlayView == null || greetingText == null) {
            Log.e("RecognitionActivity", "Failed to initialize views: textureView=$textureView, faceOverlayView=$faceOverlayView, greetingText=$greetingText")
            greetingText?.text = "UI initialization error"
            finish()
            return
        }

        faceDetector = BlazeFaceDetector(this)
        database = AppDatabase.getDatabase(this)
        cameraHelper = CameraHelper(this, textureView!!)
        userManagerHelper = UserManagerHelper(this)

        Log.d("RecognitionActivity", "Activity created, checking camera permission")

        // Check database contents
        CoroutineScope(Dispatchers.Main).launch {
            val faces = database?.faceDao()?.getAllFaces() ?: emptyList()
            Log.d("RecognitionActivity", "Database contains ${faces.size} faces")
            faces.forEach { Log.d("RecognitionActivity", "Face: ${it.name}, ${it.imageUri}") }
            if (faces.isEmpty()) {
                greetingText?.text = "No users in database. Please register a new user."
            }
        }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.CAMERA), CAMERA_PERMISSION_REQUEST_CODE)
        } else {
            startCameraAndRecognition()
        }

        val fabBack = findViewById<Button>(R.id.fabBack)
        fabBack?.setOnClickListener {
            finish()
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == CAMERA_PERMISSION_REQUEST_CODE && grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            Log.d("RecognitionActivity", "Camera permission granted")
            startCameraAndRecognition()
        } else {
            Log.w("RecognitionActivity", "Camera permission denied")
            greetingText?.text = "Camera permission denied. Please grant permission to use face recognition."
        }
    }

    private fun startCameraAndRecognition() {
        Log.d("RecognitionActivity", "Starting camera and recognition")
        CoroutineScope(Dispatchers.Main).launch {
            try {
                cameraHelper?.startCamera()
                Log.d("RecognitionActivity", "Camera started, beginning detection loop")
                startDetectionLoop()
            } catch (e: Exception) {
                Log.e("RecognitionActivity", "Failed to start camera", e)
                greetingText?.text = "Error starting camera: ${e.message}"
            }
        }
    }

    private val recognitionBuffer = mutableListOf<String>()
    private var noFaceCounter = 0
    private  val REQUIRED_CONSECUTIVE_MATCHES = 3
    private  val MAX_NO_FACE_FRAMES = 10
    private var isDialogShowing = false
    private var shouldPauseDetection = false

    private fun startDetectionLoop() {
        coroutineScope.launch {
            Log.d("RecognitionActivity", "Detection loop started")
            while (isActive) {
                // Skip processing if paused or dialog showing
                if (shouldPauseDetection || isDialogShowing) {
                    delay(100)
                    continue
                }

                if (isProcessing.compareAndSet(false, true)) {
                    try {
                        val bitmap = textureView?.bitmap
                        if (bitmap == null) {
                            Log.w("RecognitionActivity", "Bitmap is null, skipping frame")
                            withContext(Dispatchers.Main) {
                                greetingText?.text = "Camera not ready"
                            }
                            continue
                        }

                        Log.d("RecognitionActivity", "Processing frame: ${bitmap.width}x${bitmap.height}")
                        val faces = faceDetector?.detect(bitmap) ?: emptyList()
                        Log.d("RecognitionActivity", "Detected ${faces.size} faces")

                        withContext(Dispatchers.Main) {
                            faceOverlayView?.setFaces(faces, bitmap.width, bitmap.height)

                            if (faces.isEmpty()) {
                                noFaceCounter++
                                if (noFaceCounter >= MAX_NO_FACE_FRAMES) {
                                    recognitionBuffer.clear()
                                    shouldPauseDetection = true
                                    showNoFaceDetectedDialog {
                                        shouldPauseDetection = false
                                        noFaceCounter = 0
                                        greetingText?.text = "Looking for face..."
                                    }
                                } else {
                                    greetingText?.text = "Align your face (${MAX_NO_FACE_FRAMES - noFaceCounter})"
                                }
                                return@withContext
                            } else {
                                noFaceCounter = 0
                                greetingText?.text = "Face detected - processing..."
                            }

                            val largestFace = faces.maxByOrNull { it.width() * it.height() }
                            if (largestFace != null) {
                                val recognition = faceDetector?.recognizeFace(bitmap, database!!, threshold = 0.7f)

                                if (recognition != null) {
                                    val recognizedName = recognition.first.toString()
                                    recognitionBuffer.add(recognizedName)

                                    if (recognitionBuffer.size > REQUIRED_CONSECUTIVE_MATCHES) {
                                        recognitionBuffer.removeAt(0)
                                    }

                                    if (recognitionBuffer.size >= REQUIRED_CONSECUTIVE_MATCHES) {
                                        val allSame = recognitionBuffer.distinct().size == 1
                                        if (allSame) {
                                            isDialogShowing = true
                                            suspendUntilDialogClosed {
                                                showKnownUserDialog(recognizedName, it)
                                                isDialogShowing = false
                                            }
                                            recognitionBuffer.clear()
                                        }
                                    }
                                } else {
                                    if (recognitionBuffer.size >= REQUIRED_CONSECUTIVE_MATCHES &&
                                        recognitionBuffer.all { it == "UNKNOWN" }) {
                                        isDialogShowing = true
                                        suspendUntilDialogClosed {
                                            showUnknownUserDialog {
                                                isDialogShowing = false
                                                it()
                                            }
                                        }
                                        recognitionBuffer.clear()
                                    } else {
                                        recognitionBuffer.add("UNKNOWN")
                                        if (recognitionBuffer.size > REQUIRED_CONSECUTIVE_MATCHES) {
                                            recognitionBuffer.removeAt(0)
                                        }
                                    }
                                }
                            }
                        }
                    } catch (e: Exception) {
                        Log.e("RecognitionActivity", "Frame processing error", e)
                        withContext(Dispatchers.Main) {
                            greetingText?.text = "Error: ${e.message?.take(20)}..."
                        }
                        recognitionBuffer.clear()
                    } finally {
                        isProcessing.set(false)
                    }
                }
                delay(100)
            }
        }
    }


    private suspend fun suspendUntilDialogClosed(
        buildDialog: (onDismiss: () -> Unit) -> Unit
    ) = suspendCancellableCoroutine<Unit> { continuation ->
        buildDialog {
            if (continuation.isActive) continuation.resume(Unit) {}
        }
    }


    private fun showNoFaceDetectedDialog(onDismiss: () -> Unit) {
        isDialogShowing = true
        AlertDialog.Builder(this)
            .setTitle("No Face Detected")
            .setMessage("Can't detect any face. Please:\n\n• Center your face\n• Ensure good lighting\n• Remove obstructions")
            .setPositiveButton("Retry") { _, _ ->
                onDismiss()
                isDialogShowing = false
            }
            .setNegativeButton("Cancel") { _, _ ->
                // Optional: Add any cancel logic here
                onDismiss()
                isDialogShowing = false
            }
            .setOnDismissListener {
                onDismiss()
                isDialogShowing = false
            }
            .setCancelable(false)
            .show()
    }

    /*
        fun listUsers(): List<UserInfo> {
        return userManager.users
    } */
    private fun showKnownUserDialog(name: String, onDismiss: () -> Unit) {
        greetingText?.text= " $name!"
        AlertDialog.Builder(this)
            .setTitle("Known User")
            .setMessage("Welcome back, $name!")
            .setPositiveButton("OK") { _, _ ->
                val userId = userManagerHelper?.getCurrentUserId() ?: 0 //current user signed in omar id =1
                                                                        // new user awadin user id = 2
                Log.d("RecognitionActivity", "Current user ID: $userId")
                val userList = userManagerHelper?.listUsers() ?: emptyList()
                
                var newId = -1

                for (user in userList) {
                    if(user.name == name) {
                        newId = user.id
                    }
                }
                if(newId == -1) {
                    Log.w("RecognitionActivity", "User $name not found in user list")
                }
                if(newId == userId) {
                    val intent = Intent(this, GreetingActivity::class.java)
                    intent.putExtra("user_name", name)
                    Log.d("RecognitionActivity", "New User is same as Current User {NO SWITCHING}")
                    startActivity(intent)
                    finish()
                } else {
                    Log.d("RecognitionActivity", "Switched to user ID: $newId")
                    userManagerHelper?.switchUser(newId)
                }
            }
            .setNegativeButton("NO") { dialog, _ ->
                dialog.dismiss()
                onDismiss()
            }
            .setCancelable(false)
            .show()
    }

    private fun showUnknownUserDialog(onDismiss: () -> Unit) {
        AlertDialog.Builder(this)
            .setTitle("Unknown User")
            .setMessage("You are recognized as unknown. What would you like to do?")
            .setPositiveButton("Register") { _, _ ->
                startActivity(Intent(this, NewUserActivity::class.java))
                finish()
            }
            .setNegativeButton("Continue as Guest") { _, _ ->
                val intent = Intent(this, GreetingActivity::class.java)
                intent.putExtra("user_name", "Guest User")
                val guestUserId = userManagerHelper?.getGuestUserId() ?: -1
                if (guestUserId != -1) {
                    userManagerHelper?.switchUser(guestUserId)
                } else {
                    // Create new guest user
                    val newGuestUser = userManagerHelper?.createNewUser("Guest User", isGuest = true)
                    if (newGuestUser != null) {
                        userManagerHelper?.switchUser(newGuestUser.id)
                    } else {
                    }
                }
                startActivity(intent)
                finish()
            }
            .setNeutralButton("Retry") { dialog, _ ->
                // Just dismiss dialog and allow user to retry
                dialog.dismiss()
                onDismiss() // resume detection loop
            }
            .setOnCancelListener {
                onDismiss()
            }
            .setCancelable(false)
            .show()
    }

    private fun applyTheme() {
        val sharedPreferences = getSharedPreferences("AppPrefs", Context.MODE_PRIVATE)
        val theme = sharedPreferences.getString("theme", "light")
        Log.d("RecognitionActivity", "Applying theme: $theme")
        when (theme) {
            "dark" -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
            else -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d("RecognitionActivity", "Activity destroyed")
        coroutineScope.cancel()
        faceDetector?.close()
    }
}
