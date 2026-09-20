package com.ahu.ahutong.data.crawler

import com.ahu.ahutong.data.crawler.api.adwmh.createAdwmhApi
import com.ahu.ahutong.data.crawler.api.jwxt.createJwxtApi
import com.ahu.ahutong.data.crawler.model.jwxt.GradeResponse
import com.ahu.ahutong.data.model.Grade
import com.google.gson.Gson
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 成绩映射的契约测试（补齐 P2.2 里 SDK 网关的覆盖缺口）。
 *
 * 此前只有爬虫侧有契约测试，SDK 侧因为需要 native 库与 loopback 服务而完全没被覆盖。
 * 实际上两条路径消费的是**同一份 [GradeResponse] 结构**：爬虫侧由
 * `/student/for-std/grade/sheet/info/{id}` 直接返回，原生侧由本地服务的 JSON 反序列化得到。
 * 也就是说"同一份上游数据 → 同一份领域成绩"完全可以在 JVM 上钉住，
 * 需要设备才能验证的只剩"本地服务的传输与鉴权"。
 *
 * 已知差异（本次只记录、不修改，因为改动属于行为变更）：
 *
 * - **学期顺序**：爬虫侧按上游返回顺序建表，原生侧以学期名做 HashMap 键，顺序不保证一致。
 * - **同名学期**：原生侧以学期名为键，同名学期会互相覆盖（只留后一个）；爬虫侧两份都保留。
 *   若校方把同一学期的不同修读类型拆到两个 semesterId 下，原生侧会静默少一学期。
 */
class GradeMapperContractTest {

    private lateinit var server: MockWebServer

    @BeforeTest
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @AfterTest
    fun tearDown() {
        server.shutdown()
    }

    private fun crawlerGateway(): CrawlerDataSource {
        val client = OkHttpClient()
        val baseUrl = server.url("/").toString()
        return CrawlerDataSource(
            jwxt = createJwxtApi(client, baseUrl),
            adwmh = createAdwmhApi(client, baseUrl)
        )
    }

    private fun nativeGrade(json: String): Grade =
        GradeMapper.fromNativeResponse(Gson().fromJson(json, GradeResponse::class.java))

    private fun enqueueJson(body: String) {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json; charset=utf-8")
                .setBody(body)
        )
    }

    @Test
    fun `both gateways derive the same grade from the same payload`() = runBlocking {
        enqueueJson(GRADE_JSON)

        val crawlerGrade = assertNotNull(crawlerGateway().buildGradeForId("2021"))
        val nativeGrade = nativeGrade(GRADE_JSON)

        assertSameGrade(crawlerGrade, nativeGrade)
        // 同一套断言跑两遍：任何一侧改了公式，两侧都会红。
        assertPinnedGrade(crawlerGrade)
        assertPinnedGrade(nativeGrade)
    }

    @Test
    fun `a renamed semester is skipped instead of mis-parsed`() = runBlocking {
        enqueueJson(UNPARSEABLE_TERM_JSON)

        // 爬虫侧：该学期构建不出学年与学期，整个档案视为无数据。
        assertNull(crawlerGateway().buildGradeForId("2021"))

        // 原生侧：返回显式空成绩，而不是把 "2024" 猜成学年或直接崩。
        val nativeGrade = nativeGrade(UNPARSEABLE_TERM_JSON)
        assertTrue(nativeGrade.termGradeList.isEmpty())
        assertEquals("0.0", nativeGrade.totalCredit)
        assertEquals("0.0", nativeGrade.totalGradePoint)
        assertEquals("0.0", nativeGrade.totalGradePointAverage)
    }

    @Test
    fun `missing course fields fall back to zero instead of failing`() {
        val grade = nativeGrade(SPARSE_COURSE_JSON)

        val term = grade.termGradeList.single()
        val course = term.gradeList.single()
        assertEquals("", course.course)
        assertEquals("0.0", course.credit)
        assertEquals("0.0", course.gradePoint)
        assertEquals("", course.grade)
        assertEquals("", course.courseNum)
        assertEquals(0, course.semesterId)
        assertEquals("0.0", term.termTotalCredit)
        assertEquals("0.0", term.termGradePointAverage)
        assertEquals("0.0", grade.totalGradePointAverage)
    }

    @Test
    fun `a payload without grades yields the zero grade`() {
        val grade = nativeGrade("{}")

        assertTrue(grade.termGradeList.isEmpty())
        assertEquals("0.0", grade.totalCredit)
        assertEquals("0.0", grade.totalGradePoint)
        assertEquals("0.0", grade.totalGradePointAverage)
    }

    /** 名字相同的两个学期：原生侧只留后一个，爬虫侧两份都保留（见类注释的"已知差异"）。 */
    @Test
    fun `same-named semesters collapse on the native path but not on the crawler`() = runBlocking {
        enqueueJson(DUPLICATE_NAME_JSON)

        val crawlerTerms = assertNotNull(crawlerGateway().buildGradeForId("2021")).termGradeList
        assertEquals(2, crawlerTerms.size)

        val nativeTerms = nativeGrade(DUPLICATE_NAME_JSON).termGradeList
        assertEquals(1, nativeTerms.size)
        assertEquals(listOf("辅修课程"), nativeTerms.single().gradeList.map { it.course })
    }

    private fun assertSameGrade(crawler: Grade, native: Grade) {
        assertEquals(crawler.totalCredit, native.totalCredit)
        assertEquals(crawler.totalGradePoint, native.totalGradePoint)
        assertEquals(crawler.totalGradePointAverage, native.totalGradePointAverage)
        assertEquals(termSummaries(crawler), termSummaries(native))
    }

    private fun termSummaries(grade: Grade): List<String> =
        grade.termGradeList
            .map { term ->
                term.schoolYear + "/" + term.term +
                    " credit=" + term.termTotalCredit +
                    " point=" + term.termGradePoint +
                    " avg=" + term.termGradePointAverage +
                    " courses=" + term.gradeList
                        .map { it.course + ":" + it.credit + ":" + it.gradePoint + ":" + it.grade }
            }
            .sorted()

    private fun assertPinnedGrade(grade: Grade) {
        assertEquals("8.0", grade.totalCredit)
        assertEquals("28.0", grade.totalGradePoint)
        assertEquals("3.50", grade.totalGradePointAverage)
        assertEquals(2, grade.termGradeList.size)

        val firstTerm = grade.termGradeList.single { it.term == "1" }
        assertEquals("2024-2025", firstTerm.schoolYear)
        assertEquals("5.0", firstTerm.termTotalCredit)
        assertEquals("180.0", firstTerm.termGradePoint)
        assertEquals("3.80", firstTerm.termGradePointAverage)
        assertEquals(2, firstTerm.gradeList.size)

        val firstCourse = firstTerm.gradeList.single { it.course == "高等数学" }
        assertEquals("4.0", firstCourse.credit)
        assertEquals("4.0", firstCourse.gradePoint)
        assertEquals("95", firstCourse.grade)
        assertEquals("必修", firstCourse.courseNature)
        assertEquals("MATH1001", firstCourse.courseNum)
        assertEquals(101, firstCourse.semesterId)

        val secondTerm = grade.termGradeList.single { it.term == "2" }
        assertEquals("2024-2025", secondTerm.schoolYear)
        assertEquals("3.0", secondTerm.termTotalCredit)
        assertEquals("80.0", secondTerm.termGradePoint)
        assertEquals("3.00", secondTerm.termGradePointAverage)
    }

    private companion object {
        val GRADE_JSON = """
            {
              "semesterId2studentGrades": {
                "101": [
                  {
                    "courseCode": "MATH1001",
                    "courseName": "高等数学",
                    "courseType": "必修",
                    "credits": 4.0,
                    "gaGrade": "95",
                    "gp": 4.0,
                    "gradeDetail": "",
                    "semesterId": 101,
                    "semesterName": "2024-2025-1"
                  },
                  {
                    "courseCode": "ENG1001",
                    "courseName": "大学英语",
                    "courseType": "必修",
                    "credits": 1.0,
                    "gaGrade": "85",
                    "gp": 3.0,
                    "gradeDetail": "",
                    "semesterId": 101,
                    "semesterName": "2024-2025-1"
                  }
                ],
                "102": [
                  {
                    "courseCode": "PHY1001",
                    "courseName": "大学物理",
                    "courseType": "必修",
                    "credits": 3.0,
                    "gaGrade": "80",
                    "gp": 3.0,
                    "gradeDetail": "",
                    "semesterId": 102,
                    "semesterName": "2024-2025-2"
                  }
                ]
              }
            }
        """.trimIndent()

        val UNPARSEABLE_TERM_JSON = """
            {
              "semesterId2studentGrades": {
                "301": [
                  {
                    "courseCode": "MATH1001",
                    "courseName": "高等数学",
                    "credits": 4.0,
                    "gaGrade": "95",
                    "gp": 4.0,
                    "semesterId": 301,
                    "semesterName": "2024"
                  }
                ]
              }
            }
        """.trimIndent()

        val SPARSE_COURSE_JSON = """
            {
              "semesterId2studentGrades": {
                "401": [
                  { "semesterName": "2024-2025-1" }
                ]
              }
            }
        """.trimIndent()

        val DUPLICATE_NAME_JSON = """
            {
              "semesterId2studentGrades": {
                "501": [
                  {
                    "courseCode": "MAJOR",
                    "courseName": "主修课程",
                    "courseType": "必修",
                    "credits": 2.0,
                    "gaGrade": "90",
                    "gp": 4.0,
                    "semesterId": 501,
                    "semesterName": "2024-2025-1"
                  }
                ],
                "502": [
                  {
                    "courseCode": "MINOR",
                    "courseName": "辅修课程",
                    "courseType": "选修",
                    "credits": 1.0,
                    "gaGrade": "70",
                    "gp": 2.0,
                    "semesterId": 502,
                    "semesterName": "2024-2025-1"
                  }
                ]
              }
            }
        """.trimIndent()
    }
}
