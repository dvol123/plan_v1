package com.plan.app.domain.manager

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.net.Uri
import com.plan.app.domain.model.Project
import com.plan.app.domain.model.Region
import com.plan.app.domain.repository.ProjectRepository
import com.plan.app.domain.repository.RegionRepository
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.*
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
    val cells: List<Pair<Int, Int>>,
    val contents: List<ContentExportData>
)

data class ContentExportData(
    val type: String,
    val data: String,
    val sortOrder: Int
)

/**
 * Manager for export/import operations.
 */
@Singleton
class ExportManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val projectRepository: ProjectRepository,
    private val regionRepository: RegionRepository,
    private val gson: Gson
) {
    /**
     * Export project to ZIP format for transfer to another device.
     */
    suspend fun exportToZip(project: Project, outputFile: File): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val regions = regionRepository.getRegionsByProjectOnce(project.id)
                
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
                    if (photoFile != null) {
                        addFileToZip(zip, "photo.jpg", photoFile)
                    }
                    
                    // Add regions folder
                    val regionsDir = File(context.cacheDir, "temp_regions")
                    regionsDir.mkdirs()
                    
                    for (region in regions) {
                        val regionData = RegionExportData(
                            name = region.name,
                            stateName = region.state?.name,
                            stateColor = region.state?.color,
                            type1 = region.type1,
                            type2 = region.type2,
                            description = region.description,
                            note = region.note,
                            cells = region.cells.map { Pair(it.row, it.col) },
                            contents = region.contents.map { content ->
                                ContentExportData(
                                    type = content.type.name,
                                    data = content.data,
                                    sortOrder = content.sortOrder
                                )
                            }
                        )
                        addEntryToZip(zip, "regions/${region.name}.json", gson.toJson(regionData))
                    }
                    
                    // Add media folder
                    val mediaDir = File(context.cacheDir, "temp_media")
                    mediaDir.mkdirs()
                }
                true
            } catch (e: Exception) {
                e.printStackTrace()
                false
            }
        }
    }
    
    /**
     * Export project for PC (HTML + folders).
     */
    suspend fun exportForPC(project: Project, outputDir: File): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                outputDir.mkdirs()
                val regions = regionRepository.getRegionsByProjectOnce(project.id)
                
                // Create photo with areas overlay
                // This would require bitmap manipulation
                // For now, we'll create the structure
                
                // Create HTML report
                val htmlContent = generateHtmlReport(project, regions)
                File(outputDir, "report.html").writeText(htmlContent)
                
                // Create subfolders for each region
                for (region in regions) {
                    val regionDir = File(outputDir, region.name)
                    regionDir.mkdirs()
                    
                    // Copy media files
                    for (content in region.contents) {
                        if (content.type == com.plan.app.domain.model.ContentType.TEXT) {
                            File(regionDir, "comment.txt").writeText(content.data)
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
    
    private fun generateHtmlReport(project: Project, regions: List<Region>): String {
        val builder = StringBuilder()
        builder.append("<!DOCTYPE html><html><head><meta charset='UTF-8'><title>${project.name}</title>")
        builder.append("<style>body{font-family:Arial,sans-serif;margin:20px;}table{border-collapse:collapse;width:100%;}th,td{border:1px solid #ddd;padding:8px;text-align:left;}th{background-color:#4CAF50;color:white;}</style>")
        builder.append("</head><body>")
        builder.append("<h1>${project.name}</h1>")
        if (!project.description.isNullOrBlank()) {
            builder.append("<p><strong>Description:</strong> ${project.description}</p>")
        }
        builder.append("<h2>Regions</h2>")
        builder.append("<table><tr><th>Name</th><th>State</th><th>Description</th><th>Media</th></tr>")
        
        for (region in regions) {
            builder.append("<tr>")
            builder.append("<td>${region.name}</td>")
            builder.append("<td style='background-color:#${Integer.toHexString(region.state?.color ?: 0).substring(2)}'>${region.state?.name ?: "N/A"}</td>")
            builder.append("<td>${region.description ?: ""}</td>")
            builder.append("<td><a href='${region.name}/'>View files</a></td>")
            builder.append("</tr>")
        }
        
        builder.append("</table></body></html>")
        return builder.toString()
    }
    
    private fun addEntryToZip(zip: ZipOutputStream, name: String, content: String) {
        zip.putNextEntry(ZipEntry(name))
        zip.write(content.toByteArray())
        zip.closeEntry()
    }
    
    private fun addFileToZip(zip: ZipOutputStream, name: String, file: File) {
        zip.putNextEntry(ZipEntry(name))
        file.inputStream().use { it.copyTo(zip) }
        zip.closeEntry()
    }
    
    private fun getFileFromUri(uriString: String): File? {
        return try {
            val uri = Uri.parse(uriString)
            // Handle different URI schemes
            null // Placeholder - implement based on storage strategy
        } catch (e: Exception) {
            null
        }
    }
}
