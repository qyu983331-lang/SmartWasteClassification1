package com.example.smartwasteclassification

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import com.example.smartwasteclassification.databinding.ActivityIdentifyBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

class IdentifyActivity : AppCompatActivity() {

    private lateinit var binding: ActivityIdentifyBinding
    private lateinit var dbHelper: DatabaseHelper
    private var currentPhotoPath: String? = null

    companion object {
        const val ACTION_STATS_UPDATED = "com.example.smartwasteclassification.ACTION_STATS_UPDATED"
    }

    private val hotWastePool = listOf(
        "一次性口罩" to "其他垃圾", "快递纸箱" to "可回收物", "剩菜剩饭" to "厨余垃圾",
        "废旧电池" to "有害垃圾", "奶茶杯" to "其他垃圾", "塑料瓶" to "可回收物",
        "过期药品" to "有害垃圾", "落叶" to "厨余垃圾", "陶瓷碎片" to "其他垃圾", "旧衣物" to "可回收物"
    )

    private val requestPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
        if (permissions[Manifest.permission.CAMERA] != true) {
            Toast.makeText(this, "需要相机权限进行拍照", Toast.LENGTH_SHORT).show()
        }
    }

    private val takePictureLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
            currentPhotoPath?.let { displayAndIdentify(File(it)) }
        }
    }

    private val pickImageLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
            result.data?.data?.let { uri ->
                uriToFile(uri)?.let { displayAndIdentify(it) }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityIdentifyBinding.inflate(layoutInflater)
        setContentView(binding.root)
        dbHelper = DatabaseHelper(this)

        setupListeners()
    }

    private fun setupListeners() {
        binding.btnBack.setOnClickListener { finish() }

        binding.btnCameraIdentify.setOnClickListener {
            if (checkCameraPermission()) {
                dispatchTakePictureIntent()
            } else {
                requestPermissionLauncher.launch(arrayOf(Manifest.permission.CAMERA))
            }
        }

        binding.btnGalleryIdentify.setOnClickListener {
            val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
            pickImageLauncher.launch(intent)
        }
    }

    private fun checkCameraPermission(): Boolean {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
    }

    private fun dispatchTakePictureIntent() {
        try {
            val file = File.createTempFile("IMG_", ".jpg", getExternalFilesDir(Environment.DIRECTORY_PICTURES)).apply {
                currentPhotoPath = absolutePath
            }
            val uri = FileProvider.getUriForFile(this, "${packageName}.fileprovider", file)
            takePictureLauncher.launch(Intent(MediaStore.ACTION_IMAGE_CAPTURE).putExtra(MediaStore.EXTRA_OUTPUT, uri))
        } catch (e: Exception) {
            Log.e("Camera", "Intent failed", e)
        }
    }

    private fun uriToFile(uri: Uri): File? {
        val file = File.createTempFile("IMG_", ".jpg", getExternalFilesDir(Environment.DIRECTORY_PICTURES))
        return try {
            contentResolver.openInputStream(uri)?.use { input ->
                file.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            file
        } catch (e: Exception) {
            null
        }
    }

    private fun displayAndIdentify(file: File) {
        binding.ivPreview.setImageBitmap(BitmapFactory.decodeFile(file.absolutePath))
        binding.tvPlaceholder.visibility = View.GONE
        identifyWaste(file)
    }

    @SuppressLint("SetTextI18n")
    private fun identifyWaste(file: File) {
        binding.cardResult.visibility = View.INVISIBLE
        lifecycleScope.launch(Dispatchers.IO) {
            delay(1500) // 模拟处理延迟
            val res = hotWastePool.random()
            withContext(Dispatchers.Main) {
                val resultContent = "${res.first} -> ${res.second}"
                binding.tvIdentifyResult.text = resultContent
                binding.cardResult.visibility = View.VISIBLE

                // 保存拍照识别历史 (type = 0)
                val time = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date())
                dbHelper.addHistory(0, resultContent, time)

                // 同步增加积分和分类次数
                updateStats(res.second)
            }
        }
    }

    private fun updateStats(category: String) {
        val prefs = getSharedPreferences("waste_stats", MODE_PRIVATE)
        val count = prefs.getInt("total_count", 0)
        val points = prefs.getInt("points", 0)
        
        // 确定对应的分类 Key
        val categoryKey = when {
            category.contains("可回收") -> "recyclable_count"
            category.contains("有害") -> "hazardous_count"
            category.contains("厨余") || category.contains("湿") -> "kitchen_count"
            else -> "other_count"
        }
        val categoryCount = prefs.getInt(categoryKey, 0)

        prefs.edit()
            .putInt("total_count", count + 1)
            .putInt("points", points + 5)
            .putInt(categoryKey, categoryCount + 1)
            .apply()

        // 发送广播通知更新
        val intent = Intent(ACTION_STATS_UPDATED)
        sendBroadcast(intent)
    }
}
