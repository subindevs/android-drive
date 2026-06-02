/*
 * Copyright (c) 2025 Proton AG.
 * This file is part of Proton Core.
 *
 * Proton Core is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Proton Core is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Proton Core.  If not, see <https://www.gnu.org/licenses/>.
 */

package me.proton.core.drive.backup.data.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.OneTimeWorkRequest
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import me.proton.core.domain.entity.UserId
import me.proton.core.drive.backup.data.manager.BackupManagerImpl
import me.proton.core.drive.backup.data.worker.WorkerKeys.KEY_ALBUM_LINK_ID
import me.proton.core.drive.backup.data.worker.WorkerKeys.KEY_FILE_LINK_ID
import me.proton.core.drive.backup.data.worker.WorkerKeys.KEY_SHARE_ID
import me.proton.core.drive.backup.data.worker.WorkerKeys.KEY_USER_ID
import me.proton.core.drive.backup.data.worker.WorkerKeys.KEY_VOLUME_ID
import me.proton.core.drive.base.data.extension.log
import me.proton.core.drive.base.data.workmanager.addTags
import me.proton.core.drive.base.domain.log.LogTag.BACKUP
import me.proton.core.drive.drivelink.photo.domain.usecase.AddPhotosToAlbum
import me.proton.core.drive.link.domain.entity.AlbumId
import me.proton.core.drive.link.domain.entity.FileId
import me.proton.core.drive.share.domain.entity.ShareId
import me.proton.core.drive.volume.domain.entity.VolumeId

@HiltWorker
class BackupAddToAlbumWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted workerParams: WorkerParameters,
    private val addPhotosToAlbum: AddPhotosToAlbum,
) : CoroutineWorker(context, workerParams) {

    private val userId = UserId(requireNotNull(inputData.getString(KEY_USER_ID)))
    private val shareId = ShareId(userId, requireNotNull(inputData.getString(KEY_SHARE_ID)))
    private val volumeId = VolumeId(requireNotNull(inputData.getString(KEY_VOLUME_ID)))
    private val albumLinkId = requireNotNull(inputData.getString(KEY_ALBUM_LINK_ID))
    private val fileLinkId = requireNotNull(inputData.getString(KEY_FILE_LINK_ID))

    override suspend fun doWork(): Result {
        val albumId = AlbumId(shareId, albumLinkId)
        val fileId = FileId(shareId, fileLinkId)
        addPhotosToAlbum(volumeId, albumId, listOf(fileId))
            .onFailure { error ->
                error.log(BACKUP, "Cannot add photo ${fileLinkId} to album ${albumLinkId}")
                return Result.failure()
            }
        return Result.success()
    }

    companion object {
        fun getWorkRequest(
            userId: UserId,
            shareId: ShareId,
            volumeId: VolumeId,
            albumId: AlbumId,
            fileId: FileId,
            tags: Collection<String> = emptyList(),
        ): OneTimeWorkRequest =
            OneTimeWorkRequest.Builder(BackupAddToAlbumWorker::class.java)
                .setInputData(
                    Data.Builder()
                        .putString(KEY_USER_ID, userId.id)
                        .putString(KEY_SHARE_ID, shareId.id)
                        .putString(KEY_VOLUME_ID, volumeId.id)
                        .putString(KEY_ALBUM_LINK_ID, albumId.id)
                        .putString(KEY_FILE_LINK_ID, fileId.id)
                        .build()
                )
                .addTags(listOf(userId.id, BackupManagerImpl.TAG) + tags)
                .build()
    }
}
