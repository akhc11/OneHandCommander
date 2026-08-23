package com.example.onehandcommander.ui.overlays.model

/**
 * MenuGridItem
 * AppMenuのグリッドに表示される各スロットのデータ表現。
 * 01〜40番のスロットアクションと、表示用アイコン/ラベル情報を保持。
 */
data class MenuGridItem(
    val slotNumber: Int, // 1..40 (表示用バッジ番号)
    val action: MenuSlotAction,
    val title: String,
    val iconType: DirectIconType,
    val appPackageName: String? = null,
    val isCustomized: Boolean = false
)
