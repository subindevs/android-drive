/*
 * Copyright (c) 2025 Proton AG.
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

package me.proton.android.drive.photos.domain.usecase

import kotlinx.coroutines.flow.first
import me.proton.core.drive.backup.domain.repository.BackupFolderRepository
import me.proton.core.drive.backup.domain.repository.BucketRepository
import me.proton.core.drive.base.domain.log.LogTag.BACKUP
import me.proton.core.drive.base.domain.util.coRunCatching
import me.proton.core.drive.drivelink.photo.domain.usecase.CreateAlbum
import me.proton.core.drive.link.domain.entity.FolderId
import me.proton.core.drive.link.domain.extension.userId
import me.proton.core.util.kotlin.CoreLogger
import me.proton.drive.android.settings.domain.usecase.GetDeviceName
import javax.inject.Inject

class EnsureBackupAlbums @Inject constructor(
    private val backupFolderRepository: BackupFolderRepository,
    private val bucketRepository: BucketRepository,
    private val createAlbum: CreateAlbum,
    private val getDeviceName: GetDeviceName,
) {
    suspend operator fun invoke(folderId: FolderId) = coRunCatching {
        val folders = backupFolderRepository.getAll(folderId)
            .filter { it.albumId == null }

        if (folders.isEmpty()) return@coRunCatching

        val userId = folderId.userId
        val deviceName = getDeviceName(userId).first()
        val bucketNames = bucketRepository.getAll().associate { it.bucketId to it.bucketName }

        folders.forEach { folder ->
            val bucketName = bucketNames[folder.bucketId] ?: return@forEach
            val albumId = createAlbum(userId, "$bucketName ($deviceName)", isLocked = false)
                .onFailure { error ->
                    CoreLogger.w(BACKUP, error, "Cannot create album for bucket: ${folder.bucketId}")
                }
                .getOrNull() ?: return@forEach
            backupFolderRepository.updateFolder(folder.copy(albumId = albumId))
        }
    }
}
