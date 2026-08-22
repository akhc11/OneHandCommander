package com.example.onehandcommander.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * StateManager (MVI State Machine) の単体テスト
 * - 全状態遷移とガード条件の網羅
 * - ガード条件（Suspended状態でのイベント遮断など）の検証
 * - キーボードオープン時のタッチパッド自動クローズの検証
 */
class StateManagerTest {

    private lateinit var stateManager: StateManager

    @Before
    fun setUp() {
        stateManager = StateManager(ServiceState.Idle)
    }

    @Test
    fun initialState_isIdle() {
        assertEquals(ServiceState.Idle, stateManager.currentState)
    }

    @Test
    fun tapFloatingButton_transitionsBetweenIdleAndMenu() {
        // Idle -> Tap -> MenuNormal
        stateManager.processIntent(ServiceIntent.TapFloatingButton)
        assertEquals(ServiceState.MenuNormal, stateManager.currentState)

        // MenuNormal -> Tap -> Idle
        stateManager.processIntent(ServiceIntent.TapFloatingButton)
        assertEquals(ServiceState.Idle, stateManager.currentState)
    }

    @Test
    fun swipeFloatingButton_transitionsBetweenIdleAndTouchpad() {
        // Idle -> Swipe -> TouchpadActive
        stateManager.processIntent(ServiceIntent.SwipeFloatingButton)
        assertEquals(ServiceState.TouchpadActive, stateManager.currentState)

        // TouchpadActive -> Swipe -> Idle
        stateManager.processIntent(ServiceIntent.SwipeFloatingButton)
        assertEquals(ServiceState.Idle, stateManager.currentState)
    }

    @Test
    fun directSwitching_betweenTouchpadAndMenu_withoutClosingFirst() {
        // TouchpadActive 状態で Tap -> 直接 MenuNormal へ切り替え
        stateManager.processIntent(ServiceIntent.SwipeFloatingButton)
        assertEquals(ServiceState.TouchpadActive, stateManager.currentState)

        stateManager.processIntent(ServiceIntent.TapFloatingButton)
        assertEquals(ServiceState.MenuNormal, stateManager.currentState)

        // MenuNormal 状態で Swipe -> 直接 TouchpadActive へ切り替え
        stateManager.processIntent(ServiceIntent.SwipeFloatingButton)
        assertEquals(ServiceState.TouchpadActive, stateManager.currentState)
    }

    @Test
    fun keyboardOpened_dismissesTouchpad_whenActive() {
        // TouchpadActive 状態でキーボードが開いた場合 -> Idle へ戻る
        stateManager.processIntent(ServiceIntent.SwipeFloatingButton)
        assertEquals(ServiceState.TouchpadActive, stateManager.currentState)

        stateManager.processIntent(ServiceIntent.KeyboardOpened)
        assertEquals(ServiceState.Idle, stateManager.currentState)
    }

    @Test
    fun keyboardOpened_doesNothing_whenNotTouchpadActive() {
        // MenuNormal 状態でキーボードが開いた場合 -> そのまま維持
        stateManager.processIntent(ServiceIntent.TapFloatingButton)
        assertEquals(ServiceState.MenuNormal, stateManager.currentState)

        stateManager.processIntent(ServiceIntent.KeyboardOpened)
        assertEquals(ServiceState.MenuNormal, stateManager.currentState)
    }

    @Test
    fun enterSearch_transitionsToMenuSearch() {
        stateManager.processIntent(ServiceIntent.EnterSearch("downloads"))
        assertTrue(stateManager.currentState is ServiceState.MenuSearch)
        assertEquals("downloads", (stateManager.currentState as ServiceState.MenuSearch).folder)
    }

    @Test
    fun suspendAndResume_lifecycleTransitions() {
        // MenuNormal -> Suspend -> Suspended
        stateManager.processIntent(ServiceIntent.TapFloatingButton)
        assertEquals(ServiceState.MenuNormal, stateManager.currentState)

        stateManager.processIntent(ServiceIntent.Suspend)
        assertEquals(ServiceState.Suspended, stateManager.currentState)

        // Suspended 中は Tap や Swipe を受けても Suspended を維持
        stateManager.processIntent(ServiceIntent.TapFloatingButton)
        assertEquals(ServiceState.Suspended, stateManager.currentState)

        stateManager.processIntent(ServiceIntent.SwipeFloatingButton)
        assertEquals(ServiceState.Suspended, stateManager.currentState)

        // Resume -> Idle
        stateManager.processIntent(ServiceIntent.Resume)
        assertEquals(ServiceState.Idle, stateManager.currentState)
    }

    @Test
    fun dismissMenu_onlyWorksWhenMenuOpen() {
        // Idle で DismissMenu -> Idle のまま
        stateManager.processIntent(ServiceIntent.DismissMenu)
        assertEquals(ServiceState.Idle, stateManager.currentState)

        // MenuNormal で DismissMenu -> Idle へ戻る
        stateManager.processIntent(ServiceIntent.TapFloatingButton)
        assertEquals(ServiceState.MenuNormal, stateManager.currentState)

        stateManager.processIntent(ServiceIntent.DismissMenu)
        assertEquals(ServiceState.Idle, stateManager.currentState)
    }

    @Test
    fun closeAllOverlays_returnsToIdle_unlessSuspended() {
        stateManager.processIntent(ServiceIntent.SwipeFloatingButton)
        assertEquals(ServiceState.TouchpadActive, stateManager.currentState)

        stateManager.processIntent(ServiceIntent.CloseAllOverlays)
        assertEquals(ServiceState.Idle, stateManager.currentState)

        // Suspended の場合は CloseAllOverlays でも Suspended を維持
        stateManager.processIntent(ServiceIntent.Suspend)
        stateManager.processIntent(ServiceIntent.CloseAllOverlays)
        assertEquals(ServiceState.Suspended, stateManager.currentState)
    }
}
