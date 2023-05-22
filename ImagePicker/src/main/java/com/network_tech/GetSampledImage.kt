package com.network_tech

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.os.AsyncTask

import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import kotlin.jvm.Throws

class GetSampledImage : AsyncTask<String, Void, File>() {
    private var listener: OnImageSampledListener? = null

    fun setListener(listener: OnImageSampledListener) {
        this.listener = listener
    }

    fun removeListener() {
        listener = null
    }

    fun sampleImage(imagePath: String, imageDirectory: String, requiredImageSize: Int) {
        execute(imagePath, imageDirectory, requiredImageSize.toString())
    }

    /**
     * Params:
     * 0 - imagePath (String)
     * 1 - imageDirectory (String)
     * 2 - requiredImageSize (String)
     */
    override fun doInBackground(vararg params: String): File? {
        try {
            val picturePath = params[0]
            val imageDirectory = params[1]
            val requiredImageSize = Integer.parseInt(params[2])
            if (picturePath != null) {
                val options = BitmapFactory.Options()
                options.inJustDecodeBounds = true
                BitmapFactory.decodeFile(picturePath, options)
                options.inSampleSize =
                    calculateInSampleSize(options, requiredImageSize, requiredImageSize)
                options.inJustDecodeBounds = false
                var imageBitmap: Bitmap? = BitmapFactory.decodeFile(picturePath, options)

                imageBitmap = rotateImageIfRequired(imageBitmap, picturePath)

                if (imageBitmap != null) {
                    return getImageFile(imageBitmap, imageDirectory)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        return null
    }

    /**
     * Calculate an inSampleSize for use in a [BitmapFactory.Options] object when decoding
     * bitmaps using the decode* methods from [BitmapFactory]. This implementation calculates
     * the closest inSampleSize that will result in the final decoded bitmap having a width and
     * height equal to or larger than the requested width and height. This implementation does not
     * ensure a power of 2 is returned for inSampleSize which can be faster when decoding but
     * results in a larger bitmap which isn't as useful for caching purposes.
     *
     * @param options        An options object with out* params already populated (run through a decode*
     * method with inJustDecodeBounds==true
     * @param requiredWidth  The requested width of the resulting bitmap
     * @param requiredHeight The requested height of the resulting bitmap
     * @return The value to be used for inSampleSize
     */
    private fun calculateInSampleSize(
        options: BitmapFactory.Options,
        requiredWidth: Int, requiredHeight: Int
    ): Int {
        // Raw height and width of image
        val height = options.outHeight
        val width = options.outWidth
        var inSampleSize = 1

        if (height > requiredHeight || width > requiredWidth) {

            // Calculate ratios of height and width to requested height and width
            val heightRatio = Math.round(height.toFloat() / requiredHeight.toFloat())
            val widthRatio = Math.round(width.toFloat() / requiredWidth.toFloat())

            // Choose the smallest ratio as inSampleSize value, this will guarantee a final image
            // with both dimensions larger than or equal to the requested height and width.
            inSampleSize = if (heightRatio < widthRatio) heightRatio else widthRatio

            // This offers some additional logic in case the image has a strange
            // aspect ratio. For example, a panorama may have a much larger
            // width than height. In these cases the total pixels might still
            // end up being too large to fit comfortably in memory, so we should
            // be more aggressive with sample down the image (=larger inSampleSize).

            val totalPixels = (width * height).toFloat()

            // Anything more than 2x the requested pixels we'll sample down further
            val totalReqPixelsCap = (requiredWidth * requiredHeight * 2).toFloat()

            while (totalPixels / (inSampleSize * inSampleSize) > totalReqPixelsCap) {
                inSampleSize++
            }
        }
        return inSampleSize
    }

    /**
     * Rotate an image if required.
     *
     * @param img           The image bitmap
     * @param selectedImage Image URI
     * @return The resulted Bitmap after manipulation
     */
    @Throws(IOException::class)
    private fun rotateImageIfRequired(img: Bitmap?, selectedImage: String): Bitmap? {
        val ei = ExifInterface(selectedImage)
        val orientation =
            ei.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)

        return when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> rotateImage(img, 90)
            ExifInterface.ORIENTATION_ROTATE_180 -> rotateImage(img, 180)
            ExifInterface.ORIENTATION_ROTATE_270 -> rotateImage(img, 270)
            else -> img
        }
    }

    private fun rotateImage(img: Bitmap?, degree: Int): Bitmap {
        val matrix = Matrix()
        matrix.postRotate(degree.toFloat())
        val rotatedImg = Bitmap.createBitmap(img!!, 0, 0, img.width, img.height, matrix, true)
        img.recycle()
        return rotatedImg
    }

    private fun getImageFile(bitmap: Bitmap, imageDirectory: String): File? {
        try {
            val mediaStorageDir = File(imageDirectory)
            if (!mediaStorageDir.exists()) {
                if (!mediaStorageDir.mkdirs()) {
                    return null
                }
            }
            val imageFile = File(
                mediaStorageDir.path + File.separator
                        + "IMG_" + System.nanoTime() + ".jpg"
            )

            val outputStream = FileOutputStream(imageFile)
            bitmap.compress(Bitmap.CompressFormat.JPEG, 90, outputStream)
            outputStream.flush()
            outputStream.close()
            return imageFile
        } catch (e: Exception) {
            e.printStackTrace()
        }

        return null
    }

    override fun onPostExecute(file: File?) {
        super.onPostExecute(file)
        if (listener != null && file != null)
            listener!!.onImageSampled(file)
    }

    interface OnImageSampledListener {
        fun onImageSampled(imageFile: File)
    }
}
