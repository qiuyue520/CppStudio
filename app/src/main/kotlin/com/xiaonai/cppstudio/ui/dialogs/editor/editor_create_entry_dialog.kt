package com.xiaonai.cppstudio.ui.dialogs.editor

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xiaonai.cppstudio.ui.theme.app_theme_provider

enum class editor_create_file_template(
    val id: String,
    val label: String,
    val placeholder: String,
    val fixed_name: String? = null,
    val suffix: String? = null
) {
    PLAIN("PLAIN", "普通", "文件名称"),
    C_SOURCE("C_SOURCE", "C 源文件", "名称（自动加 .c）", suffix = ".c"),
    C_HEADER("C_HEADER", "C 头文件", "名称（自动加 .h）", suffix = ".h"),
    CPP_SOURCE("CPP_SOURCE", "C++ 源文件", "名称（自动加 .cpp）", suffix = ".cpp"),
    CPP_HEADER("CPP_HEADER", "C++ 头文件", "名称（自动加 .hpp）", suffix = ".hpp"),
    CMAKE("CMAKE", "CMakeLists.txt", "CMakeLists.txt", fixed_name = "CMakeLists.txt"),
    MAKEFILE("MAKEFILE", "Makefile", "Makefile", fixed_name = "Makefile"),
    GITIGNORE("GITIGNORE", ".gitignore", ".gitignore", fixed_name = ".gitignore")
}

@Composable
fun editor_create_entry_dialog(
    is_folder: Boolean,
    on_confirm: (String, editor_create_file_template) -> Unit,
    on_dismiss: () -> Unit
) {
    val colors = app_theme_provider.colors
    var name by remember(is_folder) { mutableStateOf("") }
    var template by remember(is_folder) { mutableStateOf(editor_create_file_template.PLAIN) }
    val title = if (is_folder) "新建文件夹" else "新建文件"
    val placeholder = if (is_folder) "文件夹名称" else template.placeholder

    AlertDialog(
        onDismissRequest = on_dismiss,
        containerColor = colors.dialog_bg,
        shape = RoundedCornerShape(14.dp),
        title = {
            Text(
                text = title,
                color = colors.dialog_text,
                fontSize = 17.sp,
                fontWeight = FontWeight.Medium
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { value ->
                        if (template.fixed_name != null && value != template.fixed_name) {
                            template = editor_create_file_template.PLAIN
                        }
                        name = value
                    },
                    singleLine = true,
                    placeholder = { Text(placeholder, color = colors.dialog_input_hint) },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = colors.dialog_input_border,
                        unfocusedBorderColor = colors.dialog_input_hint.copy(alpha = 0.5f),
                        focusedTextColor = colors.dialog_input_text,
                        unfocusedTextColor = colors.dialog_input_text,
                        cursorColor = colors.dialog_input_border,
                        focusedContainerColor = colors.dialog_input_bg,
                        unfocusedContainerColor = colors.dialog_input_bg
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                if (!is_folder) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 32.dp)
                            .horizontalScroll(rememberScrollState())
                    ) {
                        editor_create_file_template.entries.forEach { candidate ->
                            FilterChip(
                                selected = template == candidate,
                                onClick = {
                                    val old_template = template
                                    template = candidate
                                    name = when {
                                        candidate.fixed_name != null -> candidate.fixed_name
                                        old_template.fixed_name != null -> ""
                                        else -> name
                                    }
                                },
                                label = { Text(candidate.label, fontSize = 11.sp) },
                                colors = FilterChipDefaults.filterChipColors(
                                    containerColor = colors.dialog_input_bg,
                                    labelColor = colors.dialog_input_text,
                                    selectedContainerColor = colors.dialog_clone_bg,
                                    selectedLabelColor = colors.dialog_clone_text
                                )
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = name.trim().isNotEmpty(),
                onClick = {
                    on_confirm(
                        editor_create_file_name(name.trim(), template),
                        if (is_folder) editor_create_file_template.PLAIN else template
                    )
                }
            ) {
                Text("创建", color = colors.dialog_clone_bg)
            }
        },
        dismissButton = {
            TextButton(onClick = on_dismiss) {
                Text("取消", color = colors.dialog_hint)
            }
        }
    )
}

private fun editor_create_file_name(name: String, template: editor_create_file_template): String {
    val fixed_name = template.fixed_name
    if (fixed_name != null) return fixed_name
    val suffix = template.suffix ?: return name
    return when {
        name.lowercase().endsWith(suffix) -> name
        suffix == "_test.go" && name.lowercase().endsWith(".go") ->
            name.removeSuffix(".go") + "_test.go"
        else -> name + suffix
    }
}
