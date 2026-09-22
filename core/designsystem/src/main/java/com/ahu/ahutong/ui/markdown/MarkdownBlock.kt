package com.ahu.ahutong.ui.markdown

/** MD 块模型：隐私政策文件只用到这六类元素（标题/段落/无序/有序/表格/行内）。 */
sealed interface MarkdownBlock {
    data class Heading(val level: Int, val text: String) : MarkdownBlock
    data class Paragraph(val runs: List<InlineRun>) : MarkdownBlock
    data class BulletList(val items: List<List<InlineRun>>) : MarkdownBlock
    data class OrderedList(val items: List<List<InlineRun>>) : MarkdownBlock
    data class Table(val header: List<String>, val rows: List<List<String>>) : MarkdownBlock
}

sealed interface InlineRun {
    data class Text(val text: String) : InlineRun
    data class Bold(val text: String) : InlineRun
    data class Link(val text: String, val url: String) : InlineRun
}
