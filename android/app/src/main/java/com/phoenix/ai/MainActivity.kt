package com.phoenix.ai

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

data class PhoenixModule(val title: String, val subtitle: String)

private sealed interface UpdateUiState {
    data object Idle : UpdateUiState
    data object Checking : UpdateUiState
    data object Latest : UpdateUiState
    data class Available(val update: PhoenixUpdate) : UpdateUiState
    data class Error(val message: String) : UpdateUiState
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { PhoenixHome() }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PhoenixHome() {
    val context = LocalContext.current
    var updateState by remember { mutableStateOf<UpdateUiState>(UpdateUiState.Idle) }

    val modules = listOf(
        PhoenixModule("今日扫盘", "联网读取比赛后进入统一分析管线"),
        PhoenixModule("比赛实验室", "胜平负 / 比分 / 总进球 / 半全场 / 尾部情景"),
        PhoenixModule("二号凤凰", "Premium / Owner 对话分析与解释"),
        PhoenixModule("复盘进化", "赛果核验、Brier 评分、权重小步更新"),
        PhoenixModule("Owner 控制台", "最高权限、模型状态、用户与功能开关")
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Phoenix AI 2.1")
                        Text(
                            "v${BuildConfig.VERSION_NAME}",
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Text("凤凰主系统", style = MaterialTheme.typography.headlineMedium)
                Spacer(Modifier.height(6.dp))
                Text("Android + Phoenix API + GitHub 自动发布更新")
            }

            item {
                ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Text("版本更新", style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(6.dp))

                        when (val state = updateState) {
                            UpdateUiState.Idle -> Text("当前版本 v${BuildConfig.VERSION_NAME}")
                            UpdateUiState.Checking -> Text("正在检查 GitHub 最新版本…")
                            UpdateUiState.Latest -> Text("已经是最新版本。")
                            is UpdateUiState.Error -> Text("检查失败：${state.message}")
                            is UpdateUiState.Available -> {
                                Text("发现新版本 v${state.update.versionName}")
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    state.update.releaseNotes.take(240),
                                    style = MaterialTheme.typography.bodySmall
                                )
                                Spacer(Modifier.height(8.dp))
                                Button(
                                    onClick = {
                                        context.startActivity(
                                            Intent(
                                                Intent.ACTION_VIEW,
                                                Uri.parse(state.update.downloadUrl)
                                            )
                                        )
                                    }
                                ) {
                                    Text("下载最新版 APK")
                                }
                            }
                        }

                        Spacer(Modifier.height(10.dp))
                        OutlinedButton(
                            enabled = updateState !is UpdateUiState.Checking,
                            onClick = {
                                updateState = UpdateUiState.Checking
                                UpdateChecker.check(BuildConfig.VERSION_NAME) { result ->
                                    updateState = result.fold(
                                        onSuccess = { update ->
                                            if (update == null) {
                                                UpdateUiState.Latest
                                            } else {
                                                UpdateUiState.Available(update)
                                            }
                                        },
                                        onFailure = { error ->
                                            UpdateUiState.Error(
                                                error.message ?: "未知网络错误"
                                            )
                                        }
                                    )
                                }
                            }
                        ) {
                            Text("检查更新")
                        }
                    }
                }
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
                AssistChip(
                    onClick = {},
                    label = { Text("OWNER · Full Access") }
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "统计分析仅供研究与娱乐，不保证任何投注结果。",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}
