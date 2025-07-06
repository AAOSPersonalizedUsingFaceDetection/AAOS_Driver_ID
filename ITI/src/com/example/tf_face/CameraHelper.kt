package com.example.tf_face

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.SurfaceTexture
import android.hardware.camera2.*
import android.util.Log
import android.view.Surface
import android.view.TextureView
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

class CameraHelper(private val context: Context, private val textureView: TextureView) {

    @SuppressLint("MissingPermission")
    suspend fun startCamera() = suspendCoroutine<Unit> { continuation ->
        if (!textureView.isAvailable) {
            textureView.surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                override fun onSurfaceTextureAvailable(surface: SurfaceTexture, width: Int, height: Int) {
                    openCamera()
                    continuation.resume(Unit)
                }

                override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture, width: Int, height: Int) {}
                override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean = true
                override fun onSurfaceTextureUpdated(surface: SurfaceTexture) {}
            }
        } else {
            openCamera()
            continuation.resume(Unit)
        }
    }

    @SuppressLint("MissingPermission")
    private fun openCamera() {
        val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        try {
            val cameraId = cameraManager.cameraIdList.firstOrNull { id ->
                val characteristics = cameraManager.getCameraCharacteristics(id)
                characteristics.get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_BACK
            } ?: run {
                Log.e("CameraHelper", "No front-facing camera found")
                return
            }

            val surfaceTexture = textureView.surfaceTexture ?: run {
                Log.e("CameraHelper", "SurfaceTexture is null")
                return
            }
            surfaceTexture.setDefaultBufferSize(textureView.width, textureView.height)
            val surface = Surface(surfaceTexture)

            cameraManager.openCamera(cameraId, object : CameraDevice.StateCallback() {
                override fun onOpened(camera: CameraDevice) {
                    Log.d("CameraHelper", "Camera opened: $cameraId")
                    val previewRequestBuilder = camera.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)
                    previewRequestBuilder.addTarget(surface)

                    camera.createCaptureSession(listOf(surface), object : CameraCaptureSession.StateCallback() {
                        override fun onConfigured(session: CameraCaptureSession) {
                            Log.d("CameraHelper", "Capture session configured")
                            session.setRepeatingRequest(previewRequestBuilder.build(), null, null)
                        }

                        override fun onConfigureFailed(session: CameraCaptureSession) {
                            Log.e("CameraHelper", "Capture session configuration failed")
                        }
                    }, null)
                }

                override fun onDisconnected(camera: CameraDevice) {
                    Log.w("CameraHelper", "Camera disconnected")
                    camera.close()
                }

                override fun onError(camera: CameraDevice, error: Int) {
                    Log.e("CameraHelper", "Camera error: $error")
                    camera.close()
                }
            }, null)
        } catch (e: Exception) {
            Log.e("CameraHelper", "Failed to open camera", e)
        }
    }
}