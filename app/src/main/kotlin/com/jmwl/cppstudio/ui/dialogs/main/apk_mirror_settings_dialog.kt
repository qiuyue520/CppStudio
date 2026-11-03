package com.jmwl.cppstudio.ui.dialogs.main

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jmwl.cppstudio.toolchain.apk_mirror_store
import com.jmwl.cppstudio.toolchain.runtime.MirrorCandidate
import com.jmwl.cppstudio.ui.theme.app_theme_provider
import com.jmwl.cppstudio.ui.toast.app_toast
import kotlinx.coroutines.launch

private const val custom_key = "自定义"

/**
 * 软件源镜像（apk 镜像）切换弹窗。
 *
 * C/C++ 工具链和所有第三方库都来自 apk 仓库，这是本项目唯一的「下载源」设置
 * （Go 版本的 GOPROXY 在 C/C++ 场景没有对应概念）。内置国内镜像单选 + 自定义 host，
 * 一键测速后把最快的自动设为推荐；应用后立即重写 rootfs 的 /etc/apk/repositories。
 */
@Composable
fun apk_mirror_settings_dialog(
    on_dismiss: () -> Unit
) {
    val colors = app_theme_provider.colors
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val current_name = apk_mirror_store.current_display_name()
    var selected_key by remember { mutableStateOf(current_name) }
    var custom_text by remember {
        mutableStateOf(if (current_name == custom_key) apk_mirror_store.current_candidate().host else "")
    }
    var testing by remember { mutableStateOf(false) }
    var latencies by remember { mutableStateOf<Map<String, Long?>>(emptyMap()) }
    var fastest_name by remember { mutableStateOf<String?>(null) }

    fun start_speed_test() {
        if (testing) return
        testing = true
        scope.launch {
            val results = apk_mirror_store.speed_test(custom_text.takeIf { it.isNotBlank() })
            latencies = results.associate { it.candidate.name to it.latencyMs }
            fastest_name = results.firstOrNull { it.latencyMs != null }?.candidate?.name
            fastest_name?.let { selected_key = it }
            testing = false
        }
    }

    fun apply_selection() {
        val candidate = if (selected_key == custom_key) {
            val host = custom_text.trim()
                .removePrefix("https://")
                .removePrefix("http://")
                .substringBefore('/')
            if (host.isBlank()) {
                app_toast.show(context, "请输入镜像主机名", app_toast.LENGTH_SHORT)
                return
            }
            MirrorCandidate(custom_key, host, "")
        } else {
            apk_mirror_store.builtin_mirrors.firstOrNull { it.name == selected_key }
        }
        if (candidate == null) {
            app_toast.show(context, "请选择镜像源", app_toast.LENGTH_SHORT)
            return
        }
        apk_mirror_store.set(context, candidate)
        app_toast.show(context, "已切换到 ${candidate.name}", app_toast.LENGTH_SHORT)
        on_dismiss()
    }

    AlertDialog(
        onDismissRequest = on_dismiss,
        containerColor = colors.dialog_bg,
        title = {
            Text(
                text = "软件源镜像",
                color = colors.dialog_text,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(modifier = Modifier.heightIn(max = 400.dp)) {
                Column(
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .verticalScroll(rememberScrollState())
                ) {
                    for (mirror in apk_mirror_store.builtin_mirrors) {
                        mirror_source_row(
                            name = mirror.name,
                            url = mirror.host,
                            selected = selected_key == mirror.name,
                            latency_ms = latencies[mirror.name],
                            is_fastest = fastest_name == mirror.name,
                            on_select = { selected_key = mirror.name }
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                    mirror_source_row(
                        name = custom_key,
                        url = custom_text.ifBlank { "手动输入镜像主机名" },
                        selected = selected_key == custom_key,
                        latency_ms = latencies[custom_key],
                        is_fastest = fastest_name == custom_key,
                        on_select = { selected_key = custom_key }
                    )
                    OutlinedTextField(
                        value = custom_text,
                        onValueChange = {
                            custom_text = it
                            if (it.isNotBlank()) selected_key = custom_key
                        },
                        placeholder = {
                            Text("mirrors.example.com", fontSize = 12.sp, color = colors.subtitle)
                        },
                        singleLine = true,
                        textStyle = androidx.compose.ui.text.TextStyle(fontSize = 12.sp, color = colors.dialog_text),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 48.dp, top = 2.dp)
                    )
                }
                Spacer(Modifier.height(8.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (testing) {
                        CircularProgressIndicator(
                            color = colors.title_highlight,
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(14.dp)
                        )
                        Text("测速中…", color = colors.subtitle, fontSize = 12.sp)
                    } else {
                        TextButton(onClick = { start_speed_test() }) {
                            Text("测速并推荐最快", color = colors.title_highlight, fontSize = 13.sp)
                        }
                    }
                }
                Text(
                    text = "应用后立即改写 rootfs 的 /etc/apk/repositories，终端与安装任务都走新源。",
                    color = colors.subtitle,
                    fontSize = 10.sp,
                    lineHeight = 14.sp
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { apply_selection() }) {
                Text("应用", color = colors.title_highlight)
            }
        },
        dismissButton = {
            TextButton(onClick = on_dismiss) {
                Text("取消", color = colors.subtitle)
            }
        }
    )
}

@Composable
private fun mirror_source_row(
    name: String,
    url: String,
    selected: Boolean,
    latency_ms: Long?,
    is_fastest: Boolean,
    on_select: () -> Unit
) {
    val colors = app_theme_provider.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable { on_select() }
            .padding(horizontal = 4.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(
            selected = selected,
            onClick = on_select,
            colors = RadioButtonDefaults.colors(
                selectedColor = colors.title_highlight,
                unselectedColor = colors.subtitle
            ),
            modifier = Modifier.size(36.dp)
        )
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = name,
                    color = colors.dialog_text,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold
                )
                if (is_fastest) {
                    Spacer(Modifier.width(6.dp))
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = colors.success.copy(alpha = 0.15f)
                    ) {
                        Text(
                            text = "最快",
                            color = colors.success,
                            fontSize = 9.sp,
                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                        )
                    }
                }
            }
            Text(
                text = url,
                color = colors.subtitle,
                fontSize = 11.sp,
                lineHeight = 14.sp
            )
        }
        mirror_latency_text(latency_ms, is_fastest)
    }
}

@Composable
private fun mirror_latency_text(latency_ms: Long?, tested: Boolean) {
    val colors = app_theme_provider.colors
    if (!tested) return
    val text = when {
        latency_ms == null -> "不可用"
        else -> "${latency_ms}ms"
    }
    Text(
        text = text,
        color = if (latency_ms == null) colors.danger else colors.success,
        fontSize = 11.sp,
        modifier = Modifier.padding(start = 6.dp)
    )
}
