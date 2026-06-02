/*
 * Copyright (c) 2023 Proton AG.
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

import androidx.work.OneTimeWorkRequest
import me.proton.core.drive.backup.domain.usecase.GetFolderFromFile
import me.proton.core.drive.base.domain.extension.getOrNull
import me.proton.core.drive.base.domain.extension.toResult
import me.proton.core.drive.base.domain.log.LogTag.BACKUP
import me.proton.core.drive.linkupload.domain.entity.UploadFileLink
import me.proton.core.drive.linkupload.domain.extension.fileId
import me.proton.core.drive.linkupload.domain.usecase.GetUploadFileLink
import me.proton.core.drive.upload.data.worker.CleanupWorkers
import me.proton.core.util.kotlin.CoreLogger
import javax.inject.Inject
import kotlin.time.Duration.Companion.seconds

class BackupCleanupWorkers @Inject constructor(
    private val getFolderFromFile: GetFolderFromFile,
    private val getUploadFileLink: GetUploadFileLink,
) : CleanupWorkers {

    override suspend fun additionalCleanupWorkers(
        uploadFileLinkId: Long,
    ): List<OneTimeWorkRequest> =
        additionalCleanupWorkers(
            uploadFileLink = getUploadFileLink(uploadFileLinkId).toResult().getOrThrow(),
        )

    override suspend fun additionalCleanupWorkers(
        uploadFileLink: UploadFileLink,
    ): List<OneTimeWorkRequest> {
        val userId = uploadFileLink.userId
        val uriString = uploadFileLink.uriString

        return if (uriString == null) {
            emptyList()
        } else {
            getFolderFromFile(userId, uriString)
                .getOrNull(BACKUP, "Cannot get folder from file: $uriString")
                ?.let { backupFolder ->
                    val workers = mutableListOf(
                        BackupScheduleUploadFolderWorker.getWorkRequest(
                            backupFolder = backupFolder,
                            delay = 60.seconds,
                        ),
                        BackupNotificationWorker.getWorkRequest(backupFolder.folderId),
                        BackupClearFileWorker.getWorkRequest(backupFolder, uriString),
                    )
                    val albumId = backupFolder.albumId
                    val fileId = uploadFileLink.fileId
                    if (albumId != null && fileId != null) {
                        workers += BackupAddToAlbumWorker.getWorkRequest(
                            userId = userId,
                            shareId = uploadFileLink.shareId,
                            volumeId = uploadFileLink.volumeId,
                            albumId = albumId,
                            fileId = fileId,
                        )
                    } else if (albumId != null) {
                        CoreLogger.w(BACKUP, "Cannot add to album: fileId is null for $uriString")
                    }
                    workers
                }.orEmpty()
        }
    }
}
