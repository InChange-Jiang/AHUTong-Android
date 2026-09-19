package com.ahu.ahutong.ui.screen.main.home

/**
 * 主页 3×4 网格模型：数据 + 贪心排布，纯逻辑无 UI。
 *
 * 布局族已统一（全主题同一份配置，主题只换皮肤）：
 * - 校园卡（主卡片）恒占 4 格，形态 ∈ {2×2 方块, 1×4 整行}，固定锚在左上角
 * - 其余 8 格 = 7 个功能槽（[HomeGridConfig.icons]，可留空）+ 1 个「更多」入口
 * - 图标格由贪心算法从左到右、从上到下自动填入不与主卡片冲突的空位
 */

/** 校园卡形态。storageValue 持久化用，勿改。 */
enum class CampusSpan(val cols: Int, val rows: Int, val storageValue: String, val displayName: String) {
    SQUARE_2X2(2, 2, "2x2", "方块 2×2"),
    ROW_1X4(4, 1, "1x4", "整行 1×4");

    companion object {
        fun fromStorage(value: String?): CampusSpan =
            entries.firstOrNull { it.storageValue == value } ?: SQUARE_2X2
    }
}

/** 网格中一张已落位的卡片（像素坐标由渲染层按格换算）。 */
data class GridPlacement(
    val col: Int,
    val row: Int,
    val spanCols: Int,
    val spanRows: Int
)

/** 网格配置：主卡片形态 + 7 个功能槽（按贪心顺序，null=空位）。 */
data class HomeGridConfig(
    val campusSpan: CampusSpan = CampusSpan.SQUARE_2X2,
    val icons: List<String?> = defaultIcons()
) {
    init {
        require(icons.size == ICON_SLOT_COUNT) { "icons 必须恒为 $ICON_SLOT_COUNT 槽" }
    }

    companion object {
        const val GRID_COLS = 4
        const val GRID_ROWS = 3
        const val ICON_SLOT_COUNT = 7

        /** 「更多」入口的保留 id（不落存储，布局时自动追加为第 8 格）。 */
        const val MORE_SLOT_ID = "__more__"

        fun defaultIcons(): List<String?> = listOf(
            "electricity", "bathroom", "grade", "exam",
            "weather", "network_recharge", "free_classroom"
        )
    }
}

/**
 * 贪心排布：主卡片锚定左上角后，8 个 1×1 项（7 图标槽的非空项 + 「更多」）
 * 按行主序填入剩余空位。返回 槽位标识（widget id 或 MORE_SLOT_ID 或 "#empty_N" 空位占位）→ 落位。
 * 空槽也输出占位落位（编辑模式下要画「+」）。
 */
fun computeHomeGridPlacements(config: HomeGridConfig): List<Pair<String, GridPlacement>> {
    val occupied = Array(HomeGridConfig.GRID_ROWS) { BooleanArray(HomeGridConfig.GRID_COLS) }
    val span = config.campusSpan
    for (r in 0 until span.rows) {
        for (c in 0 until span.cols) occupied[r][c] = true
    }
    val campusPlacement = GridPlacement(0, 0, span.cols, span.rows)

    // 待排项：7 个功能槽（保留槽位序，含空位占位）+ 更多
    val queue = config.icons.mapIndexed { index, id -> (id ?: "#empty_$index") } +
        HomeGridConfig.MORE_SLOT_ID

    val placements = mutableListOf<Pair<String, GridPlacement>>()
    var qi = 0
    for (r in 0 until HomeGridConfig.GRID_ROWS) {
        for (c in 0 until HomeGridConfig.GRID_COLS) {
            if (occupied[r][c] || qi >= queue.size) continue
            placements += queue[qi] to GridPlacement(c, r, 1, 1)
            occupied[r][c] = true
            qi++
        }
    }
    return listOf(CampusCardSlotId to campusPlacement) + placements
}

/** 校园卡在排布结果里的固定标识。 */
const val CampusCardSlotId = "__campus__"

/** 图标槽内容归一化：未知 id 剔除、恒 7 槽。 */
fun normalizeHomeGridIcons(icons: List<String?>, knownIds: Set<String>): List<String?> =
    List(HomeGridConfig.ICON_SLOT_COUNT) { index ->
        icons.getOrNull(index)?.takeIf { it in knownIds }
    }
