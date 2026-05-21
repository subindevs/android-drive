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

package me.proton.core.drive.drivelink.offline.domain.usecase

import me.proton.core.domain.entity.UserId
import me.proton.core.drive.base.domain.util.coRunCatching
import javax.inject.Inject

class RemoveAllOffline @Inject constructor(
    private val getDecryptedOfflineDriveLinks: GetDecryptedOfflineDriveLinks,
    private val toggleOffline: ToggleOffline,
) {
    suspend operator fun invoke(userId: UserId) = coRunCatching {
        getDecryptedOfflineDriveLinks(userId).getOrThrow().forEach { driveLink ->
            toggleOffline(driveLink)
        }
    }
}
