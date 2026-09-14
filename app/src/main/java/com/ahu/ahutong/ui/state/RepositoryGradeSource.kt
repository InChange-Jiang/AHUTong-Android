package com.ahu.ahutong.ui.state

import com.ahu.ahutong.core.common.AhuResult
import com.ahu.ahutong.data.AHURepository
import com.ahu.ahutong.data.dao.AHUCache
import com.ahu.ahutong.data.mock.MockScenarioController
import com.ahu.ahutong.data.grade.GradeSource
import com.ahu.ahutong.data.model.GpaRankInfo
import com.ahu.ahutong.data.model.Grade
import com.ahu.ahutong.data.model.GradeStudentProfile
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * [GradeSource] 的生产实现：把请求转给 AHURepository（拉取）与 AHUCache（缓存）。
 *
 * 与课表的两个适配器同一模式：适配器留在 :app，因为仓库要用协议客户端、缓存要用分箱键；
 * 成绩的 ViewModel 因此只需要认识端口。
 */
@Singleton
class RepositoryGradeSource @Inject constructor() : GradeSource {

    override fun usesMockData(): Boolean = AHUCache.getMockData()

    override fun mockRefreshRevisions(): Flow<Long> = MockScenarioController.refreshRevisions()

    override fun cachedProfiles(): List<GradeStudentProfile> = AHUCache.getGradeStudentProfiles()

    override fun cachedPerProfileGrades(): Map<String, Grade?> = AHUCache.getPerProfileGrades()

    override fun cachedGpaRank(studentId: String): GpaRankInfo? = AHUCache.getGpaRankInfo(studentId)

    override suspend fun fetchGrade(isRefresh: Boolean): AhuResult<Grade> =
        AHURepository.getGrade(isRefresh = isRefresh)

    override suspend fun fetchProfiles(): List<GradeStudentProfile> =
        AHURepository.getGradeStudentProfiles()

    override suspend fun fetchGpaRank(studentId: String): AhuResult<GpaRankInfo> =
        AHURepository.getGpaRankInfo(studentId)

    override fun saveGpaRank(studentId: String, rank: GpaRankInfo) =
        AHUCache.saveGpaRankInfo(studentId, rank)
}
