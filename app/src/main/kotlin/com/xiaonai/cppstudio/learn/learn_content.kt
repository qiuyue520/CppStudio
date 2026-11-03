package com.xiaonai.cppstudio.learn

/**
 * 学习课程内容模型（移植自 CodeAssist LearnContent 的 authoring DSL，C/C++ 版）。
 *
 * 约定：交互练习的 starter_code / solution 都是完整可编译的单文件程序
 * （一个 .cpp 文件 + main 函数），判题器把它写入隐藏 scratch 目录后用
 * `g++ -std=c++17` 编译运行。
 * 答案与判题规则（exercise_check）只留在本模块，不进 UI。
 */

data class learn_track(
    val id: String,
    val title: String,
    val subtitle: String,
    /** 主题色（ARGB Long），课程卡片色条用。 */
    val accent_color: Long,
    /** 分组标题，如 "C/C++ 语言" / "IDE 使用"。 */
    val category: String,
    val lessons: List<learn_lesson>
)

data class learn_lesson(
    val id: String,
    val title: String,
    val summary: String,
    val est_minutes: Int = 5,
    val steps: List<learn_step>
)

sealed interface learn_step {
    val id: String
    val title: String

    /** 概念讲解：只读内容块。 */
    data class concept(
        override val id: String,
        override val title: String,
        val blocks: List<learn_block>
    ) : learn_step

    /** 交互练习：内嵌编辑器 + 编译运行判题。 */
    data class interactive(
        override val id: String,
        override val title: String,
        val blocks: List<learn_block>,
        val starter_code: String,
        val hints: List<String> = emptyList(),
        val solution: String,
        val check: exercise_check
    ) : learn_step

    /** 测验：单选。 */
    data class quiz(
        override val id: String,
        override val title: String,
        val prompt: String,
        val options: List<String>,
        val correct_index: Int,
        val explanation: String = ""
    ) : learn_step
}

/**
 * 交互练习判题规则（移植自 CodeAssist ExerciseCheck）：
 * - [expected_output] 归一化后与程序 stdout 全量比对；
 * - [must_contain] 每个片段都必须出现在输出里；
 * - 两者都空时，程序正常退出（exit 0）即通过；
 * - [require_source] 反硬编码：剥掉注释/字符串内容并去空白后，
 *   源码必须包含这些构造（防止直接打印预期答案骗过判题）。
 */
data class exercise_check(
    val expected_output: String? = null,
    val must_contain: List<String> = emptyList(),
    val require_source: List<String> = emptyList(),
    val case_sensitive: Boolean = true
)

/** 内容块：讲解文本（内联 **粗体** / `代码`）/ 只读代码 / 提示框。 */
sealed interface learn_block {
    data class text(val md: String) : learn_block
    data class code(val src: String) : learn_block
    /** kind: tip / note / warn。 */
    data class callout(val kind: String, val text: String) : learn_block
}

/**
 * 统一整理课程里的代码块：去掉公共缩进与首尾空行。
 *
 * 历史版本还会把源码里的字面量转义序列还原成换行，那会让 C/C++ 代码里的
 * `"\n"` 被提前换行破坏，现在只做缩进归一化。
 */
internal fun normalize_lesson_code(src: String): String = src.trimIndent().trim()

object learn_content {

    /** 基础：程序结构、类型、控制流、函数、指针与引用。 */
    val accent_basic = 0xFF3B82F6L

    /** 进阶：RAII、模板、STL、异常与现代 C++。 */
    val accent_advanced = 0xFF00A8A0L

    /** 并发：std::thread、mutex、future。 */
    val accent_conc = 0xFFE0533DL

    val tracks: List<learn_track> =
        cpp_basics_tracks() + cpp_advanced_tracks() + cpp_concurrency_tracks() +
            cppstudio_learn_tracks() + ide_learn_tracks() + practical_learn_tracks()

    fun find_lesson(lesson_id: String): Pair<learn_track, learn_lesson>? {
        for (track in tracks) for (lesson in track.lessons) {
            if (lesson.id == lesson_id) return track to lesson
        }
        return null
    }
}

// ---------------------------------------------------------------------------
// 课程内容里复用的小工具（各内容文件里的同名 helper 都是各自的 private 版本，
// 这里放的是需要跨文件共享的那几个）。
// ---------------------------------------------------------------------------

/** 讲解文本：内联 **粗体** / `代码`。 */
internal fun lesson_text(md: String) = learn_block.text(md.trimIndent())

/** 只读代码块。 */
internal fun lesson_code(src: String) = learn_block.code(normalize_lesson_code(src))

internal fun lesson_tip(t: String) = learn_block.callout("tip", t)
internal fun lesson_note(t: String) = learn_block.callout("note", t)
internal fun lesson_warn(t: String) = learn_block.callout("warn", t)
