package com.example.drawingapp

import android.Manifest
import android.app.Dialog
import android.content.ContentValues
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import android.view.View
import android.widget.*
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import androidx.core.view.get
import androidx.lifecycle.lifecycleScope
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream


class MainActivity : AppCompatActivity() {

    lateinit var drawingView: DrawingView
    lateinit var mImageButtonCurrentPaint: ImageButton
    var customProgressDialog: Dialog? = null
    private var requestPermission: ActivityResultLauncher<Array<String>> =
        registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) {}

    private val openGalleryLauncher: ActivityResultLauncher<Intent> =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK && result.data != null) {
                val imageBackground: ImageView = findViewById(R.id.iv_background)
                Log.i("info", "image url: ${result.data!!.data}")
                imageBackground.setImageURI(result.data?.data)
            } else {
                Log.i("error", "smth wrong : ${result.toString()}")
            }
        }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        drawingView = findViewById(R.id.drawing_view)
        drawingView.setBrushSize(10f)
        setupColorsDrawer()
        setupBrushSizeDialog()
        setupImageButton()
        setupUndoButtons()
        setupSaveButton()
    }

    private fun showProgressDialog() {
        cancelProgressDialog()
        customProgressDialog = Dialog(this)
        customProgressDialog!!.setContentView(R.layout.dialog_custom_progress)
        customProgressDialog!!.show()
    }

    private fun cancelProgressDialog() {
        if (customProgressDialog != null) {
            customProgressDialog!!.dismiss()
            customProgressDialog = null
        }
    }

    private fun setupSaveButton() {
        val saveBtn: ImageButton = findViewById(R.id.ib_save)
        saveBtn.setOnClickListener {
            showProgressDialog()
            requestPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            val frameLayout = findViewById<FrameLayout>(R.id.fl_drawing_container)
            if (isWriteStorageAllowed()) {
                lifecycleScope.launch {
                    saveBitmapFile(drawingView.getBitmapFromView(frameLayout))
                }
            } else {
                Snackbar.make(frameLayout, "Don't have write permission", Snackbar.LENGTH_LONG)
                    .show()
            }
        }
    }

    private fun setupUndoButtons() {
        val ibUndo: ImageButton = findViewById(R.id.ib_undo)
        val ibUndoUndo: ImageButton = findViewById(R.id.ib_undo_undo)
        ibUndo.setOnClickListener {
            drawingView.onClickUndo()
        }

        ibUndoUndo.setOnClickListener {
            drawingView.onClickUndoUndo()
        }
    }


    private fun getImages() {
        val pickIntent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
        openGalleryLauncher.launch(pickIntent)
    }

    private fun requestPermission(permission: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && shouldShowRequestPermissionRationale(
                permission
            )
        ) {
            //meaning we don't have camera access
            Snackbar.make(
                drawingView,
                "cant use camera, because access was denied",
                Snackbar.LENGTH_LONG
            ).show()
        } else {
            requestPermission.launch(arrayOf(permission))
        }
    }


    private fun setupImageButton() {
        val button: ImageButton = findViewById(R.id.ib_image)
        button.setOnClickListener {
            requestPermission(Manifest.permission.READ_EXTERNAL_STORAGE)
            if (isReadStorageAllowed()) {
                getImages()
            }
        }
    }


    private fun setupColorsDrawer() {
        val linearLayoutPaintColors: LinearLayout = findViewById(R.id.ll_paint_colors)
        mImageButtonCurrentPaint = linearLayoutPaintColors[1] as ImageButton
        mImageButtonCurrentPaint.setImageDrawable(
            ContextCompat.getDrawable(
                this,
                R.drawable.pallet_pressed
            )
        )
    }

    private fun setupBrushSizeDialog() {
        val button: ImageButton = findViewById(R.id.ib_brush)
        button.setOnClickListener {
            showBrushSizeDialog()
        }
    }

    private fun showBrushSizeDialog() {
        val brushDialog = Dialog(this)
        brushDialog.setContentView(R.layout.dialog_brush_size)
        brushDialog.setTitle("Brush size")
        val smallBtn: ImageButton = brushDialog.findViewById(R.id.ib_small_brush)
        smallBtn.setOnClickListener {
            drawingView.setBrushSize(10.toFloat())
            brushDialog.dismiss()
        }
        val mediumBtn: ImageButton = brushDialog.findViewById(R.id.ib_medium_brush)
        mediumBtn.setOnClickListener {
            drawingView.setBrushSize(20.toFloat())
            brushDialog.dismiss()
        }
        val largeBtn: ImageButton = brushDialog.findViewById(R.id.ib_big_brush)
        largeBtn.setOnClickListener {
            drawingView.setBrushSize(30.toFloat())
            brushDialog.dismiss()
        }
        brushDialog.show()
    }

    fun colorClicked(view: View) {
        if (view != mImageButtonCurrentPaint) {
            val newPaintButton: ImageButton = view as ImageButton
            drawingView.setBrushColor(newPaintButton.tag.toString())
            //deselecting previous button
            mImageButtonCurrentPaint.setImageDrawable(
                ContextCompat.getDrawable(
                    this,
                    R.drawable.pallet_normal
                )
            )
            //overwriting current pressed button
            mImageButtonCurrentPaint = newPaintButton
            mImageButtonCurrentPaint.setImageDrawable(
                ContextCompat.getDrawable(
                    this,
                    R.drawable.pallet_pressed
                )
            )
        }
    }

    private suspend fun saveBitmapFile(mBitmap: Bitmap?): String {
        var result = ""
        try {
            withContext(Dispatchers.IO) {
                val filename = "${System.currentTimeMillis() / 1000}" +
                        ".png"
                var fos: OutputStream? = null
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    contentResolver?.also { resolver ->
                        val contentValues = ContentValues().apply {
                            put(MediaStore.MediaColumns.DISPLAY_NAME, filename)
                            put(MediaStore.MediaColumns.MIME_TYPE, "image/png")
                            put(
                                MediaStore.MediaColumns.RELATIVE_PATH,
                                Environment.DIRECTORY_PICTURES
                            )
                        }
                        val imageUri: Uri? =
                            resolver.insert(
                                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                                contentValues
                            )
                        fos = imageUri?.let { resolver.openOutputStream(it) }
                        result = imageUri.toString()
                    }
                } else {
                    val imagesDir =
                        Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
                    val image = File(imagesDir, filename)
                    result = image.absolutePath
                    fos = FileOutputStream(image)
                }
                fos?.use {
                    mBitmap!!.compress(Bitmap.CompressFormat.PNG, 90, it)
                }
                runOnUiThread {
                    cancelProgressDialog()
                    if (result.isNotEmpty()) {
                        Toast.makeText(
                            this@MainActivity,
                            "File saved to: $result",
                            Toast.LENGTH_LONG
                        ).show()
                    } else {
                        Toast.makeText(
                            this@MainActivity,
                            "Something went wrong...",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                    shareImage(result)
                }
            }
        } catch (e: Exception) {
            Log.e("error", "MainActivity: saveBitmapFile ERROR ${e.message} ${e.stackTrace}")
        }
        return result
    }


    private fun shareImage(result: String) {
        MediaScannerConnection.scanFile(this, arrayOf(result), null) { path, uri ->
            val shareIntent = Intent()
            shareIntent.action = Intent.ACTION_SEND
            shareIntent.putExtra(Intent.EXTRA_STREAM, uri)
            shareIntent.type = "image/png"
            startActivity(Intent.createChooser(shareIntent, "Share"))
        }
    }

    private fun isReadStorageAllowed(): Boolean {
        val result =
            ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)
        return result == PackageManager.PERMISSION_GRANTED
    }

    private fun isWriteStorageAllowed(): Boolean {
        val result =
            ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
        return result == PackageManager.PERMISSION_GRANTED
    }
}