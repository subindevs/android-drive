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

package me.proton.android.drive.photos.data.provider

import androidx.core.net.toFile
import androidx.exifinterface.media.ExifInterface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.last
import me.proton.core.drive.base.domain.entity.FileTypeCategory
import me.proton.core.drive.base.domain.entity.toFileTypeCategory
import me.proton.core.drive.base.domain.extension.getOrNull
import me.proton.core.drive.base.domain.extension.toResult
import me.proton.core.drive.base.domain.log.LogTag
import me.proton.core.drive.base.domain.log.logId
import me.proton.core.drive.base.domain.util.coRunCatching
import me.proton.core.drive.drivelink.domain.usecase.GetDriveLink
import me.proton.core.drive.drivelink.download.domain.usecase.GetFile
import me.proton.core.drive.link.domain.entity.FileId
import me.proton.core.drive.link.domain.entity.PhotoTag
import me.proton.core.drive.photo.domain.provider.TagsProvider
import me.proton.core.drive.upload.domain.resolver.UriResolver
import javax.inject.Inject

class ExifTagsProvider @Inject internal constructor(
    private val uriResolver: UriResolver,
    private val getDriveLink: GetDriveLink,
    private val getFile: GetFile,
) : TagsProvider {

    override suspend fun invoke(uriString: String): List<PhotoTag> = coRunCatching(Dispatchers.IO) {
        val mimeType = uriResolver.getMimeType(uriString)
        uriResolver.useInputStream(uriString) { inputStream ->
            ExifInterface(inputStream).toPhotoTags(mimeType?.toFileTypeCategory())
        }
    }.getOrNull(LogTag.PHOTO, "Failed to get PhotoTag list from uriString: $uriString").orEmpty()

    override suspend fun invoke(fileId: FileId): List<PhotoTag> = coRunCatching(Dispatchers.IO) {
        getDriveLink(fileId).toResult().getOrThrow()
            .let { driveLink ->
                val state = getFile(driveLink).last()
                if (state is GetFile.State.Ready) {
                    driveLink to state.uri
                } else {
                    driveLink to null
                }
            }
            .let { (driveLink, fileUri) ->
                fileUri?.toFile()?.inputStream()?.use { fileInputStream ->
                    ExifInterface(fileInputStream).toPhotoTags(driveLink.mimeType.toFileTypeCategory())
                }
            }
    }.getOrNull(LogTag.PHOTO, "Failed to get PhotoTag list for fileId: ${fileId.id.logId()}").orEmpty()

    private fun ExifInterface.toPhotoTags(fileTypeCategory: FileTypeCategory? = null): List<PhotoTag> =
        listOfNotNull(
            takeIf { isSelfie }?.let { PhotoTag.Selfies },
            takeIf { fileTypeCategory == FileTypeCategory.Image && isPortrait }?.let { PhotoTag.Portraits }
        )

    private val ExifInterface.isSelfie: Boolean get() = getAttribute(ExifInterface.TAG_LENS_MODEL)
        ?.contains("front", ignoreCase = true) == true

    private val ExifInterface.isPortrait: Boolean get() = getAttribute(ExifInterface.TAG_CUSTOM_RENDERED)
        ?.toInt() == 7
}
