package com.bodiart.file_saver

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.core.net.toUri
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.OutputStream
import java.lang.ref.WeakReference

class ExternalImageFileSaver(context: Context, private val imageBitmap: Bitmap) {
    private val tag = ExternalImageFileSaver::class.java.simpleName

    var filename: String? = null
    var folderName: String? = null
    var addToGallery = false
    var contextRef = WeakReference(context)
    var imageMimeType = "image/*"

    val saveInPictureFolder = true

    private var savedImageUri: Uri? = null


    fun setFilename(name: String): ExternalImageFileSaver {
        this.filename = name
        return this
    }

    fun setFolder(folder: String): ExternalImageFileSaver {
        this.folderName = folder
        return this
    }

    fun addToGallery(add: Boolean) {
        addToGallery = add
    }

    fun setImageMimeType(mimeType: String): ExternalImageFileSaver {
        this.imageMimeType = mimeType
        return this
    }

    @Throws(java.lang.Exception::class)
    fun save() {
        validateFilename()
        val savedImageUri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
            saveAndroidAbove10()
        else
            saveAndroidBefore10()

        addToGallery(savedImageUri)
    }

    private fun validateFilename() {
        if (filename.isNullOrEmpty() || !filename!!.endsWith(".jpg") || !filename!!.endsWith(".png"))
            filename = System.currentTimeMillis().toString() + ".jpg"
    }

    /**
     * Save image file in external Pictures folder
     * @return - uri of saved image
     * */
    @RequiresApi(Build.VERSION_CODES.Q)
    private fun saveAndroidAbove10(): Uri? {
        // context
        val context = contextRef.get() ?: throw Exception("context is null")

        // file location
        val relativeLocation = if (folderName != null)
            Environment.DIRECTORY_PICTURES + File.separator + folderName
        else
            Environment.DIRECTORY_PICTURES

        // content values
        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, filename)
            put(MediaStore.MediaColumns.MIME_TYPE, imageMimeType)
            put(MediaStore.MediaColumns.RELATIVE_PATH, relativeLocation)
        }
        // content resolver
        val resolver = context.contentResolver
        var stream: OutputStream? = null
        try {
            val contentUri: Uri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
            savedImageUri = resolver.insert(contentUri, contentValues)

            if (savedImageUri == null)
                throw IOException("Failed to create new MediaStore record.")

            stream = resolver.openOutputStream(savedImageUri!!)
            if (stream == null) {
                throw IOException("Failed to get output stream.")
            }
            if (!imageBitmap.compress(Bitmap.CompressFormat.JPEG, 95, stream)) {
                throw IOException("Failed to save bitmap.")
            }
        } catch (e: IOException) {
            if (savedImageUri != null) {
                // Don't leave an orphan entry in the MediaStore
                resolver.delete(savedImageUri!!, null, null)
            }
            throw e
        } finally {
            stream?.close()
        }

        return savedImageUri
    }

    private fun saveAndroidBefore10(): Uri? {
        createFoldersIfNeed()

        val folderToSave = getDirInPictures()

        try {
            val folder = File(folderToSave)
            if (!folder.exists()) folder.mkdirs()

            val savedImageFile = File(folderToSave, filename!!)

            if (!savedImageFile.exists()) {
//                file.mkdirs();
                savedImageFile.createNewFile()
            }
            val fOut: OutputStream = FileOutputStream(savedImageFile)
            imageBitmap.compress(
                Bitmap.CompressFormat.JPEG,
                95,
                fOut
            ) // сохранять картинку в jpeg-формате с 85% сжатия.
            fOut.flush()
            fOut.close()
            Log.i(tag, "saveAndroidBefore10: image $filename saved")

            return savedImageFile.toUri()
        } catch (e: java.lang.Exception) {
            Log.e(tag, "saveAndroidBefore10: image $filename save failed", e)
        }
        return null
    }

    private fun createFoldersIfNeed() {
        if (folderName != null)
            createDirInPictures(folderName!!)
    }

    private fun createDirInPictures(dirName: String) {
        val directory = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
            dirName
        )
        if (!directory.exists()) directory.mkdirs()
    }

    private fun getDirInPictures(): String {
        return if (folderName != null)
            Environment.DIRECTORY_PICTURES + File.separator + folderName
        else
            Environment.DIRECTORY_PICTURES
    }

    private fun addToGallery(imageUri: Uri?) {
        if (!addToGallery)
            return
        imageUri ?: return
        val context = contextRef.get() ?: throw Exception("context is null")

        MediaScannerConnection.scanFile(
            context,
            arrayOf(imageUri.path),
            null
        ) { _, _ -> }
    }

}