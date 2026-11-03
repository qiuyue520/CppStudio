package com.jmwl.cppstudio.lsp.clangd

import io.github.rosemoe.sora.lsp.editor.hover.formatMarkedStringEither
import io.github.rosemoe.sora.lsp.editor.hover.formatMarkupContent
import io.github.rosemoe.sora.lsp.utils.asLspPosition
import io.github.rosemoe.sora.lsp.utils.createTextDocumentIdentifier
import io.github.rosemoe.sora.widget.CodeEditor
import kotlinx.coroutines.future.await
import kotlinx.coroutines.withTimeoutOrNull
import org.eclipse.lsp4j.Hover
import org.eclipse.lsp4j.HoverParams
import java.io.File

/**
 * hover 详情结果（剥离 lsp4j 类型，避免 app 层依赖 lsp4j）。
 *
 * @param title 符号名（从签名中提取，取不到为 null）
 * @param signature 完整签名（markdown 首个代码块内容，无代码块时为 null）
 * @param documentation 文档说明（签名代码块之外的 markdown 文本）
 */
data class clangd_hover_detail(
    val title: String?,
    val signature: String?,
    val documentation: String?
)

/**
 * 请求 clangd 的 textDocument/hover，返回指定位置的符号详情。
 *
 * 复用 hover 事件管线的 markdown 提取方式（DefaultHoverLayout 同款），
 * 结果经 documentationTranslator 翻译（与 HoverEvent 行为一致）。
 * 超时（8s）或无内容返回 null。
 */
suspend fun clangd_lsp_project.request_hover(
    file: File,
    editor: CodeEditor,
    cursor_line: Int,
    cursor_column: Int
): clangd_hover_detail? = withTimeoutOrNull(8000L) {
    val lsp_editor = runCatching {
        project.getEditor(file.absolutePath)
    }.getOrNull() ?: return@withTimeoutOrNull null
    val request_manager = lsp_editor.requestManager ?: return@withTimeoutOrNull null
    val position = editor.text.getIndexer()
        .getCharPosition(cursor_line, cursor_column)
        .asLspPosition()
    val params = HoverParams(
        lsp_editor.uri.createTextDocumentIdentifier(),
        position
    )
    val hover = request_manager.hover(params)?.await() ?: return@withTimeoutOrNull null
    // 与 HoverEvent 一致：开启文档翻译时先翻译再展示，翻译失败退回原文
    val translated = runCatching {
        lsp_editor.project.documentationTranslator.translateHover(hover)
    }.getOrNull() ?: hover
    parse_hover_detail(translated)
}

/** 当前文件是否已有已连接的 LspEditor（用于工具栏「详情」按钮可见性判断）。 */
fun clangd_lsp_project.is_hover_available(file: File): Boolean {
    val lsp_editor = runCatching { project.getEditor(file.absolutePath) }.getOrNull() ?: return false
    return lsp_editor.isConnected
}

/**
 * 把 lsp4j Hover 转成 [clangd_hover_detail]：
 * 首个 ``` 代码块 → 签名；其余 markdown → 文档；签名里的 Go 声明前缀 → 符号名。
 */
internal fun parse_hover_detail(hover: Hover): clangd_hover_detail? {
    val contents = hover.contents ?: return null
    val markdown = if (contents.isLeft) {
        contents.left.orEmpty().joinToString("\n\n") { either ->
            formatMarkedStringEither(either) ?: ""
        }
    } else {
        formatMarkupContent(contents.right) ?: ""
    }.trim()
    if (markdown.isEmpty()) return null

    val fence = Regex("```[a-zA-Z0-9_+-]*[ \\t]*\\n(.*?)```", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE))
    val match = fence.find(markdown)
    val signature = match?.groupValues?.get(1)?.trim()?.takeIf { it.isNotEmpty() }
    val documentation = if (match != null) {
        (markdown.substring(0, match.range.first) + markdown.substring(match.range.last + 1)).trim()
    } else {
        markdown
    }.takeIf { it.isNotEmpty() }

    if (signature == null && documentation == null) return null

    return clangd_hover_detail(
        title = extract_symbol_name(signature ?: markdown),
        signature = signature,
        documentation = documentation
    )
}

/** 从 C/C++ 签名提取符号名：`int sum(int a, int b)` → sum，`class Foo` → Foo，`std::string name` → name。 */
private fun extract_symbol_name(signature: String): String? {
    // 1) 声明关键字后面的名字：class/struct/union/enum/namespace/typedef/using
    Regex("\\b(?:class|struct|union|enum(?:\\s+class)?|namespace|typedef|using)\\s+([A-Za-z_][A-Za-z0-9_]*)")
        .find(signature)?.groupValues?.get(1)?.let { return it }

    // 2) 函数签名：取第一个 `(` 前面最后一个标识符（跳过 std:: 限定、指针/引用）
    val head = signature.substringBefore('(').ifBlank { signature }
    Regex("([A-Za-z_][A-Za-z0-9_]*)\\s*$")
        .find(head.trim())
        ?.groupValues?.get(1)
        ?.let { return it }

    // 3) 兜底：整段里第一个像标识符的片段
    return Regex("[A-Za-z_][A-Za-z0-9_]*")
        .find(signature)
        ?.value
}
