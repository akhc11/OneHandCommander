package com.example.onehandcommander.core

import java.io.File

/**
 * MainService および OverlayManager における UI の一意な状態を表現する sealed interface
 */
sealed interface ServiceState {
    /**
     * すべてのオーバーレイ（メニュー、テンキー、タッチパッド）が非表示の待機状態
     */
    object Idle : ServiceState

    /**
     * アプリメニュー（アプリグリッド + 最近のファイル）とテンキーが表示されている状態
     */
    object MenuNormal : ServiceState

    /**
     * アプリメニューで検索バーにフォーカスが当たり、テキスト入力中の状態
     */
    data class MenuSearch(
        val currentFolder: File? = null
    ) : ServiceState

    /**
     * タッチパッド（バーチャルカーソル操作）が表示されている状態
     */
    object TouchpadActive : ServiceState

    /**
     * 画面OFFやKeyguardロック等により、全オーバーレイ（フローティングボタン含む）が休止している状態
     */
    object Suspended : ServiceState

    /**
     * メニュー（通常または検索）が開いているかどうかを判定
     */
    fun isMenuOpen(): Boolean = this is MenuNormal || this is MenuSearch

    /**
     * タッチパッドが有効かどうかを判定
     */
    fun isTouchpadOpen(): Boolean = this is TouchpadActive
}
