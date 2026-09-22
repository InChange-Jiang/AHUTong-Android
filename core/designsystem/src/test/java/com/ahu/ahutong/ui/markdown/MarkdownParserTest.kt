package com.ahu.ahutong.ui.markdown

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/** 解析器契约测试：钉住隐私政策用到的语法子集行为。 */
class MarkdownParserTest {

    @Test
    fun `heading levels one to four`() {
        val blocks = MarkdownParser.parse("# 一\n\n## 二\n\n### 三\n\n#### 四")
        assertEquals(4, blocks.size)
        assertEquals(1, (blocks[0] as MarkdownBlock.Heading).level)
        assertEquals("二", (blocks[1] as MarkdownBlock.Heading).text)
        assertEquals(4, (blocks[3] as MarkdownBlock.Heading).level)
    }

    @Test
    fun `table splits header divider and rows`() {
        val md = "| 功能 | 数据 |\n|---|---|\n| 课表 | 学号 |\n| 成绩 | 分数 |"
        val table = MarkdownParser.parse(md).single()
        assertIs<MarkdownBlock.Table>(table)
        assertEquals(listOf("功能", "数据"), table.header)
        assertEquals(2, table.rows.size)
        assertEquals(listOf("成绩", "分数"), table.rows[1])
    }

    @Test
    fun `inline bold and link in order`() {
        val runs = MarkdownParser.parseInline("前面 **加粗** 中间 [链接](https://a.b) 后面")
        assertEquals(5, runs.size)
        assertIs<InlineRun.Text>(runs[0])
        assertEquals("加粗", (runs[1] as InlineRun.Bold).text)
        assertEquals("https://a.b", (runs[3] as InlineRun.Link).url)
    }

    @Test
    fun `bullet list groups consecutive lines and blank line breaks block`() {
        val md = "- 甲\n- 乙\n\n- 丙"
        val blocks = MarkdownParser.parse(md)
        assertEquals(2, blocks.size)
        assertEquals(2, (blocks[0] as MarkdownBlock.BulletList).items.size)
        assertEquals(1, (blocks[1] as MarkdownBlock.BulletList).items.size)
    }

    @Test
    fun `ordered list groups numbered lines`() {
        val blocks = MarkdownParser.parse("1. 一\n2. 二\n3. 三")
        val list = blocks.single()
        assertIs<MarkdownBlock.OrderedList>(list)
        assertEquals(3, list.items.size)
    }

    @Test
    fun `defense empty input and unpaired markers`() {
        assertTrue(MarkdownParser.parse("").isEmpty())
        val runs = MarkdownParser.parseInline("不成对的 **粗体")
        assertEquals("不成对的 **粗体", (runs.single() as InlineRun.Text).text)
    }

    @Test
    fun `real file regression 阅读指引与表1片段`() {
        val fixture = """
            # 安大通隐私政策

            ## 阅读指引

            - **重点内容**：校园账号与业务数据。
            - 目录：引言 → 附则

            | 业务功能 | 收集的个人信息 |
            |---|---|
            | 课程表 | 教务账号、课程数据 |
        """.trimIndent()
        val blocks = MarkdownParser.parse(fixture)
        assertEquals(
            listOf("Heading", "Heading", "BulletList", "Table"),
            blocks.map { it::class.simpleName }
        )
        assertEquals(2, (blocks[2] as MarkdownBlock.BulletList).items.size)
        assertEquals(1, (blocks[3] as MarkdownBlock.Table).rows.size)
    }
}
