package com.plan.app.domain.manager

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.net.Uri
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
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import javax.inject.Inject
import javax.inject.Singleton

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
    val data: String,
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

@Singleton
class ExportManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val projectRepository: ProjectRepository,
    private val regionRepository: RegionRepository,
    private val contentRepository: ContentRepository,
    private val stateRepository: StateRepository,
    private val gson: Gson
) {
    
    suspend fun exportToZip(project: Project, outputFile: File): ZipExportResult {
        return withContext(Dispatchers.IO) {
            try {
                val regions = regionRepository.getRegionsByProjectOnce(project.id)
                val states = stateRepository.getAllStatesOnce()
                
                ZipOutputStream(java.io.BufferedOutputStream(java.io.FileOutputStream(outputFile))).use { zip ->
                    val projectData = ProjectExportData(
                        name = project.name,
                        type1 = project.type1,
                        type2 = project.type2,
                        description = project.description,
                        note = project.note,
                        cellSize = project.cellSize
                    )
                    addEntryToZip(zip, "project.json", gson.toJson(projectData))
                    
                    val photoFile = getFileFromUri(project.photoUri)
                    if (photoFile != null && photoFile.exists()) {
                        addFileToZip(zip, "photo.jpg", photoFile)
                    }
                    
                    val mediaFiles = mutableMapOf<String, File>()
                    
                    for (region in regions) {
                        val state = region.stateId?.let { stateId ->
                            states.find { it.id == stateId }
                        }
                        
                        val contents = contentRepository.getContentsByRegionOnce(region.id)
                        val contentExportList = mutableListOf<ContentExportData>()
                        
                        contents.forEachIndexed { index, content ->
                            val relativePath = when (content.type) {
                                ContentType.PHOTO -> "media/photo_${region.id}_$index.jpg"
                                ContentType.VIDEO -> "media/video_${region.id}_$index.mp4"
                                ContentType.TEXT -> content.data
                            }
                            
                            contentExportList.add(
                                ContentExportData(
                                    type = content.type.name,
                                    data = relativePath,
                                    sortOrder = content.sortOrder
                                )
                            )
                            
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
                        
                        val safeName = region.name.replace(Regex("[^a-zA-Z0-9_-]"), "_")
                        addEntryToZip(zip, "regions/${safeName}_${region.id}.json", gson.toJson(regionData))
                    }
                    
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
    
    suspend fun exportForPC(project: Project, outputDir: File): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                outputDir.mkdirs()
                val regions = regionRepository.getRegionsByProjectOnce(project.id)
                val states = stateRepository.getAllStatesOnce()
                
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
                
                val htmlContent = generateHtmlReport(project, regions, states)
                File(outputDir, "report.html").writeText(htmlContent)
                
                for (region in regions) {
                    val safeName = region.name.replace(Regex("[^a-zA-Z0-9_-]"), "_")
                    val regionDir = File(outputDir, safeName)
                    regionDir.mkdirs()
                    
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
            alpha = 128
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
            
            val color = state?.color ?: Color.GRAY
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
        
        builder.append("<h1>${escapeHtml(project.name)}</h1>")
        
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
                val uri = Uri.parse(uriString)
                val inputStream = context.contentResolver.openInputStream(uri) ?: return null
                val tempFile = File.createTempFile("export_", ".tmp", context.cacheDir)
                tempFile.outputStream().use { outputStream ->
                    inputStream.copyTo(outputStream)
                }
                inputStream.close()
                tempFile
            } else {
                val file = File(uriString)
                if (file.exists()) file else null
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}
