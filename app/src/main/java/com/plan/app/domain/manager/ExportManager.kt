package com.plan.app.domain.manager

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.net.Uri
import android.provider.MediaStore
import com.plan.app.domain.model.Cell
import com.plan.app.domain.model.Content
import com.plan.app.domain.model.ContentType
import com.plan.app.domain.model.Project
import com.plan.app.domain.model.Region
import com.plan.app.domain.model.State
import com.plan.app.domain.repository.ContentRepository
import com.plan.app.domain.repository.ProjectRepository
import com.plan.app.domain.repository.RegionRepository
import com.plan.app.domain.repository.StateRepository
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Data class for export/import project data.
 */
data class ProjectExportData(
    val name: String,
    val type1: String?,
    val type2: String?,
    val description: String?,
    val note: String?,
    val cellSize: Int
)

data class RegionExportData(
    val name: String,
    val stateName: String?,
    val stateColor: Int?,
    val type1: String?,
    val type2: String?,
    val description: String?,
    val note: String?,
    val cells: List<CellExportData>,
    val contents: List<ContentExportData>
)

data class CellExportData(
    val row: Int,
    val col: Int
)

data class ContentExportData(
    val type: String,
    val data: String, // For media, this will be relative path
    val sortOrder: Int
)

data class ZipExportResult(
    val success: Boolean,
    val file: File? = null,
    val error: String? = null
)

data class ImportResult(
    val success: Boolean,
    val projectId: Long? = null,
    val error: String? = null
)

/**
 * Manager for export/import operations.
 */
@Singleton
class ExportManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val projectRepository: ProjectRepository,
    private val regionRepository: RegionRepository,
    private val contentRepository: ContentRepository,
    private val stateRepository: StateRepository,
    private val gson: Gson
) {
    /**
     * Export project to ZIP format for transfer to another device.
     * Structure:
     * - project.json
     * - photo.jpg (original photo)
     * - regions/*.json (region data with content info)
     * - media/* (all attached photos and videos)
     */
    suspend fun exportToZip(project: Project, outputFile: File): ZipExportResult {
        return withContext(Dispatchers.IO) {
            try {
                val regions = regionRepository.getRegionsByProjectOnce(project.id)
                val states = stateRepository.getAllStatesOnce()
                
                ZipOutputStream(BufferedOutputStream(FileOutputStream(outputFile))).use { zip ->
                    // Add project.json
                    val projectData = ProjectExportData(
                        name = project.name,
                        type1 = project.type1,
                        type2 = project.type2,
                        description = project.description,
                        note = project.note,
                        cellSize = project.cellSize
                    )
                    addEntryToZip(zip, "project.json", gson.toJson(projectData))
                    
                    // Add photo
                    val photoFile = getFileFromUri(project.photoUri)
                    if (photoFile != null && photoFile.exists()) {
                        addFileToZip(zip, "photo.jpg", photoFile)
                    }
                    
                    // Collect all media files for the media folder
                    val mediaFiles = mutableMapOf<String, File>()
                    
                    // Add regions folder with content
                    for (region in regions) {
                        val state = region.stateId?.let { stateId ->
                            states.find { it.id == stateId }
                        }
                        
                        // Get contents for this region
                        val contents = contentRepository.getContentsByRegionOnce(region.id)
                        val contentExportList = mutableListOf<ContentExportData>()
                        
                        contents.forEachIndexed { index, content ->
                            val relativePath = when (content.type) {
                                ContentType.PHOTO -> "media/photo_${region.id}_$index.jpg"
                                ContentType.VIDEO -> "media/video_${region.id}_$index.mp4"
                                ContentType.TEXT -> content.data // Text data is stored directly
                            }
                            
                            contentExportList.add(
                                ContentExportData(
                                    type = content.type.name,
                                    data = relativePath,
                                    sortOrder = content.sortOrder
                                )
                            )
                            
                            // Add media file to zip
                            if (content.type != ContentType.TEXT) {
                                val mediaFile = getFileFromUri(content.data)
                                if (mediaFile != null && mediaFile.exists()) {
                                    mediaFiles[relativePath] = mediaFile
                                }
                            }
                        }
                        
                        val regionData = RegionExportData(
                            name = region.name,
                            stateName = state?.name,
                            stateColor = state?.color,
                            type1 = region.type1,
                            type2 = region.type2,
                            description = region.description,
                            note = region.note,
                            cells = region.cells.map { CellExportData(it.row, it.col) },
                            contents = contentExportList
                        )
                        
                        // Sanitize region name for filename
                        val safeName = region.name.replace(Regex("[^a-zA-Z0-9_-]"), "_")
                        addEntryToZip(zip, "regions/${safeName}_${region.id}.json", gson.toJson(regionData))
                    }
                    
                    // Add all media files
                    for ((path, file) in mediaFiles) {
                        addFileToZip(zip, path, file)
                    }
                }
                
                ZipExportResult(success = true, file = outputFile)
            } catch (e: Exception) {
                e.printStackTrace()
                ZipExportResult(success = false, error = e.message)
            }
        }
    }
    
    /**
     * Import project from ZIP archive.
     */
    suspend fun importFromZip(zipFile: File, targetDir: File): ImportResult {
        return withContext(Dispatchers.IO) {
            try {
                var projectData: ProjectExportData? = null
                var photoFile: File? = null
                val regionDataList = mutableListOf<Pair<RegionExportData, Long>>()
                val mediaFiles = mutableMapOf<String, File>()
                
                // Extract ZIP contents
                ZipInputStream(BufferedInputStream(FileInputStream(zipFile))).use { zis ->
                    var entry: ZipEntry? = zis.readEntry()
                    
                    while (entry != null) {
                        when {
                            entry.name == "project.json" -> {
                                val content = zis.readBytes().toString(Charsets.UTF_8)
                                projectData = gson.fromJson(content, ProjectExportData::class.java)
                            }
                            entry.name == "photo.jpg" -> {
                                photoFile = File(targetDir, "photo_${System.currentTimeMillis()}.jpg")
                                FileOutputStream(photoFile).use { fos ->
                                    zis.copyTo(fos)
                                }
                            }
                            entry.name.startsWith("regions/") && entry.name.endsWith(".json") -> {
                                val content = zis.readBytes().toString(Charsets.UTF_8)
                                val regionData = gson.fromJson(content, RegionExportData::class.java)
                                // Extract region ID from filename
                                val idStr = entry.name.substringAfterLast("_").substringBefore(".")
                                val regionId = idStr.toLongOrNull() ?: System.currentTimeMillis()
                                regionDataList.add(regionData to regionId)
                            }
                            entry.name.startsWith("media/") -> {
                                val mediaFile = File(targetDir, entry.name.substringAfterLast("/"))
                                FileOutputStream(mediaFile).use { fos ->
                                    zis.copyTo(fos)
                                }
                                mediaFiles[entry.name] = mediaFile
                            }
                        }
                        entry = zis.readEntry()
                    }
                }
                
                if (projectData == null) {
                    return@withContext ImportResult(success = false, error = "Invalid ZIP file: missing project.json")
                }
                
                // Copy photo to app's internal storage
                val internalPhotoFile = if (photoFile != null && photoFile.exists()) {
                    val destFile = File(context.filesDir, "project_${System.currentTimeMillis()}.jpg")
                    photoFile.copyTo(destFile, overwrite = true)
                    destFile
                } else null
                
                // Create project
                val project = Project(
                    name = projectData!!.name,
                    photoUri = internalPhotoFile?.absolutePath ?: "",
                    type1 = projectData!!.type1,
                    type2 = projectData!!.type2,
                    description = projectData!!.description,
                    note = projectData!!.note,
                    cellSize = projectData!!.cellSize
                )
                
                val projectId = projectRepository.insertProject(project)
                
                // Create regions with contents
                for ((regionData, _) in regionDataList) {
                    // Get or create state
                    val stateId = if (regionData.stateName != null && regionData.stateColor != null) {
                        stateRepository.getOrCreate(regionData.stateName, regionData.stateColor).id
                    } else null
                    
                    val region = Region(
                        projectId = projectId,
                        name = regionData.name,
                        stateId = stateId,
                        type1 = regionData.type1,
                        type2 = regionData.type2,
                        description = regionData.description,
                        note = regionData.note,
                        cells = regionData.cells.map { Cell(it.row, it.col) }
                    )
                    
                    val regionId = regionRepository.insertRegion(region)
                    
                    // Create contents
                    val contents = regionData.contents.mapIndexed { index, contentData ->
                        val data = if (contentData.type == "PHOTO" || contentData.type == "VIDEO") {
                            // Find the media file and copy to internal storage
                            val sourceFile = mediaFiles[contentData.data]
                            if (sourceFile != null && sourceFile.exists()) {
                                val destFile = File(context.filesDir, 
                                    if (contentData.type == "PHOTO") "photo_${regionId}_$index.jpg" 
                                    else "video_${regionId}_$index.mp4"
                                )
                                sourceFile.copyTo(destFile, overwrite = true)
                                destFile.absolutePath
                            } else contentData.data
                        } else contentData.data
                        
                        Content(
                            regionId = regionId,
                            type = ContentType.valueOf(contentData.type),
                            data = data,
                            sortOrder = contentData.sortOrder
                        )
                    }
                    
                    if (contents.isNotEmpty()) {
                        contentRepository.insertContents(contents)
                    }
                }
                
                ImportResult(success = true, projectId = projectId)
            } catch (e: Exception) {
                e.printStackTrace()
                ImportResult(success = false, error = e.message)
            }
        }
    }
    
    /**
     * Export project for PC (HTML + folders).
     * Creates a folder with:
     * - photo_with_areas.jpg (photo with overlay)
     * - report.html (table with all regions)
     * - [region_name]/ folders with media and comments
     */
    suspend fun exportForPC(project: Project, outputDir: File): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                outputDir.mkdirs()
                val regions = regionRepository.getRegionsByProjectOnce(project.id)
                val states = stateRepository.getAllStatesOnce()
                
                // Create photo with areas overlay
                val photoFile = getFileFromUri(project.photoUri)
                if (photoFile != null && photoFile.exists()) {
                    val bitmap = BitmapFactory.decodeFile(photoFile.absolutePath)
                    if (bitmap != null) {
                        val photoWithAreas = drawRegionsOnBitmap(bitmap, regions, states, project.cellSize)
                        File(outputDir, "photo_with_areas.jpg").writeBytes(
                            compressBitmap(photoWithAreas, Bitmap.CompressFormat.JPEG, 90)
                        )
                        bitmap.recycle()
                        photoWithAreas.recycle()
                    }
                }
                
                // Create HTML report
                val htmlContent = generateHtmlReport(project, regions, states)
                File(outputDir, "report.html").writeText(htmlContent)
                
                // Create subfolders for each region
                for (region in regions) {
                    val safeName = region.name.replace(Regex("[^a-zA-Z0-9_-]"), "_")
                    val regionDir = File(outputDir, safeName)
                    regionDir.mkdirs()
                    
                    // Copy media files and create comment.txt
                    val contents = contentRepository.getContentsByRegionOnce(region.id)
                    for ((index, content) in contents.withIndex()) {
                        when (content.type) {
                            ContentType.TEXT -> {
                                File(regionDir, "comment.txt").writeText(content.data)
                            }
                            ContentType.PHOTO -> {
                                val sourceFile = getFileFromUri(content.data)
                                if (sourceFile != null && sourceFile.exists()) {
                                    sourceFile.copyTo(File(regionDir, "photo_$index.jpg"), overwrite = true)
                                }
                            }
                            ContentType.VIDEO -> {
                                val sourceFile = getFileFromUri(content.data)
                                if (sourceFile != null && sourceFile.exists()) {
                                    sourceFile.copyTo(File(regionDir, "video_$index.mp4"), overwrite = true)
                                }
                            }
                        }
                    }
                }
                
                true
            } catch (e: Exception) {
                e.printStackTrace()
                false
            }
        }
    }
    
    private fun drawRegionsOnBitmap(
        bitmap: Bitmap,
        regions: List<Region>,
        states: List<State>,
        cellSize: Int
    ): Bitmap {
        val result = bitmap.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(result)
        
        val gridDivisions = if (cellSize > 0) cellSize else 10
        val cellWidth = bitmap.width.toFloat() / gridDivisions
        val cellHeight = bitmap.height.toFloat() / gridDivisions
        
        val fillPaint = Paint().apply {
            style = Paint.Style.FILL
            alpha = 128 // 50% opacity
        }
        
        val strokePaint = Paint().apply {
            style = Paint.Style.STROKE
            strokeWidth = 3f
            color = Color.BLACK
        }
        
        for (region in regions) {
            val state = region.stateId?.let { stateId ->
                states.find { it.id == stateId }
            }
            
            val color = state?.color?.let { Color(it) } ?: Color.GRAY
            fillPaint.color = color
            
            for (cell in region.cells) {
                val rect = RectF(
                    cell.col * cellWidth,
                    cell.row * cellHeight,
                    (cell.col + 1) * cellWidth,
                    (cell.row + 1) * cellHeight
                )
                canvas.drawRect(rect, fillPaint)
                canvas.drawRect(rect, strokePaint)
            }
        }
        
        return result
    }
    
    private fun compressBitmap(bitmap: Bitmap, format: Bitmap.CompressFormat, quality: Int): ByteArray {
        val stream = ByteArrayOutputStream()
        bitmap.compress(format, quality, stream)
        return stream.toByteArray()
    }
    
    private fun generateHtmlReport(project: Project, regions: List<Region>, states: List<State>): String {
        val builder = StringBuilder()
        builder.append("<!DOCTYPE html><html><head><meta charset='UTF-8'><title>${escapeHtml(project.name)}</title>")
        builder.append("<style>")
        builder.append("body{font-family:Arial,sans-serif;margin:20px;max-width:1200px;}")
        builder.append("h1{color:#333;}")
        builder.append("table{border-collapse:collapse;width:100%;margin-top:20px;}")
        builder.append("th,td{border:1px solid #ddd;padding:8px;text-align:left;}")
        builder.append("th{background-color:#4CAF50;color:white;}")
        builder.append("tr:nth-child(even){background-color:#f2f2f2;}")
        builder.append(".color-box{display:inline-block;width:20px;height:20px;margin-right:8px;vertical-align:middle;border:1px solid #333;}")
        builder.append(".project-info{background:#f5f5f5;padding:15px;border-radius:5px;margin-bottom:20px;}")
        builder.append("</style>")
        builder.append("</head><body>")
        
        // Project header
        builder.append("<h1>${escapeHtml(project.name)}</h1>")
        
        // Project info
        builder.append("<div class='project-info'>")
        if (!project.description.isNullOrBlank()) {
            builder.append("<p><strong>Description:</strong> ${escapeHtml(project.description)}</p>")
        }
        if (!project.type1.isNullOrBlank()) {
            builder.append("<p><strong>Type 1:</strong> ${escapeHtml(project.type1)}</p>")
        }
        if (!project.type2.isNullOrBlank()) {
            builder.append("<p><strong>Type 2:</strong> ${escapeHtml(project.type2)}</p>")
        }
        if (!project.note.isNullOrBlank()) {
            builder.append("<p><strong>Note:</strong> ${escapeHtml(project.note)}</p>")
        }
        builder.append("<p><strong>Photo:</strong> <a href='photo_with_areas.jpg'>View photo with areas</a></p>")
        builder.append("</div>")
        
        // Regions table
        builder.append("<h2>Regions (${regions.size})</h2>")
        builder.append("<table>")
        builder.append("<tr><th>#</th><th>Name</th><th>State</th><th>Type 1</th><th>Type 2</th><th>Description</th><th>Media</th></tr>")
        
        for ((index, region) in regions.withIndex()) {
            val state = region.stateId?.let { stateId ->
                states.find { it.id == stateId }
            }
            
            val safeName = region.name.replace(Regex("[^a-zA-Z0-9_-]"), "_")
            
            builder.append("<tr>")
            builder.append("<td>${index + 1}</td>")
            builder.append("<td><strong>${escapeHtml(region.name)}</strong></td>")
            builder.append("<td>")
            if (state != null) {
                val colorHex = "#${Integer.toHexString(state.color).substring(2).uppercase()}"
                builder.append("<span class='color-box' style='background-color:$colorHex;'></span>")
                builder.append(escapeHtml(state.name))
            } else {
                builder.append("N/A")
            }
            builder.append("</td>")
            builder.append("<td>${escapeHtml(region.type1 ?: "")}</td>")
            builder.append("<td>${escapeHtml(region.type2 ?: "")}</td>")
            builder.append("<td>${escapeHtml(region.description ?: "")}</td>")
            builder.append("<td><a href='$safeName/'>View files</a></td>")
            builder.append("</tr>")
        }
        
        builder.append("</table>")
        
        // Footer
        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        builder.append("<p style='color:#888;margin-top:30px;'>Generated on ${dateFormat.format(Date())}</p>")
        builder.append("</body></html>")
        
        return builder.toString()
    }
    
    private fun escapeHtml(text: String): String {
        return text
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&#39;")
    }
    
    private fun addEntryToZip(zip: ZipOutputStream, name: String, content: String) {
        zip.putNextEntry(ZipEntry(name))
        zip.write(content.toByteArray(Charsets.UTF_8))
        zip.closeEntry()
    }
    
    private fun addFileToZip(zip: ZipOutputStream, name: String, file: File) {
        zip.putNextEntry(ZipEntry(name))
        file.inputStream().use { it.copyTo(zip) }
        zip.closeEntry()
    }
    
    private fun getFileFromUri(uriString: String): File? {
        return try {
            if (uriString.startsWith("content://") || uriString.startsWith("file://")) {
                // For content URIs, copy to temp file
                val uri = Uri.parse(uriString)
                val inputStream = context.contentResolver.openInputStream(uri) ?: return null
                val tempFile = File.createTempFile("export_", ".tmp", context.cacheDir)
                tempFile.outputStream().use { outputStream ->
                    inputStream.copyTo(outputStream)
                }
                inputStream.close()
                tempFile
            } else {
                // Direct file path
                val file = File(uriString)
                if (file.exists()) file else null
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
    
    // Extension function for ZipInputStream
    private fun ZipInputStream.readEntry(): ZipEntry? {
        return try {
            nextEntry
        } catch (e: Exception) {
            null
        }
    }
}
