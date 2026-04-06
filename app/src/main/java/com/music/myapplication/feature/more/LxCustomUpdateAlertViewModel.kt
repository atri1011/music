package com.music.myapplication.feature.more

import androidx.lifecycle.ViewModel
import com.music.myapplication.data.repository.lx.LxCustomUpdateAlertCoordinator
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.StateFlow

@HiltViewModel
class LxCustomUpdateAlertViewModel @Inject constructor(
    private val coordinator: LxCustomUpdateAlertCoordinator
) : ViewModel() {
    val state: StateFlow<com.music.myapplication.data.repository.lx.LxUpdateAlertInfo?> =
        coordinator.alertState

    fun dismiss() {
        coordinator.dismiss()
    }
}
