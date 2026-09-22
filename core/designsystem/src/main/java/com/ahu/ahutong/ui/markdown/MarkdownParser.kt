package com.ahu.ahutong.ui.markdown

/**
 * 轻量行式 MD 解析器（纯 Kotlin，可 JVM 单测）。
 * 只支持隐私政策用到的语法子集：井号标题、段落、短横/星号无序列表、数字有序列表、
 * 管道表格、行内 **粗体** 与 [文字](url)。不支持的元素按普通文本防御处理。
 */
object MarkdownParser {

    private val orderedItem = Regex("^\\d+\\.\\s+")
    private val tableDivider = Regex("^\\|?[\\s:|-]+\\|?$")

    fun parse(markdown: String): List<MarkdownBlock> {
        val lines = markdown.replace("\r\n", "\n").split("\n")
        val blocks = mutableListOf<MarkdownBlock>()
        var i = 0
        while (i < lines.size) {
            val line = lines[i]
            when {
                line.isBlank() -> i++
                line.trimStart().startsWith("#") -> {
                    val m = Regex("^(#{1,4})\\s+(.*)$").find(line.trim())
                    if (m != null) {
                        blocks += MarkdownBlock.Heading(m.groupValues[1].length, m.groupValues[2].trim())
                    } else {
                        blocks += MarkdownBlock.Paragraph(parseInline(line.trim()))
                    }
                    i++
                }
                line.trimStart().startsWith("|") -> {
                    // 表格：连续管道行；第二行是分隔行（|---|）跳过
                    val tableLines = mutableListOf<String>()
                    while (i < lines.size && lines[i].trimStart().startsWith("|")) {
                        tableLines += lines[i].trim()
                        i++
                    }
                    blocks += parseTable(tableLines)
                }
                line.trimStart().startsWith("- ") || line.trimStart().startsWith("* ") -> {
                    val items = mutableListOf<List<InlineRun>>()
                    while (i < lines.size &&
                        (lines[i].trimStart().startsWith("- ") || lines[i].trimStart().startsWith("* "))
                    ) {
                        items += parseInline(lines[i].trim().substring(2).trim())
                        i++
                    }
                    blocks += MarkdownBlock.BulletList(items)
                }
                orderedItem.containsMatchIn(line.trimStart().take(6).let { "$it " }) &&
                    orderedItem.find(line.trimStart())?.range?.first == 0 -> {
                    val items = mutableListOf<List<InlineRun>>()
                    while (i < lines.size && orderedItem.find(lines[i].trimStart())?.range?.first == 0) {
                        items += parseInline(orderedItem.replace(lines[i].trim(), ""))
                        i++
                    }
                    blocks += MarkdownBlock.OrderedList(items)
                }
                else -> {
                    // 段落：连续非空行合并（中文政策无硬换行语义）
                    val sb = StringBuilder()
                    while (i < lines.size && lines[i].isNotBlank() &&
                        !lines[i].trimStart().startsWith("#") &&
                        !lines[i].trimStart().startsWith("|") &&
                        !lines[i].trimStart().startsWith("- ") &&
                        !lines[i].trimStart().startsWith("* ") &&
                        orderedItem.find(lines[i].trimStart())?.range?.first != 0
                    ) {
                        if (sb.isNotEmpty()) sb.append(' ')
                        sb.append(lines[i].trim())
                        i++
                    }
                    blocks += MarkdownBlock.Paragraph(parseInline(sb.toString()))
                }
            }
        }
        return blocks
    }

    private fun parseTable(tableLines: List<String>): MarkdownBlock {
        fun cells(line: String): List<String> =
            line.removePrefix("|").removeSuffix("|").split("|").map { it.trim() }
        val header = cells(tableLines.first())
        val rows = tableLines.drop(1)
            .filterNot { tableDivider.matches(it) }
            .map(::cells)
        return MarkdownBlock.Table(header, rows)
    }

    /** 行内解析：顺序扫描 **粗体** 与 [文字](url)，不支持嵌套；不成对的标记按原文输出。 */
    fun parseInline(text: String): List<InlineRun> {
        val runs = mutableListOf<InlineRun>()
        var i = 0
        val buf = StringBuilder()
        fun flush() {
            if (buf.isNotEmpty()) {
                runs += InlineRun.Text(buf.toString())
                buf.clear()
            }
        }
        while (i < text.length) {
            when {
                text.startsWith("**", i) -> {
                    val end = text.indexOf("**", i + 2)
                    if (end > i + 2) {
                        flush()
                        runs += InlineRun.Bold(text.substring(i + 2, end))
                        i = end + 2
                    } else {
                        buf.append('*')
                        i++
                    }
                }
                text[i] == '[' -> {
                    val closeBracket = text.indexOf(']', i + 1)
                    val openParen = if (closeBracket > 0) closeBracket + 1 else -1
                    if (closeBracket > 0 && openParen < text.length &&
                        openParen > 0 && text.getOrNull(openParen) == '('
                    ) {
                        val closeParen = text.indexOf(')', openParen + 1)
                        if (closeParen > openParen) {
                            flush()
                            runs += InlineRun.Link(
                                text.substring(i + 1, closeBracket),
                                text.substring(openParen + 1, closeParen)
                            )
                            i = closeParen + 1
                        } else {
                            buf.append(text[i]); i++
                        }
                    } else {
                        buf.append(text[i]); i++
                    }
                }
                else -> {
                    buf.append(text[i])
                    i++
                }
            }
        }
        flush()
        return runs
    }
}
