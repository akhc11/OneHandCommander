package com.example.onehandcommander.core

import java.io.File

/**
 * ユーザー入力・ジェスチャー・システム通知によって発行される UI イベント
 */
sealed interface ServiceIntent {
    /** フローティングボタンがタップされた */
    object TapFloatingButton : ServiceIntent

    /** フローティングボタンが水平スワイプされた（タッチパッド切替） */
    object SwipeFloatingButton : ServiceIntent

    /** フローティングボタンが垂直スワイプされた（全オーバーレイ閉鎖） */
    object VerticalSwipeFloatingButton : ServiceIntent

    /** テンキーのキーが押下された */
    data class PressTenkey(val key: String) : ServiceIntent

    /** アプリメニュー内の検索バーにフォーカスが当たった */
    data class EnterSearch(val folder: File? = null) : ServiceIntent

    /** アプリメニューや最近のファイル項目が選択されて閉じた */
    object DismissMenu : ServiceIntent

    /** 外部イベント（画面タップ、バックキー等）により全オーバーレイを閉じる要求 */
    object CloseAllOverlays : ServiceIntent

    /** 画面OFFやロック画面遷移によるサービス休止要求 */
    object Suspend : ServiceIntent

    /** 画面ONやロック解除によるサービス復帰要求 */
    object Resume : ServiceIntent

    /** ソフトウェアキーボード展開が検知された */
    object KeyboardOpened : ServiceIntent
}
