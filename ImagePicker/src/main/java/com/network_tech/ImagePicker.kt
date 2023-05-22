package com.network_tech

import android.app.Activity
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import androidx.appcompat.app.AlertDialog
import androidx.core.content.FileProvider
import androidx.fragment.app.Fragment
import java.io.File
import java.io.IOException

class ImagePicker {
    /**
     * Returns the gallery/camera imageFile.
     *
     *
     * File object might be null if method is called before calling the openCamera() or openGallery()
     */
    @get:Throws(NullPointerException::class)
    var imageFile: File? = null
        private set

    /**
     * Activity object that will be used while calling startActivityForResult(). Activity then will
     * receive the callbacks to its own onActivityResult() and is responsible of calling the
     * onActivityResult() of the ImagePicker for handling result and being notified.
     */
    private var context: Activity?

    /**
     * Fragment object that will be used while calling startActivityForResult(). Fragment then will
     * receive the callbacks to its own onActivityResult() and is responsible of calling the
     * onActivityResult() of the ImagePicker for handling result and being notified.
     */
    private var fragment: Fragment? = null
    private var pickerDialog: AlertDialog? = null
    private var imagePickerListener: ImagePickerListener? = null

    constructor(activity: Activity) {
        context = activity
        setupPickerDialog()
    }

    constructor(fragment: Fragment) {
        this.fragment = fragment
        context = fragment.activity
        setupPickerDialog()
    }

    fun setImagePickerListener(imagePickerListener: ImagePickerListener) {
        this.imagePickerListener = imagePickerListener
    }

    private fun setupPickerDialog() {
        val pickerItems = arrayOf(
            context!!.getString(R.string.image_utils_dialog_camera),
            context!!.getString(R.string.image_utils_dialog_gallery),
            context!!.getString(android.R.string.cancel)
        )
        val builder = AlertDialog.Builder(context!!)
        builder.setTitle(context!!.getString(R.string.image_utils_dialog_select_your_choice))
        builder.setItems(pickerItems) { dialog: DialogInterface, which: Int ->
            when (which) {
                0 -> openCamera()
                1 -> openGallery()
            }
            dialog.dismiss()
        }
        pickerDialog = builder.create()
    }

    fun showImagePicker() {
        if (pickerDialog != null) pickerDialog!!.show()
    }

    fun dismissImagePicker() {
        if (pickerDialog != null && pickerDialog!!.isShowing) pickerDialog!!.dismiss()
    }

    /**
     * Set the image file. Used in case the existing file needs to be updated with compressed/resized image file
     */
    fun setImageFile(imageFile: File) {
        this.imageFile = imageFile
    }

    /**
     * Handles the result of events that the Activity or Fragment receives on its own
     * onActivityResult(). This method must be called inside the onActivityResult()
     * of the container Activity or Fragment.
     */
    fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent) {
        if (requestCode == Constants.REQ_CODE_GALLERY_IMAGE && resultCode == Activity.RESULT_OK) {
            if (data.data != null) {
                val imagePath = getImagePathFromGallery(context!!, data.data!!)
                if (imagePath != null) {
                    imageFile = File(imagePath)
                    imagePickerListener!!.onImageSelectedFromPicker(imageFile)
                }
            }
        } else if (requestCode == Constants.REQ_CODE_CAMERA_IMAGE && resultCode == Activity.RESULT_OK) {
            if (imageFile != null) {
                imagePickerListener!!.onImageSelectedFromPicker(imageFile)
                revokeUriPermission()
            }
        }
    }

    /**
     * Save the image to device external cache
     */
    fun openCamera() {
        checkListener()
        val imageDirectory = context!!.externalCacheDir
        if (imageDirectory != null) startCameraIntent(imageDirectory.absolutePath)
    }

    /**
     * Save the image to a custom directory
     */
    private fun openCamera(imageDirectory: String) {
        checkListener()
        startCameraIntent(imageDirectory)
    }

    private fun startCameraIntent(imageDirectory: String) {
        try {
            imageFile = createImageFile(imageDirectory)
            if (fragment == null) context!!.startActivityForResult(cameraIntent, Constants.REQ_CODE_CAMERA_IMAGE) else fragment!!.startActivityForResult(cameraIntent, Constants.REQ_CODE_CAMERA_IMAGE)
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }// Put the uri of the image file as intent extra

    // Get a list of all the camera apps

    // Grant uri read/write permissions to the camera apps
    /**
     * Returns the camera intent using FileProvider to avoid the FileUriExposedException in Android N and above
     */
    private val cameraIntent: Intent
        private get() {
            val cameraIntent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)

            // Put the uri of the image file as intent extra
            val imageUri = FileProvider.getUriForFile(
                context!!,
                BuildConfig.LIBRARY_PACKAGE_NAME + ".provider",
                imageFile!!
            )
            cameraIntent.putExtra(MediaStore.EXTRA_OUTPUT, imageUri)

            // Get a list of all the camera apps
            val resolvedIntentActivities = context!!.packageManager
                .queryIntentActivities(cameraIntent, PackageManager.MATCH_DEFAULT_ONLY)

            // Grant uri read/write permissions to the camera apps
            for (resolvedIntentInfo in resolvedIntentActivities) {
                val packageName = resolvedIntentInfo.activityInfo.packageName
                context!!.grantUriPermission(
                    packageName, imageUri,
                    Intent.FLAG_GRANT_WRITE_URI_PERMISSION or
                            Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }
            return cameraIntent
        }

    fun openGallery() {
        checkListener()
        val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
        if (fragment == null) context!!.startActivityForResult(intent, Constants.REQ_CODE_GALLERY_IMAGE) else fragment!!.startActivityForResult(intent, Constants.REQ_CODE_GALLERY_IMAGE)
    }

    private fun getImagePathFromGallery(context: Context, imageUri: Uri): String? {
        var imagePath: String? = null
        val filePathColumn = arrayOf(MediaStore.Images.Media.DATA)
        val cursor = context.contentResolver.query(imageUri, filePathColumn, null, null, null)
        if (cursor != null) {
            cursor.moveToFirst()
            val columnIndex = cursor.getColumnIndex(filePathColumn[0])
            imagePath = cursor.getString(columnIndex)
            cursor.close()
        }
        return imagePath
    }

    @Throws(IOException::class)
    private fun createImageFile(directory: String): File? {
        var imageFile: File? = null
        if (Environment.MEDIA_MOUNTED == Environment.getExternalStorageState()) {
            val storageDir = File(directory)
            if (!storageDir.mkdirs()) {
                if (!storageDir.exists()) {
                    return null
                }
            }
            val imageFileName = "IMG_" + System.currentTimeMillis() + "_"
            imageFile = File.createTempFile(imageFileName, ".jpg", storageDir)
        }
        return imageFile
    }

    /**
     * Revoke access permission for the content URI to the specified package otherwise the permission won't be
     * revoked until the device restarts.
     */
    private fun revokeUriPermission() {
        context!!.revokeUriPermission(
            FileProvider.getUriForFile(
                context!!,
                BuildConfig.LIBRARY_PACKAGE_NAME + ".provider", imageFile!!
            ),
            Intent.FLAG_GRANT_WRITE_URI_PERMISSION or Intent.FLAG_GRANT_READ_URI_PERMISSION
        )
    }

    private fun checkListener() {
        if (imagePickerListener == null) {
            throw RuntimeException("ImagePickerListener must be set before calling openCamera() or openGallery()")
        }
    }

    /**
     * @param directory Directory to save the sampled image
     * @param imageFile Image file which needs to be sampled
     * @param imageSize To calculate the closest inSampleSize that will result into final image while maintaining the
     * aspect ratio of the image
     * @param listener  To return the sampled image file
     */
    fun sampleImageFile(
        directory: File?, imageFile: File, imageSize: Int,
        listener: GetSampledImage.OnImageSampledListener
    ) {
        if (directory != null) {
            val imageDirectory = directory.absolutePath
            val getSampledImage = GetSampledImage()
            getSampledImage.setListener(listener)
            getSampledImage.sampleImage(imageFile.absolutePath, imageDirectory, imageSize)
        } else {
            throw RuntimeException("Provided image directory to save the sampled image file is null")
        }
    }

    interface ImagePickerListener {
        fun onImageSelectedFromPicker(imageFile: File?)
    }
}