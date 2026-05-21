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

package me.proton.android.drive.ui.screen

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import me.proton.android.drive.document.scanner.domain.entity.ScannerOptions
import me.proton.android.drive.document.scanner.presentation.component.rememberDocumentScannerLauncher
import me.proton.android.drive.ui.viewmodel.ScanDocumentViewModel
import me.proton.core.drive.link.domain.entity.FolderId

@Composable
fun ScanDocumentScreen(
    navigateToScanDocumentName: (FolderId, Long, String) -> Unit,
    lifecycle: Lifecycle,
    navigateBack: () -> Unit,
) {
    val viewModel = hiltViewModel<ScanDocumentViewModel>()
    var launched by remember { mutableStateOf(false) }
    val scanDocumentLauncher = rememberDocumentScannerLauncher(
        scannerOptions = ScannerOptions.default,
        onResult = { result ->
            viewModel.onScanDocumentResult(
                result = result,
                navigateToScanDocumentName = navigateToScanDocumentName,
                navigateBack = navigateBack,
            )
        },
        onError = { error ->
            viewModel.onScanDocumentError(error)
            navigateBack()
        },
    )
    LaunchedEffect(Unit) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            if (!launched) {
                launched = true
                scanDocumentLauncher.launchWithNotFound(onNotFound = navigateBack)
            }
        }
    }
}
