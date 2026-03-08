package com.example.smartwasteclassification

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Typeface
import android.location.Address
import android.location.Geocoder
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.webkit.*
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import com.aliyun.imagerecog20190930.Client
import com.aliyun.imagerecog20190930.models.ClassifyingRubbishAdvanceRequest
import com.aliyun.teaopenapi.models.Config
import com.aliyun.teautil.models.RuntimeOptions
import com.bumptech.glide.Glide
import com.example.smartwasteclassification.databinding.ActivityMainBinding
import com.google.android.material.button.MaterialButton
import com.google.android.material.chip.Chip
import com.google.android.material.tabs.TabLayout
import com.google.gson.Gson
import com.google.gson.JsonParser
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileInputStream
import java.text.SimpleDateFormat
import java.util.*

data class EnvironmentalArticle(
    val title: String,
    val summary: String,
    val content: String,
    val url: String = "",
    var imageUrl: String = ""
)

data class BannerData(
    val imageUrl: String,
    val title: String
)

data class QuizQuestion(
    val question: String,
    val options: List<String>,
    val correctIndex: Int
)

data class WasteCategory(
    val id: Int,
    val name: String,
    val iconRes: Int,
    val definition: String,
    val requirement: String,
    val items: List<String>,
    val themeColor: String,
    val tips: String,
    val impact: String
)

data class MallItem(val id: String, val name: String, val price: Int, val icon: Int)

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var dbHelper: DatabaseHelper
    private var currentPhotoPath: String? = null
    private val historyList = mutableListOf<String>()
    private val ownedItems = mutableListOf<String>() // 存储已兑换物品的 ID
    private var currentTreeIconRes: Int = R.drawable.ic_tree_seedling
    private var currentTitle: String = ""
    private var currentMedalRes: Int = 0 // 0 表示未佩戴勋章
    private var currentAvatarFrameRes: Int = R.drawable.bg_avatar_ring // 当前头像框

    // 头像相关状态
    private var avatarUri: String? = null
    private var avatarRes: Int = R.drawable.ic_tree_seedling

    private var totalIdentifiedCount = 0
    private var environmentalPoints = 99999 // 强制设为 99999

    // 分类足迹计数变量
    private var recyclableCount = 0
    private var hazardousCount = 0
    private var kitchenCount = 0
    private var otherCount = 0

    // 连胜计数
    private var quizStreak = 0

    // 记录进入背包的来源页面，默认为商城
    private var lastBackpackSource = R.id.nav_mall

    // 阿里云 AccessKey 信息
    private val ALIYUN_AK = "LTAI5tKafCNeJgQ5pYHPMdp5"
    private val ALIYUN_SK = "JONDvvPA3XsrKWZrGXSGrZ9uwJsh3w"

    // WAQI Token
    private val waqiToken = "2e9247b326638124f082541bf414139a58a218c0"

    private val dailyTips = listOf(
        "一个废旧电池可以污染60万升水，相当于一个人一生的饮水量。",
        "纸张回收利用，每吨可造好纸850公斤，节省木材3立方米。",
        "塑料自然降解需要200至400年，回收1吨废塑料可生产0.7吨二级原料。",
        "易腐垃圾（厨余垃圾）经生物技术就地处理堆肥，每吨可生产0.6吨有机肥。",
        "回收1吨废钢铁可炼好钢0.9吨，比用铁矿石炼钢节约成本47%。",
        "如果您不确定某项物品的分类，请将其投放到“其他垃圾”桶。"
    )

    private val banners = listOf(
        BannerData("https://images.unsplash.com/photo-1532996122724-e3c354a0b15b?auto=format&fit=crop&q=80&w=800", "让垃圾分类成为一种习惯"),
        BannerData("https://images.android.com/photo-1542601906990-b4d3fb778b09?auto=format&fit=crop&q=80&w=800", "守护绿水青山，共建美丽家园"),
        BannerData("https://images.unsplash.com/photo-1501854140801-50d01698950b?auto=format&fit=crop&q=80&w=800", "减少塑料使用，保护海洋生态"),
        BannerData("https://images.unsplash.com/photo-1441974231531-c6227db76b6e?auto=format&fit=crop&q=80&w=800", "植树造林，功上当代，利在千秋")
    )

    private val quizPool = listOf(
        QuizQuestion("废旧电池属于哪类垃圾？", listOf("可回收物", "有害垃圾", "厨余垃圾", "其他垃圾"), 1),
        QuizQuestion("过期的感冒药属于？", listOf("有害垃圾", "其他垃圾", "可回收物", "厨余垃圾"), 0),
        QuizQuestion("干净的快递纸箱属于？", listOf("其他垃圾", "可回收物", "厨余垃圾", "有害垃圾"), 1),
        QuizQuestion("吃剩的苹果核属于？", listOf("可回收物", "有害垃圾", "厨余垃圾", "其他垃圾"), 2),
        QuizQuestion("用过的纸巾（擦鼻涕）属于？", listOf("其他垃圾", "可回收物", "有害垃圾", "厨余垃圾"), 0),
        QuizQuestion("碎掉的陶瓷花盆属于？", listOf("其他垃圾", "可回收物", "厨余垃圾", "有害垃圾"), 0),
        QuizQuestion("大骨头（如猪腿骨）属于哪类垃圾？", listOf("厨余垃圾", "其他垃圾", "有害垃圾", "可回收物"), 1),
        QuizQuestion("旧衣服、旧鞋子属于？", listOf("其他垃圾", "可回收物", "厨余垃圾", "有害垃圾"), 1),
        QuizQuestion("过期化妆品属于哪类垃圾？", listOf("有害垃圾", "其他垃圾", "可回收物", "厨余垃圾"), 0),
        QuizQuestion("碎玻璃属于哪类垃圾？", listOf("可回收物", "有害垃圾", "厨余垃圾", "其他垃圾"), 0),
        QuizQuestion("废日光灯管属于？", listOf("可回收物", "有害垃圾", "厨余垃圾", "其他垃圾"), 1),
        QuizQuestion("杀虫剂瓶属于？", listOf("可回收物", "有害垃圾", "厨余垃圾", "其他垃圾"), 1),
        QuizQuestion("金属罐头盒属于？", listOf("可回收物", "有害垃圾", "厨余垃圾", "其他垃圾"), 0),
        QuizQuestion("剩菜剩饭属于？", listOf("可回收物", "有害垃圾", "厨余垃圾", "其他垃圾"), 2),
        QuizQuestion("西瓜皮属于？", listOf("可回收物", "有害垃圾", "厨余垃圾", "其他垃圾"), 2),
        QuizQuestion("茶叶渣属于？", listOf("可回收物", "有害垃圾", "厨余垃圾", "其他垃圾"), 2),
        QuizQuestion("烟头属于？", listOf("可回收物", "有害垃圾", "厨余垃圾", "其他垃圾"), 3),
        QuizQuestion("一次性筷子属于？", listOf("可回收物", "有害垃圾", "厨余垃圾", "其他垃圾"), 3),
        QuizQuestion("尘土属于？", listOf("可回收物", "有害垃圾", "厨余垃圾", "其他垃圾"), 3),
        QuizQuestion("榴莲壳属于？", listOf("可回收物", "有害垃圾", "厨余垃圾", "其他垃圾"), 3),
        QuizQuestion("过期油漆属于？", listOf("可回收物", "有害垃圾", "厨余垃圾", "其他垃圾"), 1),
        QuizQuestion("报纸属于？", listOf("可回收物", "有害垃圾", "厨余垃圾", "其他垃圾"), 0),
        QuizQuestion("洗发水瓶属于？", listOf("可回收物", "有害垃圾", "厨余垃圾", "其他垃圾"), 0),
        QuizQuestion("落叶属于？", listOf("可回收物", "有害垃圾", "厨余垃圾", "其他垃圾"), 2),
        QuizQuestion("鱼刺属于？", listOf("可回收物", "有害垃圾", "厨余垃圾", "其他垃圾"), 2),
        QuizQuestion("过期护肤品属于？", listOf("可回收物", "有害垃圾", "厨余垃圾", "其他垃圾"), 1),
        QuizQuestion("纸箱属于？", listOf("可回收物", "有害垃圾", "厨余垃圾", "其他垃圾"), 0),
        QuizQuestion("旧家电属于？", listOf("可回收物", "有害垃圾", "厨余垃圾", "其他垃圾"), 0),
        QuizQuestion("水银体温计属于？", listOf("可回收物", "有害垃圾", "厨余垃圾", "其他垃圾"), 1),
        QuizQuestion("猫砂属于？", listOf("可回收物", "有害垃圾", "厨余垃圾", "其他垃圾"), 3)
    )

    private val categoryData = listOf(
        WasteCategory(
            0, "可回收物", R.drawable.ic_recyclable_origin,
            "指适宜回收和资源化利用的生活垃圾。",
            "轻投轻放；清洁干燥，避免污染；立体包装请压扁投放。",
            listOf("报纸", "期刊", "图书", "纸箱", "塑料瓶", "易拉罐", "玻璃杯", "旧衣服", "电路板", "洗发水瓶", "食用油桶"),
            "#2980B9",
            "受污染的纸张（如油腻的披萨盒）、卫生纸、湿纸巾不属于可回收物。",
            "每回收1吨废纸可造好纸850公斤，节省木材3立方米，节能75%。"
        ),
        WasteCategory(
            1, "有害垃圾", R.drawable.ic_hazardous_origin,
            "指对人体健康或自然环境造成直接或潜在危害的生活垃圾。",
            "投放时请保持密封；注意轻放，避免破碎导致有害物质泄漏。",
            listOf("纽扣电池", "充电电池", "过期药品", "水银温度计", "油漆桶", "杀虫剂瓶", "荧光灯管", "蓄电池", "消毒剂"),
            "#C0392B",
            "普通干电池（如5号、7号）目前已实现无汞化，属于其他垃圾。",
            "1颗纽扣电池弃入大自然可污染60万升水，相当于一个人一年的饮水量。"
        ),
        WasteCategory(
            2, "厨余垃圾", R.drawable.ic_kitchen_origin,
            "指易腐烂的、含有机质的生活垃圾。",
            "沥干水分；去袋投放；纯流质食物直接倒进下水口。",
            listOf("剩菜剩饭", "瓜皮果核", "菜叶菜根", "蛋壳", "鱼刺", "小骨头", "茶叶渣", "过期食品", "盆栽残花"),
            "#27AE60",
            "大骨头、榴莲壳、椰子壳因为极难降解且易损坏处理设备，属于其他垃圾。",
            "经过厌氧发酵可产生沼气发电，或通过生物处理转化为有机肥。"
        ),
        WasteCategory(
            3, "其他垃圾", R.drawable.ic_other_origin,
            "指除上述分类以外的其他生活废弃物。",
            "尽量沥干水分；受污染的纸张、烟头、陶瓷碎片投放到此桶。",
            listOf("纸巾", "湿纸巾", "口罩", "烟蒂", "陶瓷碗碟", "大骨头", "榴莲壳", "猫砂", "一次性餐具", "旧毛巾"),
            "#7F8C8D",
            "外卖餐盒清洗干净后属于可回收物，受污染则属于其他垃圾。",
            "主要采取焚烧发电或卫生填埋的方式进行无害化处理。"
        )
    )

    private val mallItems = listOf(
        // --- 荣誉勋章类 ---
        MallItem("item_medal_master", "勋章：分类大师", 500, R.drawable.ic_medal_master),
        MallItem("item_medal_water", "勋章：水滴守望者", 550, R.drawable.ic_medal_water),
        MallItem("item_medal_sky", "勋章：蓝天保卫战", 650, R.drawable.ic_medal_sky),

        // --- 身份称号类 ---
        MallItem("item_seedling", "减碳先锋", 400, R.drawable.ic_title_seedling),
        MallItem("item_title_expert", "分类达人", 450, R.drawable.ic_title_expert),
        MallItem("item_title_spokesman", "环保宣传员", 1200, R.drawable.ic_title_spokesman),

        // --- 植被皮肤类 ---
        MallItem("item_tree_growing", "银杏树皮肤", 200, R.drawable.ic_tree_ginkgo),
        MallItem("item_tree_lush", "樱花树皮肤", 350, R.drawable.ic_tree_sakura),
        MallItem("item_tree_maple", "枫树皮肤", 280, R.drawable.ic_tree_maple),
        MallItem("item_tree_willow", "柳树皮肤", 320, R.drawable.ic_tree_willow),
        MallItem("item_tree_pine", "松树皮肤", 380, R.drawable.ic_tree_pine),

        // --- 头像框类 ---
        MallItem("item_avatar_nature", "自然之息头像框", 150, R.drawable.bg_avatar_ring),
        MallItem("item_avatar_silver", "白银卫士头像框", 500, R.drawable.bg_avatar_silver),
        MallItem("item_avatar_gold", "黄金守护头像框", 1000, R.drawable.bg_avatar_gold),
        MallItem("item_avatar_cyan", "青翠流光头像框", 800, R.drawable.bg_avatar_cyan)
    )

    private var currentArticles = mutableListOf<EnvironmentalArticle>()

    private val requestPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
        if (permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true) fetchLocationAndAQI()
        else requestWaqiAQI(null, null, "自动识别")
    }

    // 分类识别用的相机/相册
    private val takePictureLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) currentPhotoPath?.let { displayAndIdentify(File(it)) }
    }

    private val pickImageLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) result.data?.data?.let { uri -> uriToFile(uri)?.let { displayAndIdentify(it) } }
    }

    // 修改头像用的相机/相册
    private val avatarTakePictureLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
            avatarUri = currentPhotoPath
            avatarRes = 0
            saveStats()
            updateStatsUI()
            Toast.makeText(this, "头像已更新", Toast.LENGTH_SHORT).show()
        }
    }

    private val avatarPickImageLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
            result.data?.data?.let { uri ->
                avatarUri = uri.toString()
                avatarRes = 0
                saveStats()
                updateStatsUI()
                Toast.makeText(this, "头像已更新", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 检查登录状态
        val sharedPreferences = getSharedPreferences("user_prefs", MODE_PRIVATE)
        val isLoggedIn = sharedPreferences.getBoolean("isLoggedIn", false)
        if (!isLoggedIn) {
            val intent = Intent(this, LoginActivity::class.java)
            startActivity(intent)
            finish()
            return
        }

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        dbHelper = DatabaseHelper(this)

        // 获取昵称并显示 (如果没设置昵称则回退到用户名)
        val nickname = sharedPreferences.getString("nickname", sharedPreferences.getString("username", "环保卫士"))
        binding.tvMineUserName.text = nickname

        loadStats()
        // 强制重置积分为 99999
        environmentalPoints = 99999
        saveStats()

        checkPermissions()
        setupListeners()
        setupNavigation()
        setupMallTabs()
        setupBackpackTabs() // 初始化背包分类监听
        initWebView()
        updateDailyTip()
        updateStatsUI()
        setupBanners()
        fetchLatestArticles()
        setupEncyclopedia()

        showPage(R.id.nav_home)

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (binding.layoutQuizPage.root.isVisible) {
                    hideQuizPage()
                } else if (binding.layoutWebview.isVisible) {
                    if (binding.webView.canGoBack()) binding.webView.goBack() else hideWebView()
                } else if (binding.layoutHistoryPage.isVisible || binding.layoutNewsList.isVisible || binding.layoutIdentify.isVisible || binding.layoutMall.isVisible) {
                    showPage(R.id.nav_home)
                } else if (binding.layoutBackpackPage.isVisible) {
                    showPage(lastBackpackSource) // 根据来源返回
                } else {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                }
            }
        })
    }

    // --- 关键优化：重载 onResume 确保返回主页时数据同步刷新 ---
    override fun onResume() {
        super.onResume()
        loadStats()
        updateStatsUI()
    }

    private fun setupMallTabs() {
        binding.tabLayoutMall.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                renderMall(tab?.position ?: 0)
            }
            override fun onTabUnselected(tab: TabLayout.Tab?) {}
            override fun onTabReselected(tab: TabLayout.Tab?) {}
        })
    }

    // 实现：背包分类监听
    private fun setupBackpackTabs() {
        binding.tabLayoutBackpack.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                renderBackpack(tab?.position ?: 0)
            }
            override fun onTabUnselected(tab: TabLayout.Tab?) {}
            override fun onTabReselected(tab: TabLayout.Tab?) {}
        })
    }

    private fun setupEncyclopedia() {
        val libraryRoot = binding.layoutLibrary.root
        val tabLayout = libraryRoot.findViewById<TabLayout>(R.id.tab_layout_category)
        tabLayout?.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                updateCategoryDetail(tab?.position ?: 0)
            }
            override fun onTabUnselected(tab: TabLayout.Tab?) {}
            override fun onTabReselected(tab: TabLayout.Tab?) {}
        })

        updateCategoryDetail(0)
    }

    private fun updateCategoryDetail(position: Int) {
        if (position >= categoryData.size) return
        val category = categoryData[position]

        val libraryRoot = binding.layoutLibrary.root
        val icon = libraryRoot.findViewById<ImageView>(R.id.iv_detail_icon)
        val name = libraryRoot.findViewById<TextView>(R.id.tv_detail_name)
        val definition = libraryRoot.findViewById<TextView>(R.id.tv_detail_definition)
        val requirement = libraryRoot.findViewById<TextView>(R.id.tv_detail_requirement)
        val chipGroup = libraryRoot.findViewById<com.google.android.material.chip.ChipGroup>(R.id.cg_detail_items)
        val tvTips = libraryRoot.findViewById<TextView>(R.id.tv_detail_tips)
        val tvImpact = libraryRoot.findViewById<TextView>(R.id.tv_detail_impact)

        chipGroup?.removeAllViews()

        icon?.setImageResource(category.iconRes)
        name?.text = category.name
        name?.setTextColor(Color.parseColor(category.themeColor))
        definition?.text = category.definition
        requirement?.text = category.requirement
        tvTips?.text = category.tips
        tvImpact?.text = category.impact

        category.items.forEach { itemName ->
            val chip = Chip(this).apply {
                text = itemName
                setChipBackgroundColorResource(R.color.white)
                setTextColor(Color.parseColor("#333333"))
                chipStrokeColor = ColorStateList.valueOf(Color.parseColor(category.themeColor))
                chipStrokeWidth = 2f
            }
            chipGroup?.addView(chip)
        }
    }

    private fun setupBanners() {
        if (banners.isEmpty()) return
        var currentIndex = 0
        lifecycleScope.launch {
            while (true) {
                val banner = banners[currentIndex]
                withContext(Dispatchers.Main) {
                    binding.tvAppTitle.text = banner.title
                    Glide.with(this@MainActivity)
                        .load(banner.imageUrl)
                        .centerCrop()
                        .into(binding.ivBanner)
                }
                delay(20000)
                currentIndex = (currentIndex + 1) % banners.size
            }
        }
    }

    private fun fetchLocationAndAQI() {
        val locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            requestPermissionLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION))
            return
        }

        var isResolved = false
        val locationListener = object : LocationListener {
            override fun onLocationChanged(location: Location) {
                if (!isResolved) {
                    isResolved = true
                    resolveCityAndAQI(location)
                    locationManager.removeUpdates(this)
                }
            }
            @Deprecated("Deprecated in Java")
            override fun onStatusChanged(p0: String?, p1: Int, p2: Bundle?) {}
            override fun onProviderEnabled(p0: String) {}
            override fun onProviderDisabled(p0: String) {}
        }

        try {
            locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 0L, 0f, locationListener)
            locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 0L, 0f, locationListener)
            locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)?.let { if (!isResolved) { isResolved = true; resolveCityAndAQI(it) } }
        } catch (e: Exception) {
            Log.e("Location", "Request location updates failed", e)
            requestWaqiAQI(null, null, "获取失败")
        }
    }

    private fun resolveCityAndAQI(location: Location) {
        lifecycleScope.launch(Dispatchers.IO) {
            var cityName = ""
            try {
                val geocoder = Geocoder(this@MainActivity, Locale.CHINA)
                @Suppress("DEPRECATION")
                val addresses: List<Address>? = geocoder.getFromLocation(location.latitude, location.longitude, 1)
                if (!addresses.isNullOrEmpty()) {
                    val address = addresses[0]
                    cityName = address.locality ?: address.subLocality ?: address.adminArea ?: ""
                    cityName = cityName.replace("市", "").replace("省", "")
                }
            } catch (e: Exception) { Log.e("GEO", "Geocoder failed", e) }
            requestWaqiAQI(location.longitude, location.latitude, cityName)
        }
    }

    private fun requestWaqiAQI(lon: Double?, lat: Double?, geoCityName: String) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val client = OkHttpClient()
                val locationStr = if (lat != null && lon != null) "geo:$lat;$lon" else "here"
                val url = "https://api.waqi.info/feed/$locationStr/?token=$waqiToken"

                val request = Request.Builder().url(url).build()
                client.newCall(request).execute().use { response ->
                    val jsonString = response.body?.string() ?: ""
                    if (response.isSuccessful && jsonString.isNotEmpty()) {
                        val root = JsonParser.parseString(jsonString).asJsonObject
                        if (root.get("status").asString == "ok") {
                            val data = root.getAsJsonObject("data")
                            val aqi = data.get("aqi").asInt
                            var finalCityName = geoCityName
                            if (finalCityName.isEmpty()) {
                                finalCityName = data.getAsJsonObject("city").get("name").asString
                                if (finalCityName.contains(",")) finalCityName = finalCityName.split(",")[0]
                            }
                            withContext(Dispatchers.Main) { updateAQIUI(aqi, finalCityName) }
                        }
                    }
                }
            } catch (e: Exception) { Log.e("AQI", "Fetch failed", e) }
        }
    }

    @SuppressLint("SetTextI18n")
    private fun updateAQIUI(aqi: Int, cityName: String) {
        val (level, color) = when {
            aqi <= 50 -> "优" to "#2ECC71"
            aqi <= 100 -> "良" to "#F1C40F"
            aqi <= 150 -> "轻度污染" to "#E67E22"
            else -> "污染" to "#C0392B"
        }
        binding.tvAqiInfo.text = "AQI $aqi"
        binding.tvAqiInfo.setTextColor(Color.parseColor(color))
        binding.tvEnvStatus.text = "当前位置：${cityName}，空气质量 $level"

        // 首页招呼语也使用昵称
        val prefs = getSharedPreferences("user_prefs", MODE_PRIVATE)
        val name = prefs.getString("nickname", prefs.getString("username", "环保卫士"))
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        binding.tvGreetingText.text = when(hour) {
            in 5..10 -> "早安，$name ☀️"
            in 11..13 -> "午安，$name 🌤️"
            in 14..18 -> "下午好，$name 🌥️"
            else -> "晚上好，$name 🌙"
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun initWebView() {
        binding.webView.apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView?, url: String?) {
                    binding.tvWebviewTitle.text = view?.title ?: "详情"
                    binding.webViewProgressBar.isVisible = false
                }
            }
            webChromeClient = object : WebChromeClient() {
                override fun onProgressChanged(view: WebView?, newProgress: Int) {
                    binding.webViewProgressBar.progress = newProgress
                    binding.webViewProgressBar.isVisible = newProgress < 100
                }
            }
        }
    }

    private fun loadStats() {
        val prefs = getSharedPreferences("waste_stats", Context.MODE_PRIVATE)
        totalIdentifiedCount = prefs.getInt("total_count", 0)
        environmentalPoints = prefs.getInt("points", 99999) // 改为 99999
        currentTreeIconRes = prefs.getInt("current_tree_icon", R.drawable.ic_tree_seedling)
        currentTitle = prefs.getString("current_title", "") ?: ""
        currentMedalRes = prefs.getInt("current_medal", 0)
        currentAvatarFrameRes = prefs.getInt("current_avatar_frame", R.drawable.bg_avatar_ring)

        // 加载足迹分类数据
        recyclableCount = prefs.getInt("recyclable_count", 0)
        hazardousCount = prefs.getInt("hazardous_count", 0)
        kitchenCount = prefs.getInt("kitchen_count", 0)
        otherCount = prefs.getInt("other_count", 0)

        // 加载头像
        avatarUri = prefs.getString("avatar_uri", null)
        avatarRes = prefs.getInt("avatar_res", R.drawable.ic_tree_seedling)

        val ownedJson = prefs.getString("owned_items", "[]")
        val type = object : TypeToken<List<String>>() {}.type
        ownedItems.clear()
        ownedItems.addAll(Gson().fromJson(ownedJson, type))

        val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        if (prefs.getString("last_date", "") != today) {
            val days = prefs.getInt("active_days", 1)
            prefs.edit().putInt("active_days", days + 1).putString("last_date", today).apply()
        }
        binding.tvStatDays.text = prefs.getInt("active_days", 1).toString()
    }

    private fun saveStats() {
        getSharedPreferences("waste_stats", Context.MODE_PRIVATE).edit().apply {
            putInt("total_count", totalIdentifiedCount)
            putInt("points", environmentalPoints)
            putInt("current_tree_icon", currentTreeIconRes)
            // 保存足迹分类数据
            putInt("recyclable_count", recyclableCount)
            putInt("hazardous_count", hazardousCount)
            putInt("kitchen_count", kitchenCount)
            putInt("other_count", otherCount)
            putString("owned_items", Gson().toJson(ownedItems))
            putString("current_title", currentTitle)
            putInt("current_medal", currentMedalRes)
            putInt("current_avatar_frame", currentAvatarFrameRes)
            // 保存头像
            putString("avatar_uri", avatarUri)
            putInt("avatar_res", avatarRes)
            apply()
        }
    }

    @SuppressLint("SetTextI18n")
    private fun updateStatsUI() {
        binding.tvStatCount.text = totalIdentifiedCount.toString()
        binding.tvStatPoints.text = environmentalPoints.toString()
        binding.tvStatCarbon.text = (totalIdentifiedCount * 15).toString() // 模拟减碳量

        // 同时更新个人中心的数据
        binding.tvMinePoints.text = environmentalPoints.toString()
        binding.tvMineCount.text = totalIdentifiedCount.toString()
        binding.tvMineCarbon.text = (totalIdentifiedCount * 15).toString()

        // --- 核心：将最新分类数据同步到足迹 UI ---
        binding.tvMineRecyclableCount.text = recyclableCount.toString()
        binding.tvMineHazardousCount.text = hazardousCount.toString()
        binding.tvMineKitchenCount.text = kitchenCount.toString()
        binding.tvMineOtherCount.text = otherCount.toString()

        val (levelName, nextPoints, iconRes, iconTint) = when {
            environmentalPoints < 100 -> listOf("环保萌新", 100, currentTreeIconRes, "#2ECC71")
            environmentalPoints < 300 -> listOf("环保先行者", 300, currentTreeIconRes, "#2ECC71")
            environmentalPoints < 600 -> listOf("环保小树", 600, currentTreeIconRes, "#2ECC71")
            environmentalPoints < 1200 -> listOf("环保达人", 1200, currentTreeIconRes, "#2ECC71")
            environmentalPoints < 2500 -> listOf("分类专家", 2500, currentTreeIconRes, "#2ECC71")
            else -> listOf("环保大师", 5000, currentTreeIconRes, "#2ECC71")
        }

        // 修改显化逻辑：核心文字显示称号（如有），辅助文字显示等级
        val rawTitle = if (currentTitle.isNotEmpty()) currentTitle else levelName as String
        binding.tvIdentityName.text = rawTitle.replace("称号", "")

        // --- 动态应用美化样式 ---
        // 1. 设置基础属性
        binding.tvIdentityName.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
        binding.tvIdentityName.setTypeface(null, Typeface.BOLD)
        val px12 = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 12f, resources.displayMetrics).toInt()
        val px4 = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 4f, resources.displayMetrics).toInt()
        binding.tvIdentityName.setPadding(px12, px4, px12, px4)

        // 2. 根据当前佩戴物品的特征自动切换样式 (多样化处理)
        // 调整优先级：特定称号关键词 > 勋章 > 皮肤
        when {
            // --- 优先判断：具体称号的专属色彩 (确保换称号必变色) ---
            currentTitle.contains("回收") -> {
                binding.tvIdentityName.setBackgroundResource(R.drawable.bg_title_silver) // 银灰色
                binding.tvIdentityName.setTextColor(Color.parseColor("#616161"))
            }
            currentTitle.contains("宣传") -> {
                binding.tvIdentityName.setBackgroundResource(R.drawable.bg_title_orange) // 橙色
                binding.tvIdentityName.setTextColor(Color.parseColor("#BF360C"))
            }
            // 新增：区分减碳先锋和分类达人
            currentTitle.contains("减碳") -> {
                binding.tvIdentityName.setBackgroundResource(R.drawable.bg_skin_badge) // 嫩绿色风
                binding.tvIdentityName.setTextColor(Color.parseColor("#1B5E20"))
            }
            currentTitle.contains("分类达人") -> {
                binding.tvIdentityName.setBackgroundResource(R.drawable.bg_title_badge) // 纯金色风
                binding.tvIdentityName.setTextColor(Color.parseColor("#E65100"))
            }
            // --- 其次判断：勋章专属样式 (如果没有特殊称号，则按勋章变色) ---
            currentMedalRes != 0 -> {
                when (currentMedalRes) {
                    R.drawable.ic_medal_master -> {
                        binding.tvIdentityName.setBackgroundResource(R.drawable.bg_title_badge) // 金色
                        binding.tvIdentityName.setTextColor(Color.parseColor("#856404"))
                    }
                    R.drawable.ic_medal_sky -> {
                        binding.tvIdentityName.setBackgroundResource(R.drawable.bg_title_cyan) // 青蓝色
                        binding.tvIdentityName.setTextColor(Color.parseColor("#00838F"))
                    }
                    else -> {
                        binding.tvIdentityName.setBackgroundResource(R.drawable.bg_medal_badge) // 蓝色
                        binding.tvIdentityName.setTextColor(Color.parseColor("#1976D2"))
                    }
                }
            }
            // --- 再次判断：皮肤类样式 (如果是换了皮肤但没换称号，显示绿色样式) ---
            currentTreeIconRes != R.drawable.ic_tree_seedling -> {
                binding.tvIdentityName.setBackgroundResource(R.drawable.bg_skin_badge) // 绿色
                when (currentTreeIconRes) {
                    R.drawable.ic_tree_sakura -> binding.tvIdentityName.setTextColor(Color.parseColor("#D81B60")) // 樱花粉色
                    R.drawable.ic_tree_maple -> binding.tvIdentityName.setTextColor(Color.parseColor("#BF360C")) // 枫叶红
                    else -> binding.tvIdentityName.setTextColor(Color.parseColor("#2E7D32"))
                }
            }
            // --- Default style ---
            else -> {
                binding.tvIdentityName.setBackgroundResource(R.drawable.bg_title_badge)
                binding.tvIdentityName.setTextColor(Color.parseColor("#856404"))
            }
        }

        binding.tvUserLevel.text = "当前等级：${levelName as String}"

        binding.pbLevel.max = nextPoints as Int
        binding.pbLevel.progress = environmentalPoints
        binding.tvLevelProgress.text = "$environmentalPoints/$nextPoints"

        // 更新个人中心的进度条
        binding.tvMineLevelName.text = "当前等级：${levelName as String}"
        binding.tvMineLevelProgress.text = "$environmentalPoints/$nextPoints"
        binding.pbMineLevel.max = nextPoints
        binding.pbMineLevel.progress = environmentalPoints

        binding.ivEcoTree.setImageResource(iconRes as Int)
        // 修改：当切换到非默认皮肤时，取消 Tint 限制，显示原始颜色
        if (iconRes == R.drawable.ic_tree_seedling) {
            binding.ivEcoTree.imageTintList = ColorStateList.valueOf(Color.parseColor(iconTint as String))
        } else {
            binding.ivEcoTree.imageTintList = null
        }
        
        binding.tvBackpackCount.text = "已拥有 ${ownedItems.size} 件物品"

        // 更新 UI 显化勋章图标
        if (currentMedalRes != 0) {
            binding.ivActiveMedal.setImageResource(currentMedalRes)
            binding.ivActiveMedal.imageTintList = null // 勋章也取消 Tint
            binding.ivActiveMedal.visibility = View.VISIBLE
        } else {
            // 这里修改：如果没佩戴勋章，显示一个透明的占位图标
            binding.ivActiveMedal.setImageResource(R.drawable.ic_quiz_award)
            binding.ivActiveMedal.imageTintList = ColorStateList.valueOf(Color.LTGRAY)
            binding.ivActiveMedal.alpha = 0.3f
            binding.ivActiveMedal.visibility = View.VISIBLE
        }

        // --- 渲染个人中心头像 ---
        binding.ivMineAvatar.setBackgroundResource(currentAvatarFrameRes)

        // 修改：一旦选择了自定义头像（avatarUri 不为空），就隐藏右下角的相机图标
        binding.ivMineCameraIcon.isVisible = avatarUri.isNullOrEmpty()

        if (avatarRes != 0) {
            binding.ivMineAvatar.setImageResource(avatarRes)
            // 内置头像应用绿色调以符合主题
            if (avatarRes == R.drawable.ic_tree_seedling) {
                binding.ivMineAvatar.imageTintList = ColorStateList.valueOf(Color.parseColor("#2ECC71"))
            } else {
                binding.ivMineAvatar.imageTintList = null
            }
        } else if (!avatarUri.isNullOrEmpty()) {
            binding.ivMineAvatar.imageTintList = null
            Glide.with(this)
                .load(avatarUri)
                .circleCrop()
                .placeholder(R.drawable.ic_tree_seedling)
                .into(binding.ivMineAvatar)
        }
    }

    private fun setupListeners() {
        binding.btnCheckIn.setOnClickListener { handleCheckIn() }
        binding.entryScan.setOnClickListener {
            showIdentifyPage()
        }
        binding.entrySearch.setOnClickListener {
            startActivity(Intent(this, SearchActivity::class.java))
        }
        binding.entryQuiz.setOnClickListener { showQuizPage() }
        binding.entryHistoryMain.setOnClickListener { showHistoryPage() }

        binding.layoutQuizPage.root.findViewById<View>(R.id.btn_quiz_back)?.setOnClickListener { hideQuizPage() }
        binding.btnCamera.setOnClickListener { dispatchTakePictureIntent() }
        binding.btnGallery.setOnClickListener {
            pickImageLauncher.launch(Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI))
        }
        binding.tvMoreArticles.setOnClickListener { showFullNewsPage() }
        binding.btnNewsBack.setOnClickListener { showPage(R.id.nav_home) }
        binding.btnWebviewBack.setOnClickListener { hideWebView() }
        binding.btnHistoryBack.setOnClickListener { showPage(R.id.nav_home) }
        binding.btnIdentifyBack.setOnClickListener { showPage(R.id.nav_home) }

        binding.btnOpenBackpack.setOnClickListener {
            lastBackpackSource = R.id.nav_mall // 从商城进入
            showBackpackPage()
        }
        binding.btnBackpackBack.setOnClickListener { showPage(lastBackpackSource) }

        // --- 首页快捷跳转监听 ---
        binding.ivActiveMedal.setOnClickListener {
            lastBackpackSource = R.id.nav_home // 从首页进入
            showBackpackPage(1)
        }
        binding.ivEcoTree.setOnClickListener {
            lastBackpackSource = R.id.nav_home
            showBackpackPage(2)
        }
        binding.tvIdentityName.setOnClickListener {
            lastBackpackSource = R.id.nav_home
            showBackpackPage(3)
        }

        // --- 个人中心点击监听 ---
        binding.ivMineAvatar.setOnClickListener {
            showAvatarSourceDialog()
        }
        binding.tvMineUserName.setOnClickListener {
            showEditNicknameDialog()
        }
        binding.menuAbout.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("关于我们")
                .setMessage("Smart Waste Classification v1.0\n致力于通过AI技术推动垃圾分类，共建绿色家园。")
                .setPositiveButton("确定", null)
                .show()
        }
        binding.btnLogout.setOnClickListener {
            val sharedPreferences = getSharedPreferences("user_prefs", MODE_PRIVATE)
            sharedPreferences.edit().clear().apply()
            val intent = Intent(this, LoginActivity::class.java)
            startActivity(intent)
            finish()
        }
    }

    private fun showEditNicknameDialog() {
        val sharedPrefs = getSharedPreferences("user_prefs", MODE_PRIVATE)
        val currentNickname = sharedPrefs.getString("nickname", sharedPrefs.getString("username", ""))
        val input = EditText(this)
        input.setText(currentNickname)
        input.setSelection(input.text.length)

        AlertDialog.Builder(this)
            .setTitle("修改昵称")
            .setView(input)
            .setPositiveButton("保存") { _, _ ->
                val newNickname = input.text.toString().trim()
                if (newNickname.isNotEmpty()) {
                    val username = sharedPrefs.getString("username", "") ?: ""
                    if (dbHelper.updateNickname(username, newNickname) > 0) {
                        sharedPrefs.edit().putString("nickname", newNickname).apply()
                        binding.tvMineUserName.text = newNickname
                        updateAQIUI(0, "") // 顺便刷新首页欢迎语
                        Toast.makeText(this, "修改成功", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(this, "修改失败", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    Toast.makeText(this, "昵称不能为空", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun showAvatarSourceDialog() {
        val options = arrayOf("拍照", "从相册选择")
        AlertDialog.Builder(this)
            .setTitle("修改头像")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> dispatchAvatarTakePictureIntent()
                    1 -> avatarPickImageLauncher.launch(Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI))
                }
            }
            .show()
    }

    private fun dispatchAvatarTakePictureIntent() {
        try {
            val file = File.createTempFile("AVATAR_", ".jpg", getExternalFilesDir(Environment.DIRECTORY_PICTURES)).apply { currentPhotoPath = absolutePath }
            val uri = FileProvider.getUriForFile(this, "${packageName}.fileprovider", file)
            avatarTakePictureLauncher.launch(Intent(MediaStore.ACTION_IMAGE_CAPTURE).putExtra(MediaStore.EXTRA_OUTPUT, uri))
        } catch (e: Exception) { Log.e("AvatarCamera", "Intent failed", e) }
    }

    private fun showIdentifyPage() {
        hideMainDecorations()
        binding.layoutIdentify.isVisible = true
        binding.bottomNavigationContainer.isVisible = true
    }

    private fun showHistoryPage() {
        hideMainDecorations()
        binding.layoutHistoryPage.isVisible = true
        binding.bottomNavigationContainer.isVisible = true
        updateHistoryUI()
    }

    private fun showBackpackPage(categoryIndex: Int = 0) {
        hideMainDecorations()
        binding.layoutBackpackPage.isVisible = true
        binding.bottomNavigationContainer.isVisible = true
        // 选中对应的 Tab
        binding.tabLayoutBackpack.getTabAt(categoryIndex)?.select()
        renderBackpack(categoryIndex)
    }

    private fun hideMainDecorations() {
        binding.layoutHome.isVisible = false
        binding.layoutLibrary.root.isVisible = false
        binding.layoutMine.isVisible = false
        binding.layoutNewsList.isVisible = false
        binding.layoutIdentify.isVisible = false
        binding.layoutHistoryPage.isVisible = false
        binding.layoutMall.isVisible = false
        binding.layoutBackpackPage.isVisible = false

        binding.ivBanner.isVisible = false
        binding.bannerMask.isVisible = false
        binding.tvAppTitle.isVisible = false
        binding.cardHeader.isVisible = false
    }

    private fun restoreMainDecorations() {
        binding.ivBanner.isVisible = true
        binding.bannerMask.isVisible = true
        binding.tvAppTitle.isVisible = true
        binding.cardHeader.isVisible = true
    }

    private fun handleCheckIn() {
        val prefs = getSharedPreferences("waste_stats", Context.MODE_PRIVATE)
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        if (prefs.getString("last_check_in", "") == today) {
            Toast.makeText(this, "今日已签到", Toast.LENGTH_SHORT).show()
        } else {
            environmentalPoints += 10
            prefs.edit().putString("last_check_in", today).apply()
            saveStats()
            updateStatsUI()
            Toast.makeText(this, "签到成功 +10 🌿", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showQuizPage() {
        hideMainDecorations()
        binding.container.isVisible = false
        binding.bottomNavigationContainer.isVisible = false
        binding.layoutQuizPage.root.isVisible = true
        
        // 重置本次连胜并显示初始状态
        quizStreak = 0
        refreshQuizStatusUI()
        
        loadNextQuiz()
    }

    private fun hideQuizPage() {
        binding.layoutQuizPage.root.isVisible = false
        binding.container.isVisible = true
        binding.bottomNavigationContainer.isVisible = true
        restoreMainDecorations()
        showPage(binding.bottomNavigation.selectedItemId)
    }

    private fun loadNextQuiz() {
        val quiz = quizPool.random()
        val questionView = binding.layoutQuizPage.root.findViewById<TextView>(R.id.tv_quiz_question)
        val optionsLayout = binding.layoutQuizPage.root.findViewById<LinearLayout>(R.id.layout_quiz_options)

        questionView?.text = quiz.question
        optionsLayout?.removeAllViews()

        val btnHeight = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 56f, resources.displayMetrics).toInt()
        val marginV = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 8f, resources.displayMetrics).toInt()

        quiz.options.forEachIndexed { index, option ->
            val button = MaterialButton(this, null, com.google.android.material.R.attr.materialButtonStyle).apply {
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, btnHeight).apply {
                    setMargins(0, marginV, 0, marginV)
                }
                text = option
                isAllCaps = false
                cornerRadius = 24
                setTextColor(Color.parseColor("#333333"))
                setBackgroundColor(Color.parseColor("#F5F5F5"))
                stateListAnimator = null
                setOnClickListener { checkAnswer(index, quiz.correctIndex, it as MaterialButton, optionsLayout!!) }
            }
            optionsLayout?.addView(button)
        }
    }

    @SuppressLint("SetTextI18n")
    private fun checkAnswer(selectedIndex: Int, correctIndex: Int, selectedButton: MaterialButton, optionsLayout: LinearLayout) {
        // 禁用所有选项，防止重复点击
        for (i in 0 until optionsLayout.childCount) {
            optionsLayout.getChildAt(i).isEnabled = false
        }

        if (selectedIndex == correctIndex) {
            selectedButton.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#2ECC71"))
            selectedButton.setTextColor(Color.WHITE)
            
            // --- 实时更新逻辑 ---
            quizStreak++
            environmentalPoints += 20
            
            saveStats()
            updateStatsUI()
            refreshQuizStatusUI() // 实时刷新挑战页文本
            
            Toast.makeText(this, "回答正确！连胜 +1 🌿", Toast.LENGTH_SHORT).show()
        } else {
            selectedButton.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#E74C3C"))
            selectedButton.setTextColor(Color.WHITE)
            
            // 显化正确答案
            (optionsLayout.getChildAt(correctIndex) as? MaterialButton)?.apply {
                backgroundTintList = ColorStateList.valueOf(Color.parseColor("#2ECC71"))
                setTextColor(Color.WHITE)
            }
            
            // --- 实时更新逻辑 ---
            quizStreak = 0 // 连胜中断
            refreshQuizStatusUI()
            
            Toast.makeText(this, "很遗憾，再接再厉哦", Toast.LENGTH_SHORT).show()
        }

        lifecycleScope.launch {
            delay(1200)
            loadNextQuiz()
        }
    }

    @SuppressLint("SetTextI18n")
    private fun refreshQuizStatusUI() {
        val quizRoot = binding.layoutQuizPage.root
        quizRoot.findViewById<TextView>(R.id.tv_quiz_streak)?.text = "🔥 连胜: $quizStreak"
        quizRoot.findViewById<TextView>(R.id.tv_quiz_score)?.text = "累计环保积分: $environmentalPoints"
    }

    private fun showFullNewsPage() {
        hideMainDecorations()
        binding.layoutNewsList.isVisible = true
        populateArticleContainer(binding.fullNewsContainer, currentArticles)
    }

    private fun populateArticleContainer(container: android.view.ViewGroup, articles: List<EnvironmentalArticle>) {
        container.removeAllViews()
        val inflater = LayoutInflater.from(this)
        articles.forEach { article ->
            val itemView = inflater.inflate(R.layout.item_article, container, false)
            itemView.findViewById<TextView>(R.id.tv_article_title).text = article.title
            itemView.findViewById<TextView>(R.id.tv_article_summary).text = article.summary
            val imgView = itemView.findViewById<ImageView>(R.id.iv_article_img)
            if (article.imageUrl.isNotEmpty()) Glide.with(this).load(article.imageUrl).centerCrop().into(imgView)
            itemView.setOnClickListener { openArticle(article) }
            container.addView(itemView)
        }
    }

    private fun openArticle(article: EnvironmentalArticle) {
        if (article.url.isNotEmpty()) showWebView(article.url)
    }

    private fun showWebView(url: String) {
        binding.layoutWebview.isVisible = true
        binding.webView.loadUrl(url)
    }

    private fun hideWebView() {
        binding.layoutWebview.isVisible = false
        binding.webView.loadUrl("about:blank")
    }

    private fun fetchLatestArticles() {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                delay(500)
                val latest = listOf(
                    EnvironmentalArticle("塑料垃圾的危害", "了解塑料如何影响海洋生物，威胁生态平衡。", "https://www.un.org/zh/observances/environment-day", "https://www.un.org/zh/observances/environment-day", "https://images.unsplash.com/photo-1542601906990-b4d3fb778b09?w=400&h=400&fit=crop"),
                    EnvironmentalArticle("迈向“零浪费”", "学习如何通过分类与循环，实现零浪费的环保生活。", "https://www.un.org/zh/observances/zero-waste-day", "https://www.un.org/zh/observances/zero-waste-day", "https://images.unsplash.com/photo-1501854140801-50d01698950b?w=400&h=400&fit=crop"),
                    EnvironmentalArticle("珍爱每一滴水", "探索水资源保护的重要性，减少日常生活中的水浪费。", "https://www.un.org/zh/observances/water-day", "https://www.un.org/zh/observances/water-day", "https://images.unsplash.com/photo-1500829243541-74b677fecc30?w=400&h=400&fit=crop"),
                    EnvironmentalArticle("守护蓝色海洋", "保护海洋生物多样性，减少海洋塑料污染。", "https://www.un.org/zh/observances/oceans-day", "https://www.un.org/zh/observances/oceans-day", "https://images.unsplash.com/photo-1507525428034-b723cf961d3e?w=400&h=400&fit=crop"),
                    EnvironmentalArticle("地球家园守护", "关爱地球，守护生态，让自然之美世代相传。", "https://www.un.org/zh/observances/earth-day", "https://www.un.org/zh/observances/earth-day", "https://images.unsplash.com/photo-1470071459604-3b5ec3a7fe05?w=400&h=400&fit=crop"),
                    EnvironmentalArticle("生物多样性保护", "地球上的每一个物种都至关重要，共建和谐自然。", "https://www.un.org/zh/observances/biological-diversity-day", "https://www.un.org/zh/observances/biological-diversity-day", "https://images.unsplash.com/photo-1542838132-92c53300491e?w=400&h=400&fit=crop")
                )
                withContext(Dispatchers.Main) {
                    currentArticles.clear()
                    currentArticles.addAll(latest)
                    // 首页只取前 3 篇展示
                    populateArticleContainer(binding.layoutArticlesContainer, currentArticles.take(3))
                }
            } catch (e: Exception) { Log.e("Articles", "Fetch failed", e) }
        }
    }

    private fun setupNavigation() {
        binding.bottomNavigation.setOnItemSelectedListener { item -> showPage(item.itemId); true }
    }

    private fun showPage(navId: Int) {
        binding.layoutHome.isVisible = false
        binding.layoutLibrary.root.isVisible = false
        binding.layoutMine.isVisible = false
        binding.layoutNewsList.isVisible = false
        binding.layoutQuizPage.root.isVisible = false
        binding.layoutHistoryPage.isVisible = false
        binding.layoutMall.isVisible = false
        binding.layoutIdentify.isVisible = false
        binding.layoutBackpackPage.isVisible = false

        when (navId) {
            R.id.nav_home -> {
                binding.layoutHome.isVisible = true
                restoreMainDecorations()
                updateDailyTip()
                updateStatsUI()
                fetchLocationAndAQI()
            }
            R.id.nav_mall -> {
                binding.layoutMall.isVisible = true
                restoreMainDecorations()
                // 修复：根据当前选中的 Tab 索引进行渲染
                val currentTabIndex = binding.tabLayoutMall.selectedTabPosition
                renderMall(if (currentTabIndex >= 0) currentTabIndex else 0)
            }
            R.id.nav_library -> {
                restoreMainDecorations()
                binding.layoutLibrary.root.isVisible = true
            }
            R.id.nav_mine -> {
                hideMainDecorations()
                binding.layoutMine.isVisible = true
                loadStats() // 关键：进入时重新加载最新数据
                updateStatsUI() // 确保数据最新
            }
        }
    }

    private fun renderMall(categoryIndex: Int = 0) {
        binding.mallGrid.removeAllViews()

        // 分类过滤逻辑
        val filteredItems = when (categoryIndex) {
            1 -> mallItems.filter { it.id.contains("medal") } // 勋章
            2 -> mallItems.filter { it.id.contains("tree") }  // 皮肤
            3 -> mallItems.filter { it.id.contains("title") || it.id.contains("seedling") } // 称号
            4 -> mallItems.filter { it.id.contains("avatar") } // 头像框
            else -> mallItems // 全部
        }

        filteredItems.forEach { item ->
            val view = layoutInflater.inflate(R.layout.item_mall_product, binding.mallGrid, false)
            val btn = view.findViewById<MaterialButton>(R.id.btn_exchange)

            view.findViewById<TextView>(R.id.tv_product_name).text = item.name
            view.findViewById<TextView>(R.id.tv_product_price).text = "${item.price} 积分"
            view.findViewById<ImageView>(R.id.iv_product_img).setImageResource(item.icon)

            if (ownedItems.contains(item.id)) {
                btn.text = "已拥有"
                btn.isEnabled = false
                btn.alpha = 0.5f
            } else {
                btn.setOnClickListener {
                    if (environmentalPoints >= item.price) {
                        environmentalPoints -= item.price
                        ownedItems.add(item.id)
                        saveStats()
                        updateStatsUI()
                        renderMall(categoryIndex) // 保持在当前分类刷新
                        Toast.makeText(this, "兑换成功！可在背包中使用", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(this, "积分不足哦", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            binding.mallGrid.addView(view)
        }
    }

    private fun renderBackpack(categoryIndex: Int = 0) {
        binding.ownedItemsGrid.removeAllViews()

        // 1. 获取所有已拥有的物品对象
        val allOwnedItems = mallItems.filter { ownedItems.contains(it.id) }

        // 2. 根据索引进行分类过滤
        val itemsToShow = when (categoryIndex) {
            1 -> allOwnedItems.filter { it.id.contains("medal") } // 勋章
            2 -> allOwnedItems.filter { it.id.contains("tree") }  // 皮肤
            3 -> allOwnedItems.filter { it.id.contains("title") || it.id.contains("seedling") } // 称号
            4 -> allOwnedItems.filter { it.id.contains("avatar") } // 头像框
            else -> allOwnedItems // 全部
        }

        binding.tvEmptyBackpack.isVisible = itemsToShow.isEmpty()

        itemsToShow.forEach { item ->
            val view = layoutInflater.inflate(R.layout.item_backpack_item, binding.ownedItemsGrid, false)
            view.findViewById<TextView>(R.id.tv_item_name).text = item.name
            view.findViewById<ImageView>(R.id.iv_item_icon).setImageResource(item.icon)

            val btnUse = view.findViewById<MaterialButton>(R.id.btn_use_item)

            if (item.id.contains("tree") || item.id.contains("avatar") || item.id.contains("title") || item.id.contains("medal") || item.id.contains("seedling")) {
                val isUsing = when {
                    item.id.contains("tree") -> currentTreeIconRes == item.icon
                    item.id.contains("title") || item.id.contains("seedling") -> currentTitle == item.name
                    item.id.contains("medal") -> currentMedalRes == item.icon
                    item.id.contains("avatar") -> currentAvatarFrameRes == item.icon
                    else -> false
                }

                if (isUsing) {
                    btnUse.text = "使用中"
                    btnUse.isEnabled = false
                } else {
                    btnUse.setOnClickListener {
                        when {
                            item.id.contains("tree") -> currentTreeIconRes = item.icon
                            item.id.contains("title") || item.id.contains("seedling") -> currentTitle = item.name
                            item.id.contains("medal") -> currentMedalRes = item.icon
                            item.id.contains("avatar") -> currentAvatarFrameRes = item.icon
                        }
                        saveStats()
                        updateStatsUI()
                        renderBackpack(categoryIndex) // 保持在当前分类刷新
                    }
                }
            } else {
                btnUse.text = "已拥有"
                btnUse.isEnabled = false
            }

            binding.ownedItemsGrid.addView(view)
        }
    }

    private fun updateDailyTip() { if (dailyTips.isNotEmpty()) binding.tvDailyTip.text = dailyTips.random() }

    private fun checkPermissions() {
        val ps = arrayOf(Manifest.permission.CAMERA, Manifest.permission.ACCESS_FINE_LOCATION)
        if (ps.any { ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED }) requestPermissionLauncher.launch(ps)
        else fetchLocationAndAQI()
    }

    private fun dispatchTakePictureIntent() {
        try {
            val file = File.createTempFile("IMG_", ".jpg", getExternalFilesDir(Environment.DIRECTORY_PICTURES)).apply { currentPhotoPath = absolutePath }
            val uri = FileProvider.getUriForFile(this, "${packageName}.fileprovider", file)
            takePictureLauncher.launch(Intent(MediaStore.ACTION_IMAGE_CAPTURE).putExtra(MediaStore.EXTRA_OUTPUT, uri))
        } catch (e: Exception) { Log.e("Camera", "Intent failed", e) }
    }

    private fun uriToFile(uri: Uri): File? {
        val file = File.createTempFile("IMG_", ".jpg", getExternalFilesDir(Environment.DIRECTORY_PICTURES))
        return try {
            contentResolver.openInputStream(uri)?.use { input -> file.outputStream().use { input.copyTo(it) } }
            file
        } catch (e: Exception) { null }
    }

    private fun displayAndIdentify(file: File) {
        binding.ivImage.setImageBitmap(BitmapFactory.decodeFile(file.absolutePath))
        binding.layoutUploadPlaceholder.isVisible = false
        identifyWaste(file)
    }

    private fun createAliyunClient(): Client {
        val config = Config()
            .setAccessKeyId(ALIYUN_AK)
            .setAccessKeySecret(ALIYUN_SK)
            .setEndpoint("imagerecog.cn-shanghai.aliyuncs.com")
        return Client(config)
    }

    @SuppressLint("SetTextI18n")
    private fun identifyWaste(file: File) {
        binding.tvResult.text = "正在识别中..."
        binding.tvSuggestion.visibility = View.GONE
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val client = createAliyunClient()
                val request = ClassifyingRubbishAdvanceRequest()
                request.imageURLObject = FileInputStream(file)
                val runtime = RuntimeOptions()
                val response = client.classifyingRubbishAdvance(request, runtime)

                withContext(Dispatchers.Main) {
                    if (response.body.data != null && response.body.data.elements.isNotEmpty()) {
                        val element = response.body.data.elements[0]
                        val category = element.category
                        val itemName = element.rubbish
                        val resultText = "识别结果：$itemName -> $category"
                        binding.tvResult.text = resultText

                        // 添加投放建议
                        val suggestion = when {
                            category.contains("可回收") -> "💡 投放建议：请尽量保持清洁干燥，立体包装请压扁投放。"
                            category.contains("有害") -> "💡 投放建议：投放时请注意轻放，易破碎的请连包装一并投放。"
                            category.contains("厨余") || category.contains("湿") -> "💡 投放建议：投放前请沥干水分，去袋投放。"
                            else -> "💡 投放建议：请尽量沥干水分，投放到指定的垃圾桶中。"
                        }
                        binding.tvSuggestion.text = suggestion
                        binding.tvSuggestion.visibility = View.VISIBLE

                        addHistory(resultText)
                        
                        // --- 实时更新分类足迹 ---
                        incrementCategoryCount(category)
                        
                        totalIdentifiedCount++; environmentalPoints += 5; saveStats(); updateStatsUI()
                    } else {
                        binding.tvResult.text = "未能识别出物体，请重试"
                    }
                }
            } catch (e: Exception) {
                Log.e("Identify", "API Error", e)
                withContext(Dispatchers.Main) {
                    binding.tvResult.text = "识别出错：${e.message}"
                }
            }
        }
    }

    private fun incrementCategoryCount(category: String) {
        when {
            category.contains("可回收") -> recyclableCount++
            category.contains("有害") -> hazardousCount++
            category.contains("厨余") || category.contains("湿") -> kitchenCount++
            else -> otherCount++
        }
    }

    private fun addHistory(record: String) {
        val time = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date())
        dbHelper.addHistory(0, record, time)
        updateHistoryUI()
    }

    private fun updateHistoryUI() {
        val h = dbHelper.getHistoryByType(0)
        binding.tvHistoryList.text = if (h.isEmpty()) "暂无记录" else h.joinToString("\n\n")
    }
}
