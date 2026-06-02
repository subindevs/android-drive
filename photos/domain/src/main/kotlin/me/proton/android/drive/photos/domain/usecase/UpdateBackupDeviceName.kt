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

import me.proton.core.domain.entity.UserId
import me.proton.core.drive.backup.domain.repository.BackupFolderRepository
import me.proton.core.drive.backup.domain.repository.BucketRepository
import me.proton.core.drive.base.domain.extension.toResult
import me.proton.core.drive.base.domain.log.LogTag.BACKUP
import me.proton.core.drive.base.domain.util.coRunCatching
import me.proton.core.drive.drivelink.photo.domain.usecase.RenameAlbum
import me.proton.core.drive.share.crypto.domain.usecase.GetPhotoShare
import me.proton.core.util.kotlin.CoreLogger
import me.proton.drive.android.settings.domain.usecase.UpdateDeviceName
import javax.inject.Inject

class UpdateBackupDeviceName @Inject constructor(
    private val updateDeviceName: UpdateDeviceName,
    private val backupFolderRepository: BackupFolderRepository,
    private val bucketRepository: BucketRepository,
    private val getPhotoShare: GetPhotoShare,
    private val renameAlbum: RenameAlbum,
) {
    suspend operator fun invoke(userId: UserId, deviceName: String) = coRunCatching {
        updateDeviceName(userId, deviceName).getOrThrow()

        val folders = backupFolderRepository.getAll(userId).filter { it.albumId != null }
        if (folders.isEmpty()) return@coRunCatching

        val volumeId = getPhotoShare(userId).toResult().getOrThrow().volumeId
        val bucketNames = bucketRepository.getAll().associate { it.bucketId to it.bucketName }

        folders.forEach { folder ->
            val bucketName = bucketNames[folder.bucketId] ?: return@forEach
            val albumId = folder.albumId ?: return@forEach
            renameAlbum(volumeId, albumId, "$bucketName ($deviceName)")
                .onFailure { error ->
                    CoreLogger.w(BACKUP, error, "Failed to rename album for bucket ${folder.bucketId}")
                }
        }
    }
}
