package com.phoenix.ai

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.json.JSONObject

private enum class PhoenixScreen { TODAY, CHAT, SETTINGS }

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
        setContent { PhoenixApp() }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PhoenixApp() {
    val context = LocalContext.current
    val config = remember { PhoenixConfig(context.applicationContext) }
    val api = remember { PhoenixApi(config) }
    var screen by remember { mutableStateOf(PhoenixScreen.TODAY) }

    MaterialTheme {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Column {
                            Text("Phoenix AI")
                            Text(
                                "v${BuildConfig.VERSION_NAME} · 凤凰主系统",
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    }
                )
            },
            bottomBar = {
                NavigationBar {
                    NavigationBarItem(
                        selected = screen == PhoenixScreen.TODAY,
                        onClick = { screen = PhoenixScreen.TODAY },
                        icon = { Text("⚽") },
                        label = { Text("今日扫盘") }
                    )
                    NavigationBarItem(
                        selected = screen == PhoenixScreen.CHAT,
                        onClick = { screen = PhoenixScreen.CHAT },
                        icon = { Text("🔥") },
                        label = { Text("二号凤凰") }
                    )
                    NavigationBarItem(
                        selected = screen == PhoenixScreen.SETTINGS,
                        onClick = { screen = PhoenixScreen.SETTINGS },
                        icon = { Text("⚙") },
                        label = { Text("设置") }
                    )
                }
            }
        ) { padding ->
            Box(
                Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 14.dp)
            ) {
                when (screen) {
                    PhoenixScreen.TODAY -> TodayScreen(api)
                    PhoenixScreen.CHAT -> PhoenixChatScreen(api)
                    PhoenixScreen.SETTINGS -> SettingsScreen(config, api)
                }
            }
        }
    }
}

@Composable
private fun TodayScreen(api: PhoenixApi) {
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var matches by remember { mutableStateOf<List<MatchSummary>>(emptyList()) }
    var selected by remember { mutableStateOf<MatchSummary?>(null) }

    fun refresh() {
        loading = true
        error = null
        api.today { result ->
            loading = false
            result.onSuccess { matches = it }
                .onFailure { error = it.message ?: "读取比赛失败" }
        }
    }

    if (selected != null) {
        MatchLabScreen(
            match = selected!!,
            api = api,
            onBack = { selected = null }
        )
        return
    }

    LaunchedEffect(Unit) { refresh() }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column {
                    Text("今日比赛", style = MaterialTheme.typography.headlineSmall)
                    Text("实时赛程 → 单场数据 → Phoenix 模型")
                }
                OutlinedButton(onClick = { refresh() }, enabled = !loading) {
                    Text(if (loading) "刷新中" else "刷新")
                }
            }
        }

        error?.let { message ->
            item {
                ElevatedCard(Modifier.fillMaxWidth()) {
                    Text(
                        "联网失败：$message\n\n请到“设置”填写 API 地址。",
                        modifier = Modifier.padding(16.dp)
                    )
                }
            }
        }

        if (!loading && error == null && matches.isEmpty()) {
            item {
                ElevatedCard(Modifier.fillMaxWidth()) {
                    Text(
                        "当前没有读取到比赛。若后端处于 demo/mock 模式，会显示演示场次；配置 API-Football 后会显示真实赛程。",
                        modifier = Modifier.padding(16.dp)
                    )
                }
            }
        }

        items(matches, key = { it.matchId }) { match ->
            ElevatedCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { selected = match }
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text(
                        "${match.home}  vs  ${match.away}",
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(5.dp))
                    Text("${match.competition} · ${match.status}")
                    if (match.kickoff.isNotBlank()) Text(match.kickoff)
                    if (match.venue.isNotBlank()) Text(match.venue)
                    Spacer(Modifier.height(8.dp))
                    Text("点击进入单场实验室 →")
                }
            }
        }
    }
}

@Composable
private fun MatchLabScreen(match: MatchSummary, api: PhoenixApi, onBack: () -> Unit) {
    var loading by remember(match.matchId) { mutableStateOf(true) }
    var analysis by remember(match.matchId) { mutableStateOf<MatchAnalysis?>(null) }
    var error by remember(match.matchId) { mutableStateOf<String?>(null) }

    fun analyze() {
        loading = true
        error = null
        api.analyze(match.matchId) { result ->
            loading = false
            result.onSuccess { analysis = it }
                .onFailure { error = it.message ?: "分析失败" }
        }
    }

    LaunchedEffect(match.matchId) { analyze() }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(vertical = 12.dp)
    ) {
        TextButton(onClick = onBack) { Text("← 返回今日比赛") }
        Text(
            "${match.home} vs ${match.away}",
            style = MaterialTheme.typography.headlineSmall
        )
        Text(match.competition)

        Spacer(Modifier.height(12.dp))

        if (loading) {
            LinearProgressIndicator(Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))
            Text("Phoenix 正在融合状态、交锋、阵容、场景与市场数据…")
            return@Column
        }

        error?.let {
            ElevatedCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text("分析失败：$it")
                    Spacer(Modifier.height(8.dp))
                    Button(onClick = { analyze() }) { Text("重试") }
                }
            }
            return@Column
        }

        val p = analysis ?: return@Column

        SectionCard("胜平负概率") {
            Text("主胜 ${(p.homeWin * 100).oneDecimal()}%")
            Text("平局 ${(p.draw * 100).oneDecimal()}%")
            Text("客胜 ${(p.awayWin * 100).oneDecimal()}%")
            Text("模型信心 ${(p.confidence * 100).oneDecimal()}%")
        }

        SectionCard("预期进球") {
            Text("${p.home}  ${p.xgHome.oneDecimal()} xG")
            Text("${p.away}  ${p.xgAway.oneDecimal()} xG")
        }

        SectionCard("比分主路径") {
            p.scores.forEach {
                Text("${it.score}  ${(it.probability * 100).oneDecimal()}%")
            }
        }

        SectionCard("尾部 / 爆冷提醒") {
            p.tailRisks.forEach { Text("• $it") }
        }

        SectionCard("模型解释") {
            p.explanation.forEach { Text("• $it") }
        }

        SectionCard("数据证据") {
            if (p.evidence.isEmpty()) Text("暂无额外证据")
            p.evidence.forEach { Text("• $it") }
        }

        Spacer(Modifier.height(18.dp))
        Text(
            "概率分析仅用于研究与娱乐；真实比赛存在阵容临变、红牌、VAR、天气和随机性。",
            style = MaterialTheme.typography.bodySmall
        )
        Spacer(Modifier.height(20.dp))
    }
}

@Composable
private fun PhoenixChatScreen(api: PhoenixApi) {
    var input by remember { mutableStateOf("") }
    var reply by remember { mutableStateOf("二号凤凰已就位。你可以问我比赛数据、模型逻辑或让它解释单场预测。") }
    var loading by remember { mutableStateOf(false) }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(vertical = 14.dp)
    ) {
        Text("二号凤凰", style = MaterialTheme.typography.headlineSmall)
        Text("通过服务器端 AI 分析，不把模型密钥放进手机。")
        Spacer(Modifier.height(12.dp))

        ElevatedCard(Modifier.fillMaxWidth()) {
            Text(reply, modifier = Modifier.padding(16.dp))
        }

        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = input,
            onValueChange = { input = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("输入问题") },
            minLines = 3
        )
        Spacer(Modifier.height(10.dp))
        Button(
            enabled = input.isNotBlank() && !loading,
            onClick = {
                val message = input.trim()
                loading = true
                api.chat(
                    message = message,
                    context = JSONObject().put("app_version", BuildConfig.VERSION_NAME)
                ) { result ->
                    loading = false
                    reply = result.getOrElse {
                        "请求失败：${it.message}\n请检查 API 地址、访问令牌和服务器 OPENAI_API_KEY。"
                    }
                }
            }
        ) {
            Text(if (loading) "凤凰思考中…" else "发送")
        }
    }
}

@Composable
private fun SettingsScreen(config: PhoenixConfig, api: PhoenixApi) {
    val context = LocalContext.current
    var apiBase by remember { mutableStateOf(config.apiBase()) }
    var token by remember { mutableStateOf(config.accessToken()) }
    var status by remember { mutableStateOf("未测试") }
    var updateState by remember { mutableStateOf<UpdateUiState>(UpdateUiState.Idle) }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(vertical = 14.dp)
    ) {
        Text("系统设置", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(10.dp))

        OutlinedTextField(
            value = apiBase,
            onValueChange = { apiBase = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Phoenix API 地址") },
            placeholder = { Text("https://your-phoenix-api.example.com") }
        )
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = token,
            onValueChange = { token = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("访问令牌（Owner / Premium）") }
        )
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = {
                    config.save(apiBase, token)
                    status = "已保存"
                }
            ) { Text("保存") }

            OutlinedButton(
                onClick = {
                    config.save(apiBase, token)
                    status = "测试中…"
                    api.health { result ->
                        status = result.fold(
                            onSuccess = { "连接成功：$it" },
                            onFailure = { "连接失败：${it.message}" }
                        )
                    }
                }
            ) { Text("测试连接") }
        }

        Text(status, style = MaterialTheme.typography.bodySmall)
        Spacer(Modifier.height(18.dp))

        HorizontalDivider()
        Spacer(Modifier.height(18.dp))
        Text("应用更新", style = MaterialTheme.typography.titleMedium)
        Text("当前版本 v${BuildConfig.VERSION_NAME}")
        Spacer(Modifier.height(8.dp))

        when (val state = updateState) {
            UpdateUiState.Idle -> Unit
            UpdateUiState.Checking -> Text("正在检查 GitHub Release…")
            UpdateUiState.Latest -> Text("已经是最新版本。")
            is UpdateUiState.Error -> Text("检查失败：${state.message}")
            is UpdateUiState.Available -> {
                Text("发现 v${state.update.versionName}")
                Spacer(Modifier.height(6.dp))
                Button(
                    onClick = {
                        context.startActivity(
                            Intent(Intent.ACTION_VIEW, Uri.parse(state.update.downloadUrl))
                        )
                    }
                ) { Text("下载最新版 APK") }
            }
        }

        OutlinedButton(
            enabled = updateState !is UpdateUiState.Checking,
            onClick = {
                updateState = UpdateUiState.Checking
                UpdateChecker.check(BuildConfig.VERSION_NAME) { result ->
                    updateState = result.fold(
                        onSuccess = { if (it == null) UpdateUiState.Latest else UpdateUiState.Available(it) },
                        onFailure = { UpdateUiState.Error(it.message ?: "未知错误") }
                    )
                }
            }
        ) { Text("检查更新") }

        Spacer(Modifier.height(18.dp))
        Text(
            "不要把 OpenAI 或足球数据源的 API Key 填在这里。那些密钥只放在服务器环境变量里。",
            style = MaterialTheme.typography.bodySmall
        )
    }
}

@Composable
private fun SectionCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 10.dp)
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            content()
        }
    }
}

private fun Double.oneDecimal(): String = String.format("%.1f", this)
