/*
 * Copyright (c) 2026 Proton AG.
 * This file is part of Proton Drive.
 *
 * Proton Drive is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Proton Drive is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Proton Drive.  If not, see <https://www.gnu.org/licenses/>.
 */

package me.proton.android.drive.ui.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import me.proton.android.drive.extension.log
import me.proton.android.drive.ui.viewevent.ConfirmRemoveAllOfflineViewEvent
import me.proton.android.drive.ui.viewstate.ConfirmRemoveAllOfflineDialogViewState
import me.proton.core.drive.base.domain.log.LogTag.VIEW_MODEL
import me.proton.core.drive.base.presentation.viewmodel.UserViewModel
import me.proton.core.drive.drivelink.offline.domain.usecase.RemoveAllOffline
import javax.inject.Inject

@HiltViewModel
@ExperimentalCoroutinesApi
class ConfirmRemoveAllOfflineDialogViewModel @Inject constructor(
    private val removeAllOffline: RemoveAllOffline,
    savedStateHandle: SavedStateHandle,
) : ViewModel(), UserViewModel by UserViewModel(savedStateHandle) {

    private val _viewState = MutableStateFlow(ConfirmRemoveAllOfflineDialogViewState())
    val viewState: StateFlow<ConfirmRemoveAllOfflineDialogViewState> = _viewState.asStateFlow()

    fun viewEvent(dismiss: () -> Unit) = object : ConfirmRemoveAllOfflineViewEvent {
        override val onConfirm = {
            viewModelScope.launch {
                _viewState.update { it.copy(isLoading = true) }
                removeAllOffline(userId).onFailure { error ->
                    error.log(VIEW_MODEL, "Cannot remove all offline")
                }
                dismiss()
            }
            Unit
        }
        override val onCancel = dismiss
    }
}
