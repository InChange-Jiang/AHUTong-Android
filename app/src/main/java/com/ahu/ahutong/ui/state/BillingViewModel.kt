package com.ahu.ahutong.ui.state

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ahu.ahutong.data.AHURepository
import com.ahu.ahutong.data.crawler.model.ycard.TurnoverCount
import com.ahu.ahutong.data.crawler.model.ycard.TurnoverRecord
import com.ahu.ahutong.core.common.AhuError
import com.ahu.ahutong.core.common.AhuResult
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.Calendar
import java.util.Locale
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * 账单页状态：本月收支汇总 + 流水分页（滚动加载更多 + 下拉/按钮刷新）。
 * 数据链路：YcardApi(401 单飞刷新) → DataSource → AHURepository（无 Rust fallback，流水接口仅 ycard 一条链）。
 */
@HiltViewModel
class BillingViewModel @Inject constructor() : ViewModel() {

    companion object {
        private const val PAGE_SIZE = 20
    }

    /** 月份筛选：本月（不传时间参数，服务端默认当月）/ 上月 / 近三月；null = 全部时间。 */
    enum class MonthFilter { THIS_MONTH, LAST_MONTH, LAST_3_MONTHS }

    sealed interface ListState {
        data object Loading : ListState
        data object Ready : ListState
        data class Error(val message: String) : ListState
    }

    /** 原始流水（未按金额过滤）。 */
    private val _records = MutableStateFlow<List<TurnoverRecord>>(emptyList())

    /** 金额区间筛选（分，null=不限），服务端无金额参数 → 本地过滤已加载记录。 */
    private val _amountRange = MutableStateFlow<Pair<Long?, Long?>>(null to null)
    val amountRange: StateFlow<Pair<Long?, Long?>> = _amountRange.asStateFlow()

    /** 月份筛选（null=全部时间）。 */
    private val _monthFilter = MutableStateFlow<MonthFilter?>(MonthFilter.THIS_MONTH)
    val monthFilter: StateFlow<MonthFilter?> = _monthFilter.asStateFlow()

    /** 类型筛选：true=支出 false=收入 null=全部。 */
    private val _typeFilter = MutableStateFlow<Boolean?>(null)
    val typeFilter: StateFlow<Boolean?> = _typeFilter.asStateFlow()

    /** 展示用流水 = 原始记录 ∩ 金额区间。 */
    val records: StateFlow<List<TurnoverRecord>> =
        combine(_records, _amountRange) { list, (min, max) ->
            list.filter { (min == null || it.tranamt >= min) && (max == null || it.tranamt <= max) }
        }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val _listState = MutableStateFlow<ListState>(ListState.Loading)
    val listState: StateFlow<ListState> = _listState.asStateFlow()

    private val _summary = MutableStateFlow<TurnoverCount?>(null)
    val summary: StateFlow<TurnoverCount?> = _summary.asStateFlow()

    /** 是否还有下一页 */
    private val _hasMore = MutableStateFlow(true)
    val hasMore: StateFlow<Boolean> = _hasMore.asStateFlow()

    private val _loadingMore = MutableStateFlow(false)
    val loadingMore: StateFlow<Boolean> = _loadingMore.asStateFlow()

    private var currentPage = 1
    private var totalPages = Int.MAX_VALUE

    /**
     * 查询时间范围（YYYY-MM-DD）。
     * THIS_MONTH 返回 null 区间（不传参数，服务端默认当月）；null=全部时间 → 2020-01-01 起。
     */
    private val queryRange: Pair<String?, String?>
        get() {
            val now = Calendar.getInstance(Locale.CHINA)
            fun fmt(cal: Calendar): String = "%04d-%02d-%02d".format(
                cal.get(Calendar.YEAR), cal.get(Calendar.MONTH) + 1,
                cal.get(Calendar.DAY_OF_MONTH)
            )
            fun monthEndOf(offset: Int): Calendar = Calendar.getInstance(Locale.CHINA).apply {
                add(Calendar.MONTH, offset)
                set(Calendar.DAY_OF_MONTH, getActualMaximum(Calendar.DAY_OF_MONTH))
            }
            return when (_monthFilter.value) {
                MonthFilter.THIS_MONTH -> null to null
                MonthFilter.LAST_MONTH -> {
                    val first = Calendar.getInstance(Locale.CHINA).apply {
                        add(Calendar.MONTH, -1)
                        set(Calendar.DAY_OF_MONTH, 1)
                    }
                    fmt(first) to fmt(monthEndOf(-1))
                }
                MonthFilter.LAST_3_MONTHS -> {
                    val first = Calendar.getInstance(Locale.CHINA).apply {
                        add(Calendar.MONTH, -2)
                        set(Calendar.DAY_OF_MONTH, 1)
                    }
                    fmt(first) to fmt(monthEndOf(0))
                }
                null -> "2020-01-01" to fmt(now)
            }
        }

    init {
        refresh()
    }

    private fun billingErrorMessage(error: AhuError): String = when (error) {
        is AhuError.Network -> "网络连接失败，请检查校园网"
        is AhuError.Timeout -> "请求超时，请稍后重试"
        is AhuError.Unauthorized -> error.message
        is AhuError.ProtocolChanged -> "账单服务接口已变更，请更新应用"
        is AhuError.Server -> error.message
        is AhuError.Unknown -> error.message
    }

    /** 应用筛选（月份/类型/金额区间，元输入已换算成分传入），重置分页重拉。 */
    fun applyFilters(
        month: MonthFilter?,
        expense: Boolean?,
        amountMinFen: Long?,
        amountMaxFen: Long?
    ) {
        _monthFilter.value = month
        _typeFilter.value = expense
        _amountRange.value = amountMinFen to amountMaxFen
        refresh()
    }

    fun refresh() {
        currentPage = 1
        totalPages = Int.MAX_VALUE
        _hasMore.value = true
        _listState.value = ListState.Loading
        _records.value = emptyList()
        loadSummary()
        loadPage(1)
    }

    fun loadNextPage() {
        if (_loadingMore.value || !_hasMore.value || _listState.value is ListState.Loading) return
        loadPage(currentPage)
    }

    private fun loadSummary() {
        viewModelScope.launch {
            val (from, to) = queryRange
            when (val result = AHURepository.getBillSummary(from ?: "2020-01-01", to ?: "2099-12-31")) {
                is AhuResult.Success -> _summary.value = result.value
                is AhuResult.Failure -> Unit // 汇总失败不阻塞列表
            }
        }
    }

    private fun loadPage(page: Int) {
        viewModelScope.launch {
            _loadingMore.value = true
            val (from, to) = queryRange
            when (val result = AHURepository.getBillPage(
                page = page,
                size = PAGE_SIZE,
                timeFrom = from,
                timeTo = to,
                type = _typeFilter.value?.let { if (it) 2 else 1 }
            )) {
                is AhuResult.Success -> {
                    val pageData = result.value
                    val newRecords = pageData.records.orEmpty()
                    // total 为 null = 当月无数据（服务端边界行为）
                    totalPages = pageData.pages ?: 1
                    val existing = _records.value.map { it.orderId }.toHashSet()
                    _records.value = _records.value + newRecords.filter { it.orderId !in existing }
                    _hasMore.value = page < totalPages && newRecords.isNotEmpty()
                    currentPage = page + 1
                    _listState.value = ListState.Ready
                }
                is AhuResult.Failure -> {
                    if (_records.value.isEmpty()) {
                        _listState.value = ListState.Error(billingErrorMessage(result.error))
                    }
                }
            }
            _loadingMore.value = false
        }
    }
}
