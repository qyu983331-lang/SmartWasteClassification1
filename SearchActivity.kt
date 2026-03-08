package com.example.smartwasteclassification

import android.annotation.SuppressLint
import android.content.Context
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.smartwasteclassification.databinding.ActivitySearchBinding
import com.google.android.material.chip.Chip
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*

data class WasteItem(val name: String, val category: String)

class SearchActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySearchBinding
    private lateinit var dbHelper: DatabaseHelper
    private val wasteLibrary = mutableListOf<WasteItem>()
    private val suggestionList = mutableListOf<WasteItem>()
    private lateinit var suggestionAdapter: SuggestionAdapter

    private val commonGarbage = listOf("塑料瓶", "干电池", "纽扣电池", "剩菜", "报纸", "口罩", "碎玻璃", "过期药", "茶叶渣", "旧衣服")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySearchBinding.inflate(layoutInflater)
        setContentView(binding.root)
        dbHelper = DatabaseHelper(this)

        initWasteLibrary()
        setupRecyclerView()
        setupListeners()
        setupSuggestions()
    }

    private fun initWasteLibrary() {
        val items = listOf(
            // --- 厨余垃圾 (湿垃圾) ---
            WasteItem("苹果核", "厨余垃圾"), WasteItem("香蕉皮", "厨余垃圾"), WasteItem("剩菜剩饭", "厨余垃圾"),
            WasteItem("菜叶", "厨余垃圾"), WasteItem("蛋壳", "厨余垃圾"), WasteItem("鱼刺", "厨余垃圾"),
            WasteItem("骨头", "厨余垃圾"), WasteItem("茶叶渣", "厨余垃圾"), WasteItem("咖啡渣", "厨余垃圾"),
            WasteItem("瓜果皮", "厨余垃圾"), WasteItem("过期食品", "厨余垃圾"), WasteItem("花卉植物", "厨余垃圾"),
            WasteItem("中药渣", "厨余垃圾"), WasteItem("虾壳", "厨余垃圾"), WasteItem("蟹壳", "厨余垃圾"),
            WasteItem("西瓜皮", "厨余垃圾"), WasteItem("土豆皮", "厨余垃圾"), WasteItem("花生壳", "厨余垃圾"),
            WasteItem("玉米芯", "厨余垃圾"), WasteItem("面包屑", "厨余垃圾"), WasteItem("饼干", "厨余垃圾"),
            WasteItem("肉类残渣", "厨余垃圾"), WasteItem("蛋糕", "厨余垃圾"), WasteItem("果冻", "厨余垃圾"),
            WasteItem("火锅底料", "厨余垃圾"), WasteItem("鱼头", "厨余垃圾"), WasteItem("虾头", "厨余垃圾"),
            WasteItem("辣椒", "厨余垃圾"), WasteItem("蘑菇", "厨余垃圾"), WasteItem("果核", "厨余垃圾"),
            WasteItem("动物内脏", "厨余垃圾"), WasteItem("调料粉", "厨余垃圾"), WasteItem("干果壳", "厨余垃圾"),
            WasteItem("梨核", "厨余垃圾"), WasteItem("草莓蒂", "厨余垃圾"), WasteItem("烂菜叶", "厨余垃圾"),
            WasteItem("榴莲核", "厨余垃圾"), WasteItem("菠萝蜜核", "厨余垃圾"), WasteItem("樱桃核", "厨余垃圾"),
            WasteItem("芒果核", "厨余垃圾"), WasteItem("荔枝核", "厨余垃圾"), WasteItem("龙眼核", "厨余垃圾"),
            WasteItem("小骨头", "厨余垃圾"), WasteItem("虾线", "厨余垃圾"), WasteItem("蟹黄", "厨余垃圾"),
            WasteItem("鸡骨头", "厨余垃圾"), WasteItem("鸭骨头", "厨余垃圾"), WasteItem("鱼鳞", "厨余垃圾"),
            WasteItem("过期糖果", "厨余垃圾"), WasteItem("过期调料", "厨余垃圾"), WasteItem("剩面条", "厨余垃圾"),
            WasteItem("榴莲壳", "厨余垃圾"), WasteItem("椰子壳", "厨余垃圾"), WasteItem("甘蔗皮", "厨余垃圾"),
            WasteItem("玉米皮", "厨余垃圾"), WasteItem("粽子叶", "厨余垃圾"), WasteItem("豆壳", "厨余垃圾"),
            WasteItem("芝麻渣", "厨余垃圾"), WasteItem("火龙果皮", "厨余垃圾"), WasteItem("柚子皮", "厨余垃圾"),
            WasteItem("猪蹄骨", "厨余垃圾"), WasteItem("排骨", "厨余垃圾"), WasteItem("鸡翅骨", "厨余垃圾"),

            // --- 可回收物 ---
            WasteItem("报纸", "可回收物"), WasteItem("纸箱", "可回收物"), WasteItem("书本", "可回收物"),
            WasteItem("复印纸", "可回收物"), WasteItem("塑料瓶", "可回收物"), WasteItem("洗发水瓶", "可回收物"),
            WasteItem("食用油桶", "可回收物"), WasteItem("易拉罐", "可回收物"), WasteItem("奶粉罐", "可回收物"),
            WasteItem("玻璃瓶", "可回收物"), WasteItem("镜子", "可回收物"), WasteItem("旧衣服", "可回收物"),
            WasteItem("旧鞋子", "可回收物"), WasteItem("毛绒玩具", "可回收物"), WasteItem("电路板", "可回收物"),
            WasteItem("充电器", "可回收物"), WasteItem("电线", "可回收物"), WasteItem("金属餐具", "可回收物"),
            WasteItem("杂志", "可回收物"), WasteItem("信封", "可回收物"), WasteItem("牛奶盒", "可回收物"),
            WasteItem("洗洁精瓶", "可回收物"), WasteItem("果汁盒", "可回收物"), WasteItem("锡纸", "可回收物"),
            WasteItem("雨伞骨架", "可回收物"), WasteItem("塑料玩具", "可回收物"), WasteItem("废铁", "可回收物"),
            WasteItem("废铜", "可回收物"), WasteItem("废铝", "可回收物"), WasteItem("旧书包", "可回收物"),
            WasteItem("手机", "可回收物"), WasteItem("电脑", "可回收物"), WasteItem("鼠标", "可回收物"),
            WasteItem("键盘", "可回收物"), WasteItem("手提包", "可回收物"), WasteItem("塑料脸盆", "可回收物"),
            WasteItem("泡沫箱", "可回收物"), WasteItem("旧家电", "可回收物"), WasteItem("旧电视", "可回收物"),
            WasteItem("旧空调", "可回收物"), WasteItem("铝箔", "可回收物"), WasteItem("玻璃杯", "可回收物"),
            WasteItem("易拉扣", "可回收物"), WasteItem("金属盖", "可回收物"), WasteItem("废弃电扇", "可回收物"),
            WasteItem("废弃暖炉", "可回收物"), WasteItem("废旧电脑", "可回收物"), WasteItem("旧光盘", "可回收物"),
            WasteItem("废旧书籍", "可回收物"), WasteItem("废弃玩具", "可回收物"), WasteItem("金属衣架", "可回收物"),
            WasteItem("废旧地毯", "可回收物"), WasteItem("旧凉席", "可回收物"), WasteItem("塑料挂钩", "可回收物"),
            WasteItem("废旧水管", "可回收物"), WasteItem("旧床单", "可回收物"), WasteItem("旧毛毯", "可回收物"),
            WasteItem("塑料整理箱", "可回收物"), WasteItem("旧书包", "可回收物"), WasteItem("旧帆布鞋", "可回收物"),

            // --- 有害垃圾 ---
            WasteItem("纽扣电池", "有害垃圾"), WasteItem("充电电池", "有害垃圾"), WasteItem("锂电池", "有害垃圾"),
            WasteItem("蓄电池", "有害垃圾"), WasteItem("过期药品", "有害垃圾"), WasteItem("药瓶", "有害垃圾"),
            WasteItem("水银温度计", "有害垃圾"), WasteItem("油漆桶", "有害垃圾"), WasteItem("杀虫剂", "有害垃圾"),
            WasteItem("荧光灯管", "有害垃圾"), WasteItem("节能灯", "有害垃圾"), WasteItem("染发剂壳", "有害垃圾"),
            WasteItem("水银血压计", "有害垃圾"), WasteItem("消毒剂瓶", "有害垃圾"), WasteItem("指甲油", "有害垃圾"),
            WasteItem("老鼠药", "有害垃圾"), WasteItem("除草剂", "有害垃圾"), WasteItem("打印机硒鼓", "有害垃圾"),
            WasteItem("墨粉盒", "有害垃圾"), WasteItem("相纸", "有害垃圾"), WasteItem("洗甲水", "有害垃圾"),
            WasteItem("油漆", "有害垃圾"), WasteItem("软膏", "有害垃圾"), WasteItem("胶水", "有害垃圾"),
            WasteItem("机油桶", "有害垃圾"), WasteItem("农药瓶", "有害垃圾"), WasteItem("化学试剂瓶", "有害垃圾"),
            WasteItem("过期的眼药水", "有害垃圾"), WasteItem("废旧灯管", "有害垃圾"), WasteItem("废弃荧光灯", "有害垃圾"),
            WasteItem("空油漆灌", "有害垃圾"), WasteItem("废农药", "有害垃圾"), WasteItem("废旧油漆盒", "有害垃圾"),
            WasteItem("过期维生素", "有害垃圾"), WasteItem("X光片", "有害垃圾"), WasteItem("过期染发剂", "有害垃圾"),

            // --- 其他垃圾 (干垃圾) ---
            WasteItem("纸巾", "其他垃圾"), WasteItem("湿纸巾", "其他垃圾"), WasteItem("口罩", "其他垃圾"),
            WasteItem("烟蒂", "其他垃圾"), WasteItem("陶瓷碗碟", "其他垃圾"), WasteItem("花盆碎片", "其他垃圾"),
            WasteItem("尘土", "其他垃圾"), WasteItem("一次性餐盒", "其他垃圾"), WasteItem("尿不湿", "其他垃圾"),
            WasteItem("猫砂", "其他垃圾"), WasteItem("橡皮泥", "其他垃圾"), WasteItem("头发", "其他垃圾"),
            WasteItem("牙刷", "其他垃圾"), WasteItem("大骨头", "其他垃圾"), WasteItem("贝壳", "其他垃圾"),
            WasteItem("干电池", "其他垃圾"), WasteItem("碱性电池", "其他垃圾"), WasteItem("海绵", "其他垃圾"),
            WasteItem("牙膏皮", "其他垃圾"), WasteItem("创可贴", "其他垃圾"), WasteItem("棉签", "其他垃圾"),
            WasteItem("毛发", "其他垃圾"), WasteItem("旧地毯", "其他垃圾"), WasteItem("湿垃圾袋", "其他垃圾"),
            WasteItem("一次性筷子", "其他垃圾"), WasteItem("吸管", "其他垃圾"), WasteItem("橡皮", "其他垃圾"),
            WasteItem("笔杆", "其他垃圾"), WasteItem("保鲜膜", "其他垃圾"), WasteItem("污损塑料", "其他垃圾"),
            WasteItem("手纸", "其他垃圾"), WasteItem("湿手纸", "其他垃圾"), WasteItem("手套", "其他垃圾"),
            WasteItem("手机壳", "其他垃圾"), WasteItem("护手霜", "其他垃圾"), WasteItem("护手霜瓶", "其他垃圾"),
            WasteItem("洗手液瓶", "其他垃圾"), WasteItem("榴莲壳", "其他垃圾"), WasteItem("核桃壳", "其他垃圾"),
            WasteItem("餐巾纸", "其他垃圾"), WasteItem("胶带", "其他垃圾"), WasteItem("笔芯", "其他垃圾"),
            WasteItem("零食袋", "其他垃圾"), WasteItem("一次性纸杯", "其他垃圾"), WasteItem("陶瓷餐具", "其他垃圾"),
            WasteItem("一次性尿片", "其他垃圾"), WasteItem("废旧拖把", "其他垃圾"), WasteItem("旧扫帚", "其他垃圾"),
            WasteItem("大块碎瓷片", "其他垃圾"), WasteItem("废旧塑料梳子", "其他垃圾"), WasteItem("圆珠笔", "其他垃圾"),
            WasteItem("旧布鞋", "其他垃圾"), WasteItem("污损严重的书本", "其他垃圾"), WasteItem("脏旧雨伞", "其他垃圾"),
            WasteItem("牙粉盒", "其他垃圾"), WasteItem("一次性剃须刀", "其他垃圾"), WasteItem("旧抹布", "其他垃圾"),
            WasteItem("干燥剂", "其他垃圾"), WasteItem("木制梳子", "其他垃圾"), WasteItem("废旧笔芯", "其他垃圾")
        )
        wasteLibrary.addAll(items)
    }

    private fun setupRecyclerView() {
        suggestionAdapter = SuggestionAdapter(suggestionList) { item ->
            binding.etSearch.setText(item.name)
            binding.rvSuggestions.visibility = View.GONE
            performSearch()
        }
        binding.rvSuggestions.layoutManager = LinearLayoutManager(this)
        binding.rvSuggestions.adapter = suggestionAdapter
    }

    private fun setupListeners() {
        binding.btnBack.setOnClickListener { finish() }
        binding.btnSearch.setOnClickListener { performSearch() }

        binding.etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val input = s.toString().trim()
                if (input.isNotEmpty()) {
                    updateSuggestions(input)
                } else {
                    binding.rvSuggestions.visibility = View.GONE
                }
            }
            override fun afterTextChanged(s: Editable?) {}
        })
    }

    @SuppressLint("NotifyDataSetChanged")
    private fun updateSuggestions(input: String) {
        suggestionList.clear()
        val filtered = wasteLibrary.filter { it.name.contains(input) }
        if (filtered.isNotEmpty()) {
            suggestionList.addAll(filtered)
            suggestionAdapter.notifyDataSetChanged()
            binding.rvSuggestions.visibility = View.VISIBLE
        } else {
            binding.rvSuggestions.visibility = View.GONE
        }
    }

    private fun setupSuggestions() {
        commonGarbage.forEach { keyword ->
            val chip = Chip(this).apply {
                text = keyword
                setOnClickListener {
                    binding.etSearch.setText(keyword)
                    performSearch()
                }
            }
            binding.chipGroupHot.addView(chip)
        }
    }

    private fun performSearch() {
        val keyword = binding.etSearch.text.toString().trim()
        if (keyword.isEmpty()) {
            Toast.makeText(this, "请输入垃圾名称", Toast.LENGTH_SHORT).show()
            return
        }

        binding.rvSuggestions.visibility = View.GONE
        binding.tvSearchResult.text = "正在查询中..."
        binding.tvSuggestion.visibility = View.GONE
        binding.cardResult.visibility = View.VISIBLE

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val result = queryWasteCategory(keyword)
                
                withContext(Dispatchers.Main) {
                    val resultContent = "$keyword -> $result"
                    binding.tvSearchResult.text = resultContent
                    updateResultIcon(result)
                    
                    val suggestion = when {
                        result.contains("可回收") -> "💡 投放建议：请尽量保持清洁干燥，立体包装请压扁投放。"
                        result.contains("有害") -> "💡 投放建议：投放时请注意轻放，易破碎的请连包装一并投放。"
                        result.contains("厨余") || result.contains("湿") -> "💡 投放建议：投放前请沥干水分，纯流质食物请直接倒进下水口。"
                        else -> "💡 投放建议：请尽量沥干水分，投放到指定的垃圾桶中。"
                    }
                    binding.tvSuggestion.text = suggestion
                    binding.tvSuggestion.visibility = View.VISIBLE

                    // 保存搜索历史 (type = 1)
                    val time = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date())
                    dbHelper.addHistory(1, "搜索：$resultContent", time)

                    updateStats(result)
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    binding.tvSearchResult.text = "查询失败：${e.message}"
                }
            }
        }
    }

    private fun updateResultIcon(category: String) {
        val iconRes = when (category) {
            "可回收物" -> R.drawable.ic_recyclable_origin
            "有害垃圾" -> R.drawable.ic_hazardous_origin
            "厨余垃圾" -> R.drawable.ic_kitchen_origin
            else -> R.drawable.ic_other_origin
        }
        binding.ivResultIcon.setImageResource(iconRes)
        binding.ivResultIcon.scaleType = ImageView.ScaleType.FIT_CENTER
    }

    private fun queryWasteCategory(name: String): String {
        wasteLibrary.find { it.name == name }?.let { return it.category }

        return when {
            name.contains("纽扣电池") || name.contains("充电电池") || name.contains("锂电池") -> "有害垃圾"
            name.contains("干电池") || name.contains("5号电池") || name.contains("7号电池") -> "其他垃圾"
            name.contains("纸") || name.contains("瓶") || name.contains("罐") || name.contains("衣") || name.contains("塑料") -> "可回收物"
            name.contains("电池") || name.contains("药") || name.contains("漆") || name.contains("灯") -> "有害垃圾"
            name.contains("菜") || name.contains("果") || name.contains("饭") || name.contains("骨") || name.contains("苹") -> "厨余垃圾"
            else -> "其他垃圾"
        }
    }

    private fun updateStats(category: String) {
        val prefs = getSharedPreferences("waste_stats", Context.MODE_PRIVATE)
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
            .putInt("points", points + 2) // 搜索给 2 积分
            .putInt(categoryKey, categoryCount + 1)
            .apply()
    }

    class SuggestionAdapter(
        private val items: List<WasteItem>,
        private val onClick: (WasteItem) -> Unit
    ) : RecyclerView.Adapter<SuggestionAdapter.ViewHolder>() {

        class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val nameText: TextView = view.findViewById(R.id.tv_waste_name)
            val categoryText: TextView = view.findViewById(R.id.tv_waste_category)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_waste_suggestion, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val item = items[position]
            holder.nameText.text = item.name
            holder.categoryText.text = item.category
            holder.itemView.setOnClickListener { onClick(item) }
        }

        override fun getItemCount() = items.size
    }
}
