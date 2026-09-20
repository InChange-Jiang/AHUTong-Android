package com.ahu.ahutong.architecture

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * P3 收尾判据「每个 feature 自带 fake 适配器的 JVM 测试」的守卫。
 *
 * 两条规则：
 * 1. 每个 :feature:* 模块必须有自己的测试源集与至少一个 *Test.kt；
 * 2. :app 的测试源集里不许出现住在 feature 模块里的 ViewModel 的测试。
 *
 * 第二条来自 P3 收尾时修掉的真实问题：repository-index / schedule / recharge 的 ViewModel 已经搬进模块，
 * 测试却留在 :app。那种状态下单独构建一个 feature 模块（gradlew :feature:x:testDebugUnitTest）根本跑不到
 * 自己 ViewModel 的行为，模块也就谈不上自带测试。
 *
 * 模块清单从 settings.gradle.kts 读，新增 feature 自动纳入，不需要维护第二份清单。
 *
 * 两条规则都按「抓得住」来写：feature 清单取 settings 里每一处 `:feature:<name>`（一行 include 多个也算），
 * 测试源集看 `src/test/java` 与 `src/test/kotlin` 两个根，:app 那边按 `<ViewModel>` 前缀匹配——
 * `ScheduleViewModelContractTest` 这种命名同样算在它头上。
 */
class FeatureTestOwnershipTest {

    @Test
    fun everyFeatureModuleCarriesItsOwnJvmTests() {
        val modulesWithoutTests = featureModules().filterNot { module ->
            testRoots(module).any { root ->
                root.walkTopDown().any { it.isFile && it.name.endsWith("Test.kt") }
            }
        }

        assertTrue(
            modulesWithoutTests.isEmpty(),
            "以下 feature 模块没有自己的 JVM 测试源集（测试跑回了 :app）：" +
                System.lineSeparator() +
                modulesWithoutTests.joinToString(System.lineSeparator()) +
                System.lineSeparator() +
                "feature 的测试应与被测代码同模块，这样模块能单独验证自己。"
        )
    }

    @Test
    fun appTestsDoNotOwnFeatureViewModels() {
        val featureViewModels = featureModules()
            .flatMap { module -> viewModelFileNames(module) }
            .map { name -> name.removeSuffix(".kt") }
            .toSet()
        val appTestRoot = File(repositoryRoot(), "app/src/test")
        val strays = appTestRoot.walkTopDown()
            .filter { it.isFile && it.name.endsWith("Test.kt") }
            .filter { file -> featureViewModels.any { vm -> file.name.startsWith(vm) } }
            .map { it.relativeTo(repositoryRoot()).invariantSeparatorsPath }
            .sorted()
            .toList()

        assertTrue(
            strays.isEmpty(),
            ":app 的测试源集里出现了 feature 模块的 ViewModel 测试，请把它们搬到对应 feature 模块：" +
                System.lineSeparator() +
                strays.joinToString(System.lineSeparator())
        )
    }

    private fun testRoots(module: String): List<File> =
        listOf("src/test/java", "src/test/kotlin")
            .map { sourceSet -> File(repositoryRoot(), "feature/$module/$sourceSet") }
            .filter { it.isDirectory }

    private fun viewModelFileNames(module: String): List<String> {
        val mainRoot = File(repositoryRoot(), "feature/$module/src/main/java")
        return mainRoot.walkTopDown()
            .filter { it.isFile && it.name.endsWith("ViewModel.kt") }
            .map { it.name }
            .toList()
    }

    private fun featureModules(): List<String> {
        val settings = File(repositoryRoot(), "settings.gradle.kts").readText()
        return FEATURE_ENTRY.findAll(settings)
            .map { match -> match.groupValues[1] }
            .distinct()
            .sorted()
            .toList()
    }

    private fun repositoryRoot(): File {
        val userDirectory = requireNotNull(System.getProperty("user.dir"))
        return generateSequence(File(userDirectory)) { it.parentFile }
            .first { File(it, "app/src/test/java").isDirectory }
    }

    private companion object {
        /** settings 里任何一处 :feature: 名字，含一行 include 多个模块的写法。 */
        val FEATURE_ENTRY = Regex(":feature:([A-Za-z0-9_.-]+)")
    }
}
