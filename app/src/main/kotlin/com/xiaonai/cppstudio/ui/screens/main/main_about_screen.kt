package com.xiaonai.cppstudio.ui.screens.main

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.SystemUpdateAlt
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xiaonai.cppstudio.BuildConfig
import com.xiaonai.cppstudio.R
import com.xiaonai.cppstudio.ui.components.sub_page_top_bar
import com.xiaonai.cppstudio.ui.theme.app_colors
import com.xiaonai.cppstudio.ui.theme.app_theme_provider

@Composable
fun main_about_screen(
    on_back: () -> Unit
) {
    val colors = app_theme_provider.colors
    val version_text = "v${BuildConfig.VERSION_NAME}-arm64-v8a"

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        sub_page_top_bar("关于", on_back)

        Column(modifier = Modifier.padding(horizontal = 30.dp)) {
            Spacer(modifier = Modifier.height(18.dp))

            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Surface(
                    modifier = Modifier.size(88.dp),
                    shape = RoundedCornerShape(20.dp),
                    color = colors.card_bg
                ) {
                    Image(
                        painter = painterResource(R.drawable.ic_cppstudio_avatar),
                        contentDescription = "CppStudio",
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(20.dp))
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                Text(
                    text = "CppStudio",
                    fontSize = 28.sp,
                    lineHeight = 32.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.card_text_title,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(10.dp))

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = colors.card_bg
                ) {
                    Text(
                        text = version_text,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = colors.title_highlight,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp)
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "by qiuyue520",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.title_highlight
                )
            }

            Spacer(modifier = Modifier.height(28.dp))

            about_section_title("链接", colors)
            // 自己的仓库地址在 cppstudio_backend_config.GITHUB_REPO 里配置；留空则不显示外链
            val repo = com.xiaonai.cppstudio.editor.config.cppstudio_backend_config.GITHUB_REPO.trim()
            val links = buildList {
                if (repo.isNotBlank()) {
                    add(
                        about_link_item(
                            Icons.Default.Code,
                            "源代码",
                            "github.com/$repo",
                            "https://github.com/$repo"
                        )
                    )
                    add(
                        about_link_item(
                            Icons.Default.BugReport,
                            "问题反馈",
                            "通过 GitHub Issues 提交问题",
                            "https://github.com/$repo/issues"
                        )
                    )
                    add(
                        about_link_item(
                            Icons.Default.SystemUpdateAlt,
                            "更新发布",
                            "GitHub Releases 下载最新版本",
                            "https://github.com/$repo/releases"
                        )
                    )
                }
                add(
                    about_link_item(
                        Icons.Default.Groups,
                        "开源许可",
                        "MIT License · 基于 XCode 与 GoStudio 改造",
                        copy_value = "MIT"
                    )
                )
            }
            about_link_group(
                colors = colors,
                rows = links
            )

            Spacer(modifier = Modifier.height(26.dp))

            about_section_title("这是什么", colors)
            about_paragraph(
                "CppStudio 是一个跑在 Android 手机 / 平板上的 C/C++ 开发环境：写代码、CMake 构建、clangd 补全与诊断、在终端里运行，全部在本地完成，不需要连电脑。\n" +
                    "随手写点 C/C++、刷题、验证算法，或者手边没有电脑时改几行代码，都能直接用。",
                colors
            )

            Spacer(modifier = Modifier.height(22.dp))

            about_section_title("技术栈", colors)
            about_paragraph(
                "编辑器：sora-editor + TextMate 语法高亮（C / C++ / CMake / JSON）\n" +
                    "语言服务：clangd，读 CMake 导出的 compile_commands.json\n" +
                    "工具链：proot + Alpine rootfs 内的 build-base / cmake / clang / gdb / git\n" +
                    "界面：Kotlin + Jetpack Compose，浅紫配色",
                colors
            )

            Spacer(modifier = Modifier.height(22.dp))

            about_section_title("致谢", colors)
            about_paragraph(
                "应用骨架来自 by-yitong 的 GoStudio（MIT），其编辑器与 proot 运行时源自 xiaochenzaine 的 XCode。\n" +
                    "本版本把语言层整体换成了 C/C++，并补上了 CMake 工程模型、库管理与 clangd 支持。\n" +
                    "本项目是个人二次开发版本，与上游作者没有隶属关系。",
                colors
            )

            Spacer(modifier = Modifier.height(38.dp))
        }
    }
}

/** 关于页正文段落。 */
@Composable
private fun about_paragraph(
    text: String,
    colors: app_colors
) {
    Text(
        text = text,
        fontSize = 12.sp,
        lineHeight = 19.sp,
        color = colors.card_text_subtitle,
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 2.dp)
    )
}

@Composable
private fun about_section_title(
    title: String,
    colors: app_colors
) {
    Text(
        text = title,
        fontSize = 13.sp,
        letterSpacing = 0.sp,
       fontWeight = FontWeight.Bold,
        color = colors.section_title,
       modifier = Modifier
           .fillMaxWidth()
           .padding(bottom = 12.dp),
        textAlign = TextAlign.Start
   )
}

private data class about_link_item(
    val icon: ImageVector,
    val title: String,
    val subtitle: String,
    val url: String? = null,
    val copy_value: String? = null
)

@Composable
private fun about_link_group(
    colors: app_colors,
    rows: List<about_link_item>
) {
    Column(
        modifier = Modifier
           .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
   ) {
       rows.forEachIndexed { index, row ->
           about_link_row(
                item = row,
                colors = colors,
                shape = about_group_item_shape(
                    is_top = index == 0,
                    is_bottom = index == rows.lastIndex
                )
            )
            if (index < rows.lastIndex) {
                Spacer(modifier = Modifier.height(1.dp))
            }
        }
    }
}

@Composable
private fun about_link_row(
    item: about_link_item,
    colors: app_colors,
    shape: RoundedCornerShape
) {
    val context = LocalContext.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
           .clip(shape)
            .background(colors.card_bg)
            .clickable {
                val url = item.url
                val copy_value = item.copy_value
                when {
                    url != null -> runCatching {
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                    }
                    copy_value != null -> {
                        val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE)
                            as android.content.ClipboardManager
                        clipboard.setPrimaryClip(android.content.ClipData.newPlainText(item.title, copy_value))
                        android.widget.Toast.makeText(
                            context,
                            "已复制：$copy_value",
                            android.widget.Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            }
            .heightIn(min = 54.dp)
            .padding(horizontal = 14.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier
                .size(30.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(colors.card_icon_bg.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = item.icon,
                contentDescription = item.title,
                tint = colors.card_icon_bg,
                modifier = Modifier.size(16.dp)
            )
        }

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(1.dp)
        ) {
            Text(
                text = item.title,
                fontSize = 14.sp,
                lineHeight = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = colors.card_text_title
            )
            Text(
                text = item.subtitle,
                fontSize = 10.5.sp,
                lineHeight = 11.sp,
                fontWeight = FontWeight.Light,
                color = colors.title_highlight
            )
        }

        Icon(
            imageVector = if (item.copy_value != null) {
                Icons.Default.ContentCopy
            } else {
                Icons.AutoMirrored.Filled.OpenInNew
            },
            contentDescription = null,
            tint = colors.card_text_subtitle.copy(alpha = 0.36f),
            modifier = Modifier.size(14.dp)
        )
    }
}

private fun about_group_item_shape(
    is_top: Boolean,
    is_bottom: Boolean
): RoundedCornerShape {
   return when {
        is_top && is_bottom -> RoundedCornerShape(12.dp)
        is_top -> RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp, bottomStart = 0.dp, bottomEnd = 0.dp)
        is_bottom -> RoundedCornerShape(topStart = 0.dp, topEnd = 0.dp, bottomStart = 12.dp, bottomEnd = 12.dp)
       else -> RoundedCornerShape(0.dp)
   }
}
