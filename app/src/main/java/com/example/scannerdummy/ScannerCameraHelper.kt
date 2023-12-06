package com.example.scannerdummy

import android.hardware.Camera
import android.util.Log

object ScannerCameraHelper {
    fun getCameraParameters(isBackCamera: Boolean): Camera.Parameters? {
        return try {
            val cameraFacing = getCameraFacing(isBackCamera)
            val cameraId = getCameraId(cameraFacing) ?: return null
            Camera.open(cameraId)?.parameters
        } catch (ex: Exception) {
            Log.e("CameraError","${ex.message}")
            null
        }
    }

    private fun getCameraFacing(isBackCamera: Boolean): Int {
        return if (isBackCamera) {
            Camera.CameraInfo.CAMERA_FACING_BACK
        } else {
            Camera.CameraInfo.CAMERA_FACING_FRONT
        }
    }

    private fun getCameraId(cameraFacing: Int): Int? {
        for (cameraId in 0 until Camera.getNumberOfCameras()) {
            val cameraInfo = Camera.CameraInfo()
            Camera.getCameraInfo(cameraId, cameraInfo)
            if (cameraInfo.facing == cameraFacing) {
                return cameraId
            }
        }
        return null
    }
}