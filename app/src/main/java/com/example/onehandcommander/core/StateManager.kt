package com.example.onehandcommander.core

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * MainService の単一方向状態管理（MVI / State Machine）を担当するクラス
 */
class StateManager(
    initialState: ServiceState = ServiceState.Idle
) {
    private val _state = MutableStateFlow<ServiceState>(initialState)
    val state: StateFlow<ServiceState> = _state.asStateFlow()

    val currentState: ServiceState
        get() = _state.value

    /**
     * イベントを受け取り、新しい状態を計算して発行する
     */
    fun processIntent(intent: ServiceIntent) {
        val nextState = reduce(_state.value, intent)
        if (nextState != _state.value) {
            _state.value = nextState
        }
    }

    private fun reduce(current: ServiceState, intent: ServiceIntent): ServiceState {
        return when (intent) {
            is ServiceIntent.Suspend -> {
                ServiceState.Suspended
            }
            is ServiceIntent.Resume -> {
                if (current is ServiceState.Suspended) ServiceState.Idle else current
            }
            is ServiceIntent.CloseAllOverlays -> {
                if (current is ServiceState.Suspended) ServiceState.Suspended else ServiceState.Idle
            }
            is ServiceIntent.DismissMenu -> {
                if (current.isMenuOpen()) ServiceState.Idle else current
            }
            is ServiceIntent.TapFloatingButton -> {
                when (current) {
                    is ServiceState.Idle -> ServiceState.MenuNormal
                    is ServiceState.MenuNormal, is ServiceState.MenuSearch -> ServiceState.Idle
                    is ServiceState.TouchpadActive -> ServiceState.MenuNormal
                    is ServiceState.Suspended -> ServiceState.Suspended
                }
            }
            is ServiceIntent.SwipeFloatingButton -> {
                when (current) {
                    is ServiceState.TouchpadActive -> ServiceState.Idle
                    is ServiceState.MenuNormal, is ServiceState.MenuSearch -> ServiceState.TouchpadActive
                    is ServiceState.Idle -> ServiceState.TouchpadActive
                    is ServiceState.Suspended -> ServiceState.Suspended
                }
            }
            is ServiceIntent.VerticalSwipeFloatingButton -> {
                if (current is ServiceState.Suspended) ServiceState.Suspended else ServiceState.Idle
            }
            is ServiceIntent.EnterSearch -> {
                if (current is ServiceState.Suspended) ServiceState.Suspended else ServiceState.MenuSearch(intent.folder)
            }
            is ServiceIntent.PressTenkey -> {
                current
            }
            is ServiceIntent.KeyboardOpened -> {
                // キーボード展開時はタッチパッドを非表示にして待機状態へ遷移
                if (current is ServiceState.TouchpadActive) ServiceState.Idle else current
            }
        }
    }
}
