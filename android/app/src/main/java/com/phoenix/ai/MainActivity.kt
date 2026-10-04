package com.phoenix.ai

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

data class PhoenixModule(val title: String, val subtitle: String)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { PhoenixHome() }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PhoenixHome() {
    val modules = listOf(
        PhoenixModule("今日扫盘", "联网读取比赛后进入统一分析管线"),
        PhoenixModule("比赛实验室", "胜平负 / 比分 / 总进球 / 半全场 / 尾部情景"),
        PhoenixModule("二号凤凰", "Premium / Owner 对话分析与解释"),
        PhoenixModule("复盘进化", "赛果核验、Brier 评分、权重小步更新"),
        PhoenixModule("Owner 控制台", "最高权限、模型状态、用户与功能开关")
    )

    Scaffold(topBar = { TopAppBar(title = { Text("Phoenix AI 2.0") }) }) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Text("凤凰主系统", style = MaterialTheme.typography.headlineMedium)
                Spacer(Modifier.height(6.dp))
                Text("第一版可扩展骨架：下一步接真实数据、登录、订阅与完整后端。")
            }
            items(modules) { module ->
                ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Text(module.title, style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(4.dp))
                        Text(module.subtitle, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
            item {
                AssistChip(onClick = {}, label = { Text("OWNER · Full Access") })
                Text("统计分析仅供研究与娱乐，不保证任何投注结果。",
                    style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
