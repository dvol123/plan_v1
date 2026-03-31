package com.plan.app.domain.manager

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
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
    val originalFileName: String? = null,
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
    val error: String? = null,
    val importedCount: Int = 1  // Number of projects imported (for multi-project import)
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
                    // Track used paths to ensure uniqueness
                    val usedPaths = mutableSetOf<String>()
                    
                    for (region in regions) {
                        val state = region.stateId?.let { stateId ->
                            states.find { it.id == stateId }
                        }
                        
                        val contents = contentRepository.getContentsByRegionOnce(region.id)
                        val contentExportList = mutableListOf<ContentExportData>()
                        
                        contents.forEachIndexed { index, content ->
                            // Use original file name if available, otherwise generate one
                            // Ensure unique path by adding region_id suffix if needed
                            val baseRelativePath = when (content.type) {
                                ContentType.PHOTO -> {
                                    val ext = content.originalFileName?.substringAfterLast(".", "jpg") ?: "jpg"
                                    val baseName = content.originalFileName?.substringBeforeLast(".")
                                        ?: "photo_${region.id}_$index"
                                    "media/$baseName.$ext"
                                }
                                ContentType.VIDEO -> {
                                    val ext = content.originalFileName?.substringAfterLast(".", "mp4") ?: "mp4"
                                    val baseName = content.originalFileName?.substringBeforeLast(".")
                                        ?: "video_${region.id}_$index"
                                    "media/$baseName.$ext"
                                }
                                ContentType.TEXT -> content.data
                                ContentType.FILE -> {
                                    val sourceFile = getFileFromUri(content.data)
                                    val ext = content.originalFileName?.substringAfterLast(".")
                                        ?: sourceFile?.extension?.ifEmpty { "bin" } ?: "bin"
                                    val baseName = content.originalFileName?.substringBeforeLast(".")
                                        ?: "file_${region.id}_$index"
                                    "media/$baseName.$ext"
                                }
                            }
                            
                            // Make path unique if it's already used
                            val relativePath = if (content.type != ContentType.TEXT) {
                                if (usedPaths.contains(baseRelativePath)) {
                                    // Add region_id to make it unique
                                    val ext = baseRelativePath.substringAfterLast(".")
                                    val pathWithoutExt = baseRelativePath.substringBeforeLast(".")
                                    "${pathWithoutExt}_r${region.id}.$ext"
                                } else {
                                    baseRelativePath
                                }.also { usedPaths.add(it) }
                            } else {
                                baseRelativePath
                            }
                            
                            android.util.Log.d("ExportManager", "Exporting content: type=${content.type}, data=${content.data}, " +
                                "originalFileName=${content.originalFileName}, relativePath=$relativePath")
                            
                            contentExportList.add(
                                ContentExportData(
                                    type = content.type.name,
                                    data = relativePath,
                                    originalFileName = content.originalFileName,
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
                    val bitmap = loadBitmapWithExifOrientation(photoFile)
                    if (bitmap != null) {
                        val photoWithAreas = drawRegionsOnBitmap(bitmap, regions, states, project.cellSize)
                        File(outputDir, "photo_with_areas.jpg").writeBytes(
                            compressBitmap(photoWithAreas, Bitmap.CompressFormat.JPEG, 90)
                        )
                        bitmap.recycle()
                        photoWithAreas.recycle()
                    }
                }
                
                // Collect content data for each region
                val regionContentsMap = mutableMapOf<Long, List<ContentExportInfo>>()
                
                for (region in regions) {
                    val safeName = region.name.replace(Regex("[^a-zA-Z0-9_-]"), "_")
                    val regionDir = File(outputDir, safeName)
                    regionDir.mkdirs()
                    
                    val contents = contentRepository.getContentsByRegionOnce(region.id)
                    val contentInfos = mutableListOf<ContentExportInfo>()
                    
                    var photoIndex = 0
                    var videoIndex = 0
                    var fileIndex = 0
                    
                    for (content in contents) {
                        when (content.type) {
                            ContentType.TEXT -> {
                                File(regionDir, "comment.txt").writeText(content.data)
                            }
                            ContentType.PHOTO -> {
                                val sourceFile = getFileFromUri(content.data)
                                if (sourceFile != null && sourceFile.exists()) {
                                    // Use original file name if available
                                    val ext = content.originalFileName?.substringAfterLast(".", "jpg") ?: "jpg"
                                    val baseName = content.originalFileName?.substringBeforeLast(".")
                                        ?: "photo_$photoIndex"
                                    val fileName = "$baseName.$ext"
                                    sourceFile.copyTo(File(regionDir, fileName), overwrite = true)
                                    contentInfos.add(ContentExportInfo("photo", "$safeName/$fileName"))
                                    photoIndex++
                                }
                            }
                            ContentType.VIDEO -> {
                                val sourceFile = getFileFromUri(content.data)
                                if (sourceFile != null && sourceFile.exists()) {
                                    // Use original file name if available
                                    val ext = content.originalFileName?.substringAfterLast(".", "mp4") ?: "mp4"
                                    val baseName = content.originalFileName?.substringBeforeLast(".")
                                        ?: "video_$videoIndex"
                                    val fileName = "$baseName.$ext"
                                    sourceFile.copyTo(File(regionDir, fileName), overwrite = true)
                                    contentInfos.add(ContentExportInfo("video", "$safeName/$fileName"))
                                    videoIndex++
                                }
                            }
                            ContentType.FILE -> {
                                val sourceFile = getFileFromUri(content.data)
                                if (sourceFile != null && sourceFile.exists()) {
                                    // Use original file name if available
                                    val ext = content.originalFileName?.substringAfterLast(".")
                                        ?: sourceFile.extension.ifEmpty { "bin" }
                                    val baseName = content.originalFileName?.substringBeforeLast(".")
                                        ?: "file_$fileIndex"
                                    val fileName = "$baseName.$ext"
                                    sourceFile.copyTo(File(regionDir, fileName), overwrite = true)
                                    contentInfos.add(ContentExportInfo("file", "${safeName}/$fileName"))
                                    fileIndex++
                                }
                            }
                        }
                    }
                    
                    regionContentsMap[region.id] = contentInfos
                }
                
                val htmlContent = generateHtmlReportWithContents(project, regions, states, regionContentsMap)
                File(outputDir, "report.html").writeText(htmlContent)
                
                true
            } catch (e: Exception) {
                e.printStackTrace()
                false
            }
        }
    }
    
    /**
     * Load bitmap with correct EXIF orientation.
     */
    private fun loadBitmapWithExifOrientation(file: File): Bitmap? {
        val bitmap = BitmapFactory.decodeFile(file.absolutePath) ?: return null
        
        try {
            val exif = ExifInterface(file.absolutePath)
            val orientation = exif.getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL
            )
            
            val rotationDegrees = when (orientation) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                else -> 0f
            }
            
            if (rotationDegrees != 0f) {
                val matrix = Matrix()
                matrix.postRotate(rotationDegrees)
                val rotatedBitmap = Bitmap.createBitmap(
                    bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true
                )
                if (rotatedBitmap != bitmap) {
                    bitmap.recycle()
                }
                return rotatedBitmap
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        
        return bitmap
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
            fillPaint.alpha = 100 // Semi-transparent (about 40% opacity)
            
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
        builder.append("<!DOCTYPE html>")
        builder.append("<html lang='en'>")
        builder.append("<head>")
        builder.append("<meta charset='UTF-8'>")
        builder.append("<meta name='viewport' content='width=device-width, initial-scale=1.0'>")
        builder.append("<meta http-equiv='X-UA-Compatible' content='IE=edge'>")
        builder.append("<title>${escapeHtml(project.name)}</title>")
        builder.append("<style>")
        // Reset and base styles
        builder.append("*{box-sizing:border-box;margin:0;padding:0;}")
        builder.append("html,body{height:100%;font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',Roboto,Helvetica,Arial,sans-serif;font-size:14px;background:#f0f2f5;color:#333;}")
        // Main container
        builder.append(".container{display:flex;height:100vh;width:100%;overflow:hidden;}")
        // Left column (1/3 width)
        builder.append(".left-column{width:33.333%;min-width:250px;max-width:50%;display:flex;flex-direction:column;border-right:1px solid #ddd;background:#fff;resize:horizontal;overflow:hidden;}")
        // Right column (2/3 width)
        builder.append(".right-column{flex:1;display:flex;flex-direction:column;background:#fafafa;min-width:300px;}")
        // Panel styles
        builder.append(".panel{display:flex;flex-direction:column;overflow:hidden;}")
        builder.append(".panel-header{padding:12px 16px;background:#1976d2;color:#fff;font-weight:600;font-size:15px;display:flex;align-items:center;justify-content:space-between;}")
        builder.append(".panel-header.light{background:#f5f5f5;color:#333;border-bottom:1px solid #ddd;}")
        builder.append(".panel-content{flex:1;overflow:auto;padding:16px;}")
        // Tree panel (Part 1) - initial size 35%, resizable
        builder.append(".tree-panel{flex:0 0 35%;min-height:100px;max-height:60%;overflow:hidden;}")
        builder.append(".tree-panel .panel-content{padding:8px;overflow:auto;}")
        // Info panel (Part 2) - takes remaining space
        builder.append(".info-panel{flex:1 1 auto;min-height:100px;overflow:hidden;}")
        // Media panel (Part 3)
        builder.append(".media-panel{flex:1;}")
        // Tree styles
        builder.append(".tree-item{padding:6px 8px;cursor:pointer;border-radius:4px;margin:2px 0;display:flex;align-items:center;gap:8px;transition:background 0.2s;}")
        builder.append(".tree-item:hover{background:#e3f2fd;}")
        builder.append(".tree-item.active{background:#bbdefb;font-weight:500;}")
        builder.append(".tree-item.project{font-weight:600;color:#1976d2;padding-left:12px;}")
        builder.append(".tree-item.region{padding-left:32px;color:#555;}")
        builder.append(".tree-icon{width:18px;height:18px;display:flex;align-items:center;justify-content:center;font-size:12px;}")
        builder.append(".tree-toggle{width:20px;height:20px;display:flex;align-items:center;justify-content:center;cursor:pointer;color:#666;border-radius:4px;}")
        builder.append(".tree-toggle:hover{background:#e0e0e0;}")
        builder.append(".tree-children{margin-left:8px;}")
        builder.append(".tree-children.collapsed{display:none;}")
        // Color indicator
        builder.append(".color-indicator{width:12px;height:12px;border-radius:50%;display:inline-block;border:1px solid rgba(0,0,0,0.2);}")
        // Info panel styles
        builder.append(".info-section{margin-bottom:16px;padding-bottom:16px;border-bottom:1px solid #eee;}")
        builder.append(".info-section:last-child{border-bottom:none;margin-bottom:0;padding-bottom:0;}")
        builder.append(".info-label{font-weight:600;color:#666;font-size:12px;text-transform:uppercase;margin-bottom:4px;}")
        builder.append(".info-value{color:#333;line-height:1.5;white-space:pre-wrap;}")
        builder.append(".info-value.empty{color:#999;font-style:italic;}")
        // Media panel styles
        builder.append(".media-grid{display:grid;grid-template-columns:repeat(auto-fill,minmax(200px,1fr));gap:16px;}")
        builder.append(".media-item{background:#fff;border-radius:8px;overflow:hidden;box-shadow:0 2px 8px rgba(0,0,0,0.1);transition:transform 0.2s,box-shadow 0.2s;}")
        builder.append(".media-item:hover{transform:translateY(-2px);box-shadow:0 4px 16px rgba(0,0,0,0.15);}")
        builder.append(".media-item img,.media-item video{width:100%;height:150px;object-fit:cover;background:#eee;display:block;}")
        builder.append(".media-item .media-caption{padding:8px 12px;font-size:12px;color:#666;}")
        builder.append(".media-item .media-type{display:inline-block;padding:2px 6px;border-radius:4px;font-size:10px;text-transform:uppercase;font-weight:600;}")
        builder.append(".media-type.photo{background:#e8f5e9;color:#2e7d32;}")
        builder.append(".media-type.video{background:#fff3e0;color:#ef6c00;}")
        // Empty state
        builder.append(".empty-state{text-align:center;padding:40px 20px;color:#999;}")
        builder.append(".empty-state-icon{font-size:48px;margin-bottom:16px;opacity:0.5;}")
        // Resizer
        builder.append(".resizer{width:5px;background:#e0e0e0;cursor:col-resize;transition:background 0.2s;}")
        builder.append(".resizer:hover,.resizer.active{background:#1976d2;}")
        // Horizontal resizer
        builder.append(".h-resizer{height:5px;background:#e0e0e0;cursor:row-resize;transition:background 0.2s;flex-shrink:0;}")
        builder.append(".h-resizer:hover,.h-resizer.active{background:#1976d2;}")
        // Project info header
        builder.append(".project-header{background:linear-gradient(135deg,#1976d2,#1565c0);color:#fff;padding:16px;border-radius:8px;margin-bottom:12px;}")
        builder.append(".project-header h1{font-size:18px;margin-bottom:4px;}")
        builder.append(".project-header .meta{font-size:12px;opacity:0.9;}")
        // Photo link
        builder.append(".photo-link{display:inline-flex;align-items:center;gap:6px;padding:8px 16px;background:#e3f2fd;color:#1976d2;border-radius:6px;text-decoration:none;font-size:13px;margin-top:12px;transition:background 0.2s;}")
        builder.append(".photo-link:hover{background:#bbdefb;}")
        // Scrollbar styling
        builder.append(".panel-content::-webkit-scrollbar{width:8px;height:8px;}")
        builder.append(".panel-content::-webkit-scrollbar-track{background:#f1f1f1;border-radius:4px;}")
        builder.append(".panel-content::-webkit-scrollbar-thumb{background:#ccc;border-radius:4px;}")
        builder.append(".panel-content::-webkit-scrollbar-thumb:hover{background:#aaa;}")
        // Footer
        builder.append(".footer{padding:12px 16px;background:#f5f5f5;border-top:1px solid #ddd;font-size:11px;color:#888;text-align:center;}")
        builder.append("</style>")
        builder.append("</head>")
        builder.append("<body>")
        builder.append("<div class='container'>")
        // Left column
        builder.append("<div class='left-column' id='leftColumn'>")
        // Part 1: Tree panel
        builder.append("<div class='panel tree-panel'>")
        builder.append("<div class='panel-header light'><span>Projects & Regions</span><span id='regionCount'>${regions.size}</span></div>")
        builder.append("<div class='panel-content' id='treeContent'>")
        // Tree structure
        builder.append("<div class='tree-item project' onclick='selectProject()'>")
        builder.append("<span class='tree-icon'>📁</span>")
        builder.append("<span>${escapeHtml(project.name)}</span>")
        builder.append("</div>")
        builder.append("<div class='tree-children' id='regionsList'>")
        for (region in regions) {
            val state = region.stateId?.let { stateId -> states.find { it.id == stateId } }
            val colorHex = if (state != null) "#${Integer.toHexString(state.color).substring(2).uppercase()}" else "#9e9e9e"
            builder.append("<div class='tree-item region' data-region-id='${region.id}' onclick='selectRegion(${region.id})'>")
            if (state != null) {
                builder.append("<span class='color-indicator' style='background-color:$colorHex;'></span>")
            } else {
                builder.append("<span class='color-indicator' style='background-color:#9e9e9e;'></span>")
            }
            builder.append("<span>${escapeHtml(region.name)}</span>")
            builder.append("</div>")
        }
        builder.append("</div>")
        builder.append("</div>")
        builder.append("</div>")
        // Horizontal resizer
        builder.append("<div class='h-resizer' id='hResizer'></div>")
        // Part 2: Info panel
        builder.append("<div class='panel info-panel'>")
        builder.append("<div class='panel-header light'>Region Details</div>")
        builder.append("<div class='panel-content' id='infoContent'>")
        // Project info by default
        builder.append("<div class='project-header'>")
        builder.append("<h1>${escapeHtml(project.name)}</h1>")
        builder.append("<div class='meta'>${regions.size} regions</div>")
        builder.append("</div>")
        if (!project.description.isNullOrBlank()) {
            builder.append("<div class='info-section'><div class='info-label'>Description</div><div class='info-value'>${escapeHtml(project.description)}</div></div>")
        }
        if (!project.type1.isNullOrBlank()) {
            builder.append("<div class='info-section'><div class='info-label'>Type 1</div><div class='info-value'>${escapeHtml(project.type1)}</div></div>")
        }
        if (!project.type2.isNullOrBlank()) {
            builder.append("<div class='info-section'><div class='info-label'>Type 2</div><div class='info-value'>${escapeHtml(project.type2)}</div></div>")
        }
        if (!project.note.isNullOrBlank()) {
            builder.append("<div class='info-section'><div class='info-label'>Note</div><div class='info-value'>${escapeHtml(project.note)}</div></div>")
        }
        builder.append("<a href='photo_with_areas.jpg' class='photo-link' target='_blank'>📷 View photo with areas</a>")
        builder.append("</div>")
        builder.append("</div>")
        builder.append("</div>")
        // Vertical resizer
        builder.append("<div class='resizer' id='vResizer'></div>")
        // Right column (Part 3: Media panel)
        builder.append("<div class='right-column'>")
        builder.append("<div class='panel media-panel'>")
        builder.append("<div class='panel-header'><span>Media Content</span><span id='mediaCount'>0 items</span></div>")
        builder.append("<div class='panel-content' id='mediaContent'>")
        builder.append("<div class='empty-state'><div class='empty-state-icon'>📷</div><div>Select a region to view media content</div></div>")
        builder.append("</div>")
        builder.append("</div>")
        builder.append("<div class='footer'>Generated on ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())}</div>")
        builder.append("</div>")
        builder.append("</div>")
        // JavaScript
        builder.append("<script>")
        // Region data
        builder.append("const regionsData = {")
        for (region in regions) {
            val state = region.stateId?.let { stateId -> states.find { it.id == stateId } }
            val safeName = region.name.replace(Regex("[^a-zA-Z0-9_-]"), "_")
            val colorHex = if (state != null) "#${Integer.toHexString(state.color).substring(2).uppercase()}" else "#9e9e9e"
            builder.append("'${region.id}':{")
            builder.append("name:'${escapeJs(region.name)}',")
            builder.append("stateName:'${escapeJs(state?.name ?: "N/A")}',")
            builder.append("stateColor:'$colorHex',")
            builder.append("type1:'${escapeJs(region.type1 ?: "")}',")
            builder.append("type2:'${escapeJs(region.type2 ?: "")}',")
            builder.append("description:'${escapeJs(region.description ?: "")}',")
            builder.append("note:'${escapeJs(region.note ?: "")}',")
            builder.append("folder:'$safeName',")
            builder.append("contents:[")
            // Contents will be populated separately
            builder.append("]")
            builder.append("},")
        }
        builder.append("};")
        // Select project
        builder.append("function selectProject(){document.querySelectorAll('.tree-item').forEach(i=>i.classList.remove('active'));document.querySelector('.tree-item.project').classList.add('active');}")
        // Select region
        builder.append("function selectRegion(id){")
        builder.append("document.querySelectorAll('.tree-item').forEach(i=>i.classList.remove('active'));")
        builder.append("document.querySelector('.tree-item.region[data-region-id=\"'+id+'\"]').classList.add('active');")
        builder.append("const r=regionsData[id];")
        builder.append("if(!r)return;")
        // Update info panel
        builder.append("const infoContent=document.getElementById('infoContent');")
        builder.append("infoContent.innerHTML=")
        builder.append("'<div class=\\'info-section\\'>'")
        builder.append("+'<div class=\\'info-label\\'>Region Name</div>'")
        builder.append("+'<div class=\\'info-value\\' style=\\'font-size:18px;font-weight:600;\\'>'+r.name+'</div>'")
        builder.append("+'</div>'")
        builder.append("+'<div class=\\'info-section\\'>'")
        builder.append("+'<div class=\\'info-label\\'>State</div>'")
        builder.append("+'<div class=\\'info-value\\'><span class=\\'color-indicator\\' style=\\'background-color:'+r.stateColor+';margin-right:8px;\\'></span>'+r.stateName+'</div>'")
        builder.append("+'</div>'")
        builder.append("+(r.type1?'<div class=\\'info-section\\'><div class=\\'info-label\\'>Type 1</div><div class=\\'info-value\\'>'+r.type1+'</div></div>':'')")
        builder.append("+(r.type2?'<div class=\\'info-section\\'><div class=\\'info-label\\'>Type 2</div><div class=\\'info-value\\'>'+r.type2+'</div></div>':'')")
        builder.append("+(r.description?'<div class=\\'info-section\\'><div class=\\'info-label\\'>Description</div><div class=\\'info-value\\'>'+r.description+'</div></div>':'')")
        builder.append("+(r.note?'<div class=\\'info-section\\'><div class=\\'info-label\\'>Note</div><div class=\\'info-value\\'>'+r.note+'</div></div>':'');")
        // Update media panel
        builder.append("updateMediaPanel(r);")
        builder.append("}")
        // Update media panel
        builder.append("function updateMediaPanel(region){")
        builder.append("const mediaContent=document.getElementById('mediaContent');")
        builder.append("const contents=region.contents||[];")
        builder.append("document.getElementById('mediaCount').textContent=contents.length+' items';")
        builder.append("if(contents.length===0){")
        builder.append("mediaContent.innerHTML='<div class=\\'empty-state\\'><div class=\\'empty-state-icon\\'>📷</div><div>No media content for this region</div></div>';")
        builder.append("return;")
        builder.append("}")
        builder.append("let html='<div class=\\'media-grid\\'>';")
        builder.append("contents.forEach(function(item){")
        builder.append("if(item.type==='photo'){")
        builder.append("html+='<div class=\\'media-item\\'><a href=\\''+item.path+'\\' target=\\'_blank\\'><img src=\\''+item.path+'\\' alt=\\'Photo\\'></a><div class=\\'media-caption\\'><span class=\\'media-type photo\\'>Photo</span></div></div>';")
        builder.append("}else if(item.type==='video'){")
        builder.append("html+='<div class=\\'media-item\\'><video controls preload=\\'metadata\\'><source src=\\''+item.path+'\\' type=\\'video/mp4\\'></video><div class=\\'media-caption\\'><span class=\\'media-type video\\'>Video</span></div></div>';")
        builder.append("}")
        builder.append("});")
        builder.append("html+='</div>';")
        builder.append("mediaContent.innerHTML=html;")
        builder.append("}")
        // Vertical resizer
        builder.append("(function(){")
        builder.append("const resizer=document.getElementById('vResizer');")
        builder.append("const leftColumn=document.getElementById('leftColumn');")
        builder.append("let isResizing=false;")
        builder.append("resizer.addEventListener('mousedown',function(e){isResizing=true;resizer.classList.add('active');});")
        builder.append("document.addEventListener('mousemove',function(e){if(!isResizing)return;const containerWidth=document.querySelector('.container').offsetWidth;const newWidth=e.clientX;leftColumn.style.width=newWidth+'px';});")
        builder.append("document.addEventListener('mouseup',function(){isResizing=false;resizer.classList.remove('active');});")
        builder.append("})();")
        // Horizontal resizer
        builder.append("(function(){")
        builder.append("const resizer=document.getElementById('hResizer');")
        builder.append("const treePanel=document.querySelector('.tree-panel');")
        builder.append("const infoPanel=document.querySelector('.info-panel');")
        builder.append("let isResizing=false;")
        builder.append("resizer.addEventListener('mousedown',function(e){isResizing=true;e.preventDefault();resizer.classList.add('active');});")
        builder.append("document.addEventListener('mousemove',function(e){if(!isResizing)return;const containerRect=document.getElementById('leftColumn').getBoundingClientRect();const relY=e.clientY-containerRect.top;const containerHeight=containerRect.height;const newHeight=Math.max(100,Math.min(relY,containerHeight-150));treePanel.style.flex='0 0 '+newHeight+'px';});")
        builder.append("document.addEventListener('mouseup',function(){isResizing=false;resizer.classList.remove('active');});")
        builder.append("})();")
        builder.append("</script>")
        builder.append("</body>")
        builder.append("</html>")
        return builder.toString()
    }
    
    private fun escapeJs(text: String): String {
        return text
            .replace("\\", "\\\\")
            .replace("'", "\\'")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t")
    }
    
    private fun generateHtmlReportWithContents(
        project: Project,
        regions: List<Region>,
        states: List<State>,
        regionContentsMap: Map<Long, List<ContentExportInfo>>,
        languageCode: String = "ru"
    ): String {
        val t = HtmlTranslations.forLanguage(languageCode)
        val builder = StringBuilder()
        builder.append("<!DOCTYPE html>")
        builder.append("<html lang='${t.htmlLang}'>")
        builder.append("<head>")
        builder.append("<meta charset='UTF-8'>")
        builder.append("<meta name='viewport' content='width=device-width, initial-scale=1.0'>")
        builder.append("<meta http-equiv='X-UA-Compatible' content='IE=edge'>")
        builder.append("<title>${escapeHtml(project.name)}</title>")
        builder.append("<style>")
        // Reset and base styles
        builder.append("*{box-sizing:border-box;margin:0;padding:0;}")
        builder.append("html,body{height:100%;font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',Roboto,Helvetica,Arial,sans-serif;font-size:14px;background:#f0f2f5;color:#333;}")
        // Main container
        builder.append(".container{display:flex;height:100vh;width:100%;overflow:hidden;}")
        // Left column (1/3 width)
        builder.append(".left-column{width:33.333%;min-width:250px;max-width:50%;display:flex;flex-direction:column;border-right:1px solid #ddd;background:#fff;overflow:hidden;}")
        // Right column (2/3 width)
        builder.append(".right-column{flex:1;display:flex;flex-direction:column;background:#fafafa;min-width:300px;}")
        // Panel styles
        builder.append(".panel{display:flex;flex-direction:column;overflow:hidden;}")
        builder.append(".panel-header{padding:12px 16px;background:#1976d2;color:#fff;font-weight:600;font-size:15px;display:flex;align-items:center;justify-content:space-between;flex-shrink:0;}")
        builder.append(".panel-header.light{background:#f5f5f5;color:#333;border-bottom:1px solid #ddd;}")
        builder.append(".panel-content{flex:1;overflow:auto;padding:16px;}")
        // Tree panel (Part 1) - initial size 35%, resizable
        builder.append(".tree-panel{flex:0 0 35%;min-height:100px;max-height:60%;overflow:hidden;}")
        builder.append(".tree-panel .panel-content{padding:8px;overflow:auto;}")
        // Info panel (Part 2) - takes remaining space
        builder.append(".info-panel{flex:1 1 auto;min-height:100px;overflow:hidden;}")
        // Media panel (Part 3)
        builder.append(".media-panel{flex:1;}")
        // Tree styles
        builder.append(".tree-item{padding:6px 8px;cursor:pointer;border-radius:4px;margin:2px 0;display:flex;align-items:center;gap:8px;transition:background 0.2s;user-select:none;}")
        builder.append(".tree-item:hover{background:#e3f2fd;}")
        builder.append(".tree-item.active{background:#bbdefb;font-weight:500;}")
        builder.append(".tree-item.project{font-weight:600;color:#1976d2;padding-left:12px;}")
        builder.append(".tree-item.region{padding-left:32px;color:#555;}")
        builder.append(".tree-icon{width:18px;height:18px;display:flex;align-items:center;justify-content:center;font-size:12px;}")
        builder.append(".tree-toggle{width:20px;height:20px;display:flex;align-items:center;justify-content:center;cursor:pointer;color:#666;border-radius:4px;}")
        builder.append(".tree-toggle:hover{background:#e0e0e0;}")
        builder.append(".tree-children{margin-left:8px;}")
        builder.append(".tree-children.collapsed{display:none;}")
        // Color indicator
        builder.append(".color-indicator{width:12px;height:12px;border-radius:50%;display:inline-block;border:1px solid rgba(0,0,0,0.2);flex-shrink:0;}")
        // Info panel styles
        builder.append(".info-section{margin-bottom:16px;padding-bottom:16px;border-bottom:1px solid #eee;}")
        builder.append(".info-section:last-child{border-bottom:none;margin-bottom:0;padding-bottom:0;}")
        builder.append(".info-label{font-weight:600;color:#666;font-size:12px;text-transform:uppercase;margin-bottom:4px;}")
        builder.append(".info-value{color:#333;line-height:1.5;word-wrap:break-word;white-space:pre-wrap;}")
        builder.append(".info-value.empty{color:#999;font-style:italic;}")
        // Media panel styles
        builder.append(".media-grid{display:grid;grid-template-columns:repeat(auto-fill,minmax(200px,1fr));gap:16px;}")
        builder.append(".media-item{background:#fff;border-radius:8px;overflow:hidden;box-shadow:0 2px 8px rgba(0,0,0,0.1);transition:transform 0.2s,box-shadow 0.2s;cursor:pointer;}")
        builder.append(".media-item:hover{transform:translateY(-2px);box-shadow:0 4px 16px rgba(0,0,0,0.15);}")
        builder.append(".media-item img,.media-item video{width:100%;height:150px;object-fit:cover;background:#eee;display:block;}")
        builder.append(".media-item .media-caption{padding:8px 12px;font-size:12px;color:#666;}")
        builder.append(".media-item .media-type{display:inline-block;padding:2px 6px;border-radius:4px;font-size:10px;text-transform:uppercase;font-weight:600;}")
        builder.append(".media-type.photo{background:#e8f5e9;color:#2e7d32;}")
        builder.append(".media-type.video{background:#fff3e0;color:#ef6c00;}")
        builder.append(".media-type.file{background:#e3f2fd;color:#1976d2;}")
        builder.append(".media-item .file-icon{width:100%;height:150px;display:flex;align-items:center;justify-content:center;background:#f5f5f5;font-size:48px;}")
        builder.append(".media-item .file-name{padding:8px 12px;font-size:11px;color:#333;word-break:break-all;max-height:40px;overflow:hidden;}")
        // Media viewer (fullscreen in panel 3)
        builder.append(".media-viewer{display:none;flex-direction:column;height:100%;background:#1a1a1a;}")
        builder.append(".media-viewer.active{display:flex;}")
        builder.append(".viewer-header{display:flex;align-items:center;gap:12px;padding:12px 16px;background:#2a2a2a;color:#fff;flex-shrink:0;}")
        builder.append(".viewer-title{font-weight:500;font-size:14px;flex:1;overflow:hidden;text-overflow:ellipsis;white-space:nowrap;}")
        builder.append(".viewer-nav{display:flex;gap:8px;}")
        builder.append(".viewer-btn{background:rgba(255,255,255,0.1);border:none;color:#fff;padding:8px 16px;border-radius:6px;cursor:pointer;font-size:13px;transition:background 0.2s;display:flex;align-items:center;gap:6px;}")
        builder.append(".viewer-btn:hover{background:rgba(255,255,255,0.2);}")
        builder.append(".viewer-btn:disabled{opacity:0.5;cursor:not-allowed;}")
        builder.append(".viewer-content{flex:1;display:flex;align-items:center;justify-content:center;padding:16px;overflow:auto;}")
        builder.append(".viewer-content img{max-width:100%;max-height:100%;object-fit:contain;border-radius:4px;transform-origin:center;transition:transform 0.15s ease-out;cursor:grab;}")
        builder.append(".viewer-content img.zoomed{cursor:move;}")
        builder.append(".viewer-content video{max-width:100%;max-height:100%;border-radius:4px;}")
        builder.append(".viewer-footer{display:flex;align-items:center;justify-content:center;gap:16px;padding:12px 16px;background:#2a2a2a;flex-shrink:0;}")
        builder.append(".viewer-counter{color:#aaa;font-size:13px;}")
        // Zoom controls - same style as viewer-btn
        builder.append(".zoom-controls{display:flex;align-items:center;gap:4px;}")
        builder.append(".zoom-btn{background:rgba(255,255,255,0.1);border:none;color:#fff;padding:8px 14px;border-radius:6px;cursor:pointer;font-size:16px;font-weight:bold;transition:background 0.2s;}")
        builder.append(".zoom-btn:hover{background:rgba(255,255,255,0.2);}")
        builder.append(".zoom-level{color:#fff;font-size:13px;min-width:50px;text-align:center;}")
        // Empty state
        builder.append(".empty-state{text-align:center;padding:40px 20px;color:#999;}")
        builder.append(".empty-state-icon{font-size:48px;margin-bottom:16px;opacity:0.5;}")
        // Resizer
        builder.append(".resizer{width:5px;background:#e0e0e0;cursor:col-resize;transition:background 0.2s;flex-shrink:0;}")
        builder.append(".resizer:hover,.resizer.active{background:#1976d2;}")
        // Horizontal resizer
        builder.append(".h-resizer{height:5px;background:#e0e0e0;cursor:row-resize;transition:background 0.2s;flex-shrink:0;}")
        builder.append(".h-resizer:hover,.h-resizer.active{background:#1976d2;}")
        // Project info header
        builder.append(".project-header{background:linear-gradient(135deg,#1976d2,#1565c0);color:#fff;padding:16px;border-radius:8px;margin-bottom:12px;}")
        builder.append(".project-header h1{font-size:18px;margin-bottom:4px;}")
        builder.append(".project-header .meta{font-size:12px;opacity:0.9;}")
        // Photo link
        builder.append(".photo-link{display:inline-flex;align-items:center;gap:6px;padding:8px 16px;background:#e3f2fd;color:#1976d2;border-radius:6px;text-decoration:none;font-size:13px;margin-top:12px;transition:background 0.2s;cursor:pointer;}")
        builder.append(".photo-link:hover{background:#bbdefb;}")
        // Scrollbar styling (cross-browser)
        builder.append(".panel-content{scrollbar-width:thin;scrollbar-color:#ccc #f1f1f1;}")
        builder.append(".panel-content::-webkit-scrollbar{width:8px;height:8px;}")
        builder.append(".panel-content::-webkit-scrollbar-track{background:#f1f1f1;border-radius:4px;}")
        builder.append(".panel-content::-webkit-scrollbar-thumb{background:#ccc;border-radius:4px;}")
        builder.append(".panel-content::-webkit-scrollbar-thumb:hover{background:#aaa;}")
        // Footer
        builder.append(".footer{padding:12px 16px;background:#f5f5f5;border-top:1px solid #ddd;font-size:11px;color:#888;text-align:center;flex-shrink:0;}")
        // Region badge
        builder.append(".region-badge{display:inline-block;padding:2px 8px;border-radius:12px;font-size:11px;background:#e0e0e0;color:#666;margin-left:auto;}")
        // Filter controls
        builder.append(".filter-container{padding:8px;background:#f5f5f5;border-bottom:1px solid #e0e0e0;}")
        builder.append(".filter-label{font-size:11px;color:#666;margin-bottom:4px;display:block;}")
        builder.append(".filter-select{width:100%;padding:6px 8px;border:1px solid #ddd;border-radius:4px;font-size:13px;background:#fff;cursor:pointer;}")
        builder.append(".filter-select:focus{outline:none;border-color:#1976d2;}")
        builder.append("</style>")
        builder.append("</head>")
        builder.append("<body>")
        builder.append("<div class='container'>")
        // Left column
        builder.append("<div class='left-column' id='leftColumn'>")
        // Part 1: Tree panel
        builder.append("<div class='panel tree-panel' id='treePanel'>")
        builder.append("<div class='panel-header light'><span>${t.projectsAndRegions}</span><span id='regionCount'>${regions.size} ${t.regions}</span></div>")
        // Filter by state
        builder.append("<div class='filter-container'>")
        builder.append("<label class='filter-label'>${t.filterByState}</label>")
        builder.append("<select class='filter-select' id='stateFilter' onchange='filterByState(this.value)'>")
        builder.append("<option value=''>${t.allStates}</option>")
        for (state in states) {
            builder.append("<option value='${state.id}'>${escapeHtml(state.name)}</option>")
        }
        builder.append("</select>")
        builder.append("</div>")
        builder.append("<div class='panel-content' id='treeContent'>")
        // Tree structure
        builder.append("<div class='tree-item project active' onclick='selectProject()'>")
        builder.append("<span class='tree-icon'>📁</span>")
        builder.append("<span>${escapeHtml(project.name)}</span>")
        builder.append("</div>")
        builder.append("<div class='tree-children' id='regionsList'>")
        for (region in regions) {
            val state = region.stateId?.let { stateId -> states.find { it.id == stateId } }
            val colorHex = if (state != null) "#${Integer.toHexString(state.color).substring(2).uppercase()}" else "#9e9e9e"
            val contentCount = regionContentsMap[region.id]?.size ?: 0
            val stateIdValue = region.stateId?.toString() ?: ""
            builder.append("<div class='tree-item region' data-region-id='${region.id}' data-state-id='$stateIdValue' onclick='selectRegion(${region.id})'>")
            builder.append("<span class='color-indicator' style='background-color:$colorHex;'></span>")
            builder.append("<span style='flex:1;overflow:hidden;text-overflow:ellipsis;white-space:nowrap;'>${escapeHtml(region.name)}</span>")
            if (contentCount > 0) {
                builder.append("<span class='region-badge'>$contentCount</span>")
            }
            builder.append("</div>")
        }
        builder.append("</div>")
        builder.append("</div>")
        builder.append("</div>")
        // Horizontal resizer
        builder.append("<div class='h-resizer' id='hResizer'></div>")
        // Part 2: Info panel
        builder.append("<div class='panel info-panel' id='infoPanel'>")
        builder.append("<div class='panel-header light'>${t.regionDetails}</div>")
        builder.append("<div class='panel-content' id='infoContent'>")
        // Project info by default
        builder.append("<div class='project-header'>")
        builder.append("<h1>${escapeHtml(project.name)}</h1>")
        builder.append("<div class='meta'>${regions.size} ${t.regions}</div>")
        builder.append("</div>")
        if (!project.description.isNullOrBlank()) {
            builder.append("<div class='info-section'><div class='info-label'>${t.description}</div><div class='info-value'>${escapeHtml(project.description)}</div></div>")
        }
        if (!project.type1.isNullOrBlank()) {
            builder.append("<div class='info-section'><div class='info-label'>${t.type1}</div><div class='info-value'>${escapeHtml(project.type1)}</div></div>")
        }
        if (!project.type2.isNullOrBlank()) {
            builder.append("<div class='info-section'><div class='info-label'>${t.type2}</div><div class='info-value'>${escapeHtml(project.type2)}</div></div>")
        }
        if (!project.note.isNullOrBlank()) {
            builder.append("<div class='info-section'><div class='info-label'>${t.note}</div><div class='info-value'>${escapeHtml(project.note)}</div></div>")
        }
        builder.append("<span class='photo-link' onclick='showPhotoWithAreas()'>${t.viewPhotoWithAreas}</span>")
        builder.append("</div>")
        builder.append("</div>")
        builder.append("</div>")
        // Vertical resizer
        builder.append("<div class='resizer' id='vResizer'></div>")
        // Right column (Part 3: Media panel)
        builder.append("<div class='right-column'>")
        builder.append("<div class='panel media-panel'>")
        builder.append("<div class='panel-header'><span>${t.mediaContent}</span><span id='mediaCount'>0 ${t.items}</span></div>")
        // Media grid
        builder.append("<div class='panel-content' id='mediaContent'>")
        builder.append("<div class='empty-state'><div class='empty-state-icon'>📷</div><div>${t.selectRegionToViewMedia}</div></div>")
        builder.append("</div>")
        // Media viewer (hidden by default)
        builder.append("<div class='media-viewer' id='mediaViewer'>")
        builder.append("<div class='viewer-header'>")
        // Left side: filename
        builder.append("<span class='viewer-title' id='viewerTitle'>${t.photo}</span>")
        // Download button
        builder.append("<button class='viewer-btn' id='btnDownload' onclick='downloadCurrentMedia()' style='margin-left:12px;'>${t.download}</button>")
        // Zoom controls in header
        builder.append("<div class='zoom-controls'>")
        builder.append("<button class='zoom-btn' onclick='zoomOut()' title='Zoom Out'>${t.zoomOut}</button>")
        builder.append("<span class='zoom-level' id='zoomLevel'>100%</span>")
        builder.append("<button class='zoom-btn' onclick='zoomIn()' title='Zoom In'>${t.zoomIn}</button>")
        builder.append("<button class='zoom-btn' onclick='resetZoom()' title='Reset'>${t.resetZoom}</button>")
        builder.append("</div>")
        // Right side: navigation buttons
        builder.append("<div class='viewer-nav'>")
        builder.append("<button class='viewer-btn' id='btnPrev' onclick='navigateMedia(-1)'>${t.prev}</button>")
        builder.append("<button class='viewer-btn' id='btnNext' onclick='navigateMedia(1)'>${t.next}</button>")
        builder.append("<button class='viewer-btn' onclick='closeViewer()'>${t.backToGrid}</button>")
        builder.append("</div>")
        builder.append("</div>")
        builder.append("<div class='viewer-content' id='viewerContent'></div>")
        // Footer with counter only
        builder.append("<div class='viewer-footer'>")
        builder.append("<span class='viewer-counter' id='viewerCounter'>1 / 1</span>")
        builder.append("</div>")
        builder.append("</div>")
        builder.append("</div>")
        builder.append("<div class='footer'>Generated on ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())}</div>")
        builder.append("</div>")
        builder.append("</div>")
        // JavaScript
        builder.append("<script>")
        // State variables
        builder.append("let currentRegion=null;")
        builder.append("let currentMediaIndex=0;")
        builder.append("let currentMediaList=[];")
        // Region data with contents
        builder.append("const regionsData = {")
        for (region in regions) {
            val state = region.stateId?.let { stateId -> states.find { it.id == stateId } }
            val safeName = region.name.replace(Regex("[^a-zA-Z0-9_-]"), "_")
            val colorHex = if (state != null) "#${Integer.toHexString(state.color).substring(2).uppercase()}" else "#9e9e9e"
            builder.append("'${region.id}':{")
            builder.append("name:'${escapeJs(region.name)}',")
            builder.append("stateName:'${escapeJs(state?.name ?: "N/A")}',")
            builder.append("stateColor:'$colorHex',")
            builder.append("type1:'${escapeJs(region.type1 ?: "")}',")
            builder.append("type2:'${escapeJs(region.type2 ?: "")}',")
            builder.append("description:'${escapeJs(region.description ?: "")}',")
            builder.append("note:'${escapeJs(region.note ?: "")}',")
            builder.append("folder:'$safeName',")
            builder.append("contents:[")
            val contents = regionContentsMap[region.id] ?: emptyList()
            for (content in contents) {
                builder.append("{type:'${content.type}',path:'${content.path}'},")
            }
            builder.append("]")
            builder.append("},")
        }
        builder.append("};")
        // Select project
        builder.append("function selectProject(){")
        builder.append("document.querySelectorAll('.tree-item').forEach(i=>i.classList.remove('active'));")
        builder.append("document.querySelector('.tree-item.project').classList.add('active');")
        builder.append("showProjectInfo();")
        builder.append("}")
        // Show project info
        builder.append("function showProjectInfo(){")
        builder.append("currentRegion=null;")
        builder.append("closeViewer();")
        builder.append("const infoContent=document.getElementById('infoContent');")
        builder.append("infoContent.innerHTML=")
        builder.append("'<div class=\\'project-header\\'>'")
        builder.append("+'<h1>${escapeJs(project.name)}</h1>'")
        builder.append("+'<div class=\\'meta\\'>${regions.size} ${t.regions}</div>'")
        builder.append("+'</div>'")
        if (!project.description.isNullOrBlank()) {
            builder.append("+'<div class=\\'info-section\\'><div class=\\'info-label\\'>${t.description}</div><div class=\\'info-value\\'>${escapeJs(project.description)}</div></div>'")
        }
        if (!project.type1.isNullOrBlank()) {
            builder.append("+'<div class=\\'info-section\\'><div class=\\'info-label\\'>${t.type1}</div><div class=\\'info-value\\'>${escapeJs(project.type1)}</div></div>'")
        }
        if (!project.type2.isNullOrBlank()) {
            builder.append("+'<div class=\\'info-section\\'><div class=\\'info-label\\'>${t.type2}</div><div class=\\'info-value\\'>${escapeJs(project.type2)}</div></div>'")
        }
        if (!project.note.isNullOrBlank()) {
            builder.append("+'<div class=\\'info-section\\'><div class=\\'info-label\\'>${t.note}</div><div class=\\'info-value\\'>${escapeJs(project.note)}</div></div>'")
        }
        builder.append("+'<span class=\\'photo-link\\' onclick=\\'showPhotoWithAreas()\\'>${t.viewPhotoWithAreas}</span>';")
        builder.append("document.getElementById('mediaContent').innerHTML='<div class=\\'empty-state\\'><div class=\\'empty-state-icon\\'>📷</div><div>${t.selectRegionToViewMedia}</div></div>';")
        builder.append("document.getElementById('mediaCount').textContent='0 ${t.items}';")
        builder.append("}")
        // Show photo with areas
        builder.append("function showPhotoWithAreas(){")
        builder.append("currentMediaList=[{type:'photo',path:'photo_with_areas.jpg'}];")
        builder.append("currentMediaIndex=0;")
        builder.append("openViewer();")
        builder.append("document.getElementById('viewerTitle').textContent='Photo with Areas';")
        builder.append("}")
        // Select region
        builder.append("function selectRegion(id){")
        builder.append("document.querySelectorAll('.tree-item').forEach(i=>i.classList.remove('active'));")
        builder.append("const regionEl=document.querySelector('.tree-item.region[data-region-id=\"'+id+'\"]');")
        builder.append("if(regionEl)regionEl.classList.add('active');")
        builder.append("const r=regionsData[id];")
        builder.append("if(!r)return;")
        builder.append("currentRegion=r;")
        builder.append("closeViewer();")
        // Update info panel
        builder.append("const infoContent=document.getElementById('infoContent');")
        builder.append("let infoHtml=")
        builder.append("'<div class=\\'info-section\\'>'")
        builder.append("+'<div class=\\'info-label\\'>${t.regionName}</div>'")
        builder.append("+'<div class=\\'info-value\\' style=\\'font-size:18px;font-weight:600;\\'>'+r.name+'</div>'")
        builder.append("+'</div>'")
        builder.append("+'<div class=\\'info-section\\'>'")
        builder.append("+'<div class=\\'info-label\\'>${t.state}</div>'")
        builder.append("+'<div class=\\'info-value\\'><span class=\\'color-indicator\\' style=\\'background-color:'+r.stateColor+';margin-right:8px;\\'></span>'+r.stateName+'</div>'")
        builder.append("+'</div>'")
        builder.append("+(r.type1?'<div class=\\'info-section\\'><div class=\\'info-label\\'>${t.type1}</div><div class=\\'info-value\\'>'+r.type1+'</div></div>':'')")
        builder.append("+(r.type2?'<div class=\\'info-section\\'><div class=\\'info-label\\'>${t.type2}</div><div class=\\'info-value\\'>'+r.type2+'</div></div>':'')")
        builder.append("+(r.description?'<div class=\\'info-section\\'><div class=\\'info-label\\'>${t.description}</div><div class=\\'info-value\\'>'+r.description+'</div></div>':'')")
        builder.append("+(r.note?'<div class=\\'info-section\\'><div class=\\'info-label\\'>${t.note}</div><div class=\\'info-value\\'>'+r.note+'</div></div>':'');")
        // Add files section in info panel
        builder.append("const files=(r.contents||[]).filter(c=>c.type==='file');")
        builder.append("if(files.length>0){")
        builder.append("infoHtml+='<div class=\\'info-section\\'><div class=\\'info-label\\'>${t.files}</div>';")
        builder.append("files.forEach(function(f){")
        builder.append("const fName=f.path.split('/').pop();")
        builder.append("infoHtml+='<div style=\\'margin:4px 0;\\'><a href=\\''+f.path+'\\' target=\\'_blank\\' style=\\'color:#1976d2;text-decoration:none;\\'>📄 '+fName+'</a>';")
        builder.append("infoHtml+=' <a href=\\''+f.path+'\\' download=\\''+fName+'\\' style=\\'color:#666;font-size:11px;margin-left:8px;\\'>⬇</a></div>';")
        builder.append("});")
        builder.append("infoHtml+='</div>';")
        builder.append("}")
        builder.append("infoContent.innerHTML=infoHtml;")
        // Update media panel
        builder.append("updateMediaPanel(r);")
        builder.append("}")
        // Update media panel
        builder.append("function updateMediaPanel(region){")
        builder.append("const mediaContent=document.getElementById('mediaContent');")
        builder.append("const mediaOnly=(region.contents||[]).filter(c=>c.type==='photo'||c.type==='video');");
        builder.append("currentMediaList=mediaOnly;")
        builder.append("document.getElementById('mediaCount').textContent=currentMediaList.length+' ${t.item}'+(currentMediaList.length!==1?'s':'');")
        builder.append("if(currentMediaList.length===0){")
        builder.append("mediaContent.innerHTML='<div class=\\'empty-state\\'><div class=\\'empty-state-icon\\'>📷</div><div>${t.noMediaForRegion}</div></div>';")
        builder.append("return;")
        builder.append("}")
        builder.append("let html='<div class=\\'media-grid\\'>';")
        builder.append("currentMediaList.forEach(function(item,index){")
        builder.append("if(item.type==='photo'){")
        builder.append("html+='<div class=\\'media-item\\' onclick=\\'openMediaItem('+index+')\\'><img src=\\''+item.path+'\\' alt=\\'Photo\\' loading=\\'lazy\\'><div class=\\'media-caption\\'><span class=\\'media-type photo\\'>${t.photo}</span></div></div>';")
        builder.append("}else if(item.type==='video'){")
        builder.append("html+='<div class=\\'media-item\\' onclick=\\'openMediaItem('+index+')\\'><video src=\\''+item.path+'\\' preload=\\'metadata\\' muted></video><div class=\\'media-caption\\'><span class=\\'media-type video\\'>${t.video}</span></div></div>';")
        builder.append("}")
        builder.append("});")
        builder.append("html+='</div>';")
        builder.append("mediaContent.innerHTML=html;")
        builder.append("}")
        // Open media item in viewer
        builder.append("function openMediaItem(index){")
        builder.append("currentMediaIndex=index;")
        builder.append("openViewer();")
        builder.append("}")
        // Open viewer
        builder.append("function openViewer(){")
        builder.append("if(currentMediaList.length===0)return;")
        builder.append("document.getElementById('mediaContent').style.display='none';")
        builder.append("document.getElementById('mediaViewer').classList.add('active');")
        builder.append("resetZoom();")
        builder.append("updateViewerContent();")
        builder.append("}")
        // Close viewer
        builder.append("function closeViewer(){")
        builder.append("document.getElementById('mediaViewer').classList.remove('active');")
        builder.append("document.getElementById('mediaContent').style.display='block';")
        builder.append("resetZoom();")
        builder.append("}")
        // Update viewer content
        builder.append("function updateViewerContent(){")
        builder.append("if(currentMediaList.length===0)return;")
        builder.append("const item=currentMediaList[currentMediaIndex];")
        builder.append("const viewerContent=document.getElementById('viewerContent');")
        builder.append("const viewerTitle=document.getElementById('viewerTitle');")
        builder.append("const viewerCounter=document.getElementById('viewerCounter');")
        builder.append("const zoomControls=document.querySelector('.zoom-controls');")
        builder.append("viewerCounter.textContent=(currentMediaIndex+1)+' / '+currentMediaList.length;")
        // Extract filename from path
        builder.append("const fileName=item.path.split('/').pop();")
        builder.append("if(item.type==='photo'){")
        builder.append("viewerContent.innerHTML='<img src=\\''+item.path+'\\' alt=\\'Photo\\'>';")
        builder.append("viewerTitle.textContent=fileName;")
        builder.append("if(zoomControls)zoomControls.style.display='flex';")
        builder.append("}else if(item.type==='video'){")
        builder.append("viewerContent.innerHTML='<video controls autoplay><source src=\\''+item.path+'\\' type=\\'video/mp4\\'>Your browser does not support video.</video>';")
        builder.append("viewerTitle.textContent=fileName;")
        builder.append("if(zoomControls)zoomControls.style.display='none';")
        builder.append("}")
        builder.append("document.getElementById('btnPrev').disabled=currentMediaIndex===0;")
        builder.append("document.getElementById('btnNext').disabled=currentMediaIndex===currentMediaList.length-1;")
        builder.append("}")
        // Navigate media
        builder.append("function navigateMedia(direction){")
        builder.append("const newIndex=currentMediaIndex+direction;")
        builder.append("if(newIndex>=0&&newIndex<currentMediaList.length){")
        builder.append("currentMediaIndex=newIndex;")
        builder.append("resetZoom();")
        builder.append("updateViewerContent();")
        builder.append("}")
        builder.append("}")
        // Filter by state
        builder.append("function filterByState(stateId){")
        builder.append("const regions=document.querySelectorAll('.tree-item.region');")
        builder.append("let visibleCount=0;")
        builder.append("regions.forEach(function(region){")
        builder.append("if(stateId===''||region.getAttribute('data-state-id')===stateId){")
        builder.append("region.style.display='flex';")
        builder.append("visibleCount++;")
        builder.append("}else{")
        builder.append("region.style.display='none';")
        builder.append("}")
        builder.append("});")
        builder.append("document.getElementById('regionCount').textContent=visibleCount+' region'+(visibleCount!==1?'s':'');")
        builder.append("}")
        // Zoom functions
        builder.append("let currentZoom=1;")
        builder.append("const zoomStep=0.25;")
        builder.append("const minZoom=0.25;")
        builder.append("const maxZoom=4;")
        builder.append("function updateZoom(){")
        builder.append("const img=document.querySelector('#viewerContent img');")
        builder.append("if(img){")
        builder.append("img.style.transform='translate('+translateX+'px,'+translateY+'px) scale('+currentZoom+')';")
        builder.append("img.classList.toggle('zoomed',currentZoom!==1);")
        builder.append("document.getElementById('zoomLevel').textContent=Math.round(currentZoom*100)+'%';")
        builder.append("}")
        builder.append("}")
        builder.append("function zoomIn(){")
        builder.append("if(currentZoom<maxZoom){currentZoom=Math.min(maxZoom,currentZoom+zoomStep);updateZoom();}")
        builder.append("}")
        builder.append("function zoomOut(){")
        builder.append("if(currentZoom>minZoom){currentZoom=Math.max(minZoom,currentZoom-zoomStep);updateZoom();}")
        builder.append("}")
        builder.append("function resetZoom(){currentZoom=1;translateX=0;translateY=0;updateZoom();}")
        // Download current media - open in new tab
        builder.append("function downloadCurrentMedia(){")
        builder.append("if(currentMediaList.length===0)return;")
        builder.append("const item=currentMediaList[currentMediaIndex];")
        builder.append("window.open(item.path,'_blank');")
        builder.append("}")
        // Drag functionality for zoomed images
        builder.append("let isDragging=false;")
        builder.append("let startX=0,startY=0;")
        builder.append("let translateX=0,translateY=0;")
        builder.append("document.addEventListener('mousedown',function(e){")
        builder.append("const img=document.querySelector('#viewerContent img');")
        builder.append("if(!img||currentZoom===1)return;")
        builder.append("if(e.target===img){")
        builder.append("isDragging=true;")
        builder.append("startX=e.clientX-translateX;")
        builder.append("startY=e.clientY-translateY;")
        builder.append("img.style.cursor='grabbing';")
        builder.append("e.preventDefault();")
        builder.append("}")
        builder.append("});")
        builder.append("document.addEventListener('mousemove',function(e){")
        builder.append("if(!isDragging)return;")
        builder.append("translateX=e.clientX-startX;")
        builder.append("translateY=e.clientY-startY;")
        builder.append("updateZoom();")
        builder.append("});")
        builder.append("document.addEventListener('mouseup',function(){")
        builder.append("if(isDragging){")
        builder.append("isDragging=false;")
        builder.append("const img=document.querySelector('#viewerContent img');")
        builder.append("if(img)img.style.cursor='grab';")
        builder.append("}")
        builder.append("});")
        // Keyboard navigation
        builder.append("document.addEventListener('keydown',function(e){")
        builder.append("if(!document.getElementById('mediaViewer').classList.contains('active'))return;")
        builder.append("if(e.key==='ArrowLeft')navigateMedia(-1);")
        builder.append("else if(e.key==='ArrowRight')navigateMedia(1);")
        builder.append("else if(e.key==='Escape')closeViewer();")
        builder.append("else if(e.key==='+'||e.key==='=')zoomIn();")
        builder.append("else if(e.key==='-')zoomOut();")
        builder.append("else if(e.key==='0')resetZoom();")
        builder.append("});")
        // Vertical resizer (left-right column)
        builder.append("(function(){")
        builder.append("const resizer=document.getElementById('vResizer');")
        builder.append("const leftColumn=document.getElementById('leftColumn');")
        builder.append("let isResizing=false;")
        builder.append("resizer.addEventListener('mousedown',function(e){isResizing=true;resizer.classList.add('active');document.body.style.cursor='col-resize';document.body.style.userSelect='none';});")
        builder.append("document.addEventListener('mousemove',function(e){if(!isResizing)return;const containerWidth=document.querySelector('.container').offsetWidth;const newWidth=Math.max(250,Math.min(e.clientX,containerWidth*0.5));leftColumn.style.width=newWidth+'px';});")
        builder.append("document.addEventListener('mouseup',function(){if(isResizing){isResizing=false;resizer.classList.remove('active');document.body.style.cursor='';document.body.style.userSelect='';}});")
        builder.append("})();")
        // Horizontal resizer (tree-info panels)
        builder.append("(function(){")
        builder.append("const resizer=document.getElementById('hResizer');")
        builder.append("const treePanel=document.getElementById('treePanel');")
        builder.append("let isResizing=false;")
        builder.append("resizer.addEventListener('mousedown',function(e){isResizing=true;e.preventDefault();resizer.classList.add('active');document.body.style.cursor='row-resize';document.body.style.userSelect='none';});")
        builder.append("document.addEventListener('mousemove',function(e){if(!isResizing)return;const containerRect=document.getElementById('leftColumn').getBoundingClientRect();const relY=e.clientY-containerRect.top;const containerHeight=containerRect.height-5;const newHeight=Math.max(100,Math.min(relY,containerHeight-150));treePanel.style.flex='0 0 '+newHeight+'px';});")
        builder.append("document.addEventListener('mouseup',function(){if(isResizing){isResizing=false;resizer.classList.remove('active');document.body.style.cursor='';document.body.style.userSelect='';}});")
        builder.append("})();")
        builder.append("</script>")
        builder.append("</body>")
        builder.append("</html>")
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
    
    /**
     * Import a project from a ZIP file.
     * Supports both single project ZIPs and multi-project ZIPs (with nested ZIPs in projects/ folder).
     */
    suspend fun importFromZip(zipUri: Uri): ImportResult {
        return withContext(Dispatchers.IO) {
            try {
                val tempZipFile = File.createTempFile("import_", ".zip", context.cacheDir)
                context.contentResolver.openInputStream(zipUri)?.use { input ->
                    tempZipFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
                
                // First pass: check what type of archive this is
                val nestedZipFiles = mutableListOf<String>()
                var hasProjectJson = false
                var hasReportHtml = false
                
                ZipInputStream(tempZipFile.inputStream()).use { zipIn ->
                    var entry = zipIn.nextEntry
                    while (entry != null) {
                        when {
                            entry.name == "project.json" -> hasProjectJson = true
                            entry.name == "report.html" -> hasReportHtml = true
                            entry.name.startsWith("projects/") && entry.name.endsWith(".zip") -> {
                                nestedZipFiles.add(entry.name)
                            }
                        }
                        zipIn.closeEntry()
                        entry = zipIn.nextEntry
                    }
                }
                
                // If this is a multi-project archive, import each nested ZIP
                if (!hasProjectJson && nestedZipFiles.isNotEmpty()) {
                    val count = importNestedProjects(tempZipFile, nestedZipFiles)
                    tempZipFile.delete()
                    return@withContext ImportResult(
                        success = true, 
                        importedCount = count,
                        error = if (count > 0) null else "No valid projects found in archive"
                    )
                }
                
                // Single project import
                val result = importSingleProject(tempZipFile, hasReportHtml)
                tempZipFile.delete()
                result
            } catch (e: Exception) {
                e.printStackTrace()
                ImportResult(success = false, error = e.message)
            }
        }
    }
    
    /**
     * Import nested projects from a multi-project ZIP archive.
     */
    private suspend fun importNestedProjects(zipFile: File, nestedZipPaths: List<String>): Int {
        var importedCount = 0
        
        ZipInputStream(zipFile.inputStream()).use { zipIn ->
            var entry = zipIn.nextEntry
            while (entry != null) {
                if (nestedZipPaths.contains(entry.name)) {
                    // Extract nested ZIP to temp file
                    val nestedZipFile = File.createTempFile("nested_import_", ".zip", context.cacheDir)
                    nestedZipFile.outputStream().use { output ->
                        zipIn.copyTo(output)
                    }
                    
                    // Import the nested project
                    val result = importSingleProject(nestedZipFile, false)
                    if (result.success) {
                        importedCount++
                    }
                    
                    nestedZipFile.delete()
                }
                zipIn.closeEntry()
                entry = zipIn.nextEntry
            }
        }
        
        return importedCount
    }
    
    /**
     * Import a single project from a ZIP file.
     */
    private suspend fun importSingleProject(zipFile: File, hasReportHtml: Boolean): ImportResult {
        return try {
            val mediaDir = File(context.filesDir, "media")
            if (!mediaDir.exists()) mediaDir.mkdirs()
            
            var projectData: ProjectExportData? = null
            val regionsData = mutableListOf<Pair<RegionExportData, Long>>()
            val mediaFiles = mutableMapOf<String, File>()
            var photoFile: File? = null
            
            ZipInputStream(zipFile.inputStream()).use { zipIn ->
                var entry = zipIn.nextEntry
                while (entry != null) {
                    when {
                        entry.name == "project.json" -> {
                            val content = zipIn.readBytes().toString(Charsets.UTF_8)
                            projectData = gson.fromJson(content, ProjectExportData::class.java)
                        }
                        entry.name == "photo.jpg" -> {
                            photoFile = File.createTempFile("imported_photo_", ".jpg", context.cacheDir)
                            photoFile?.outputStream()?.use { output ->
                                zipIn.copyTo(output)
                            }
                        }
                        entry.name.startsWith("media/") -> {
                            val mediaFile = File(context.cacheDir, entry.name.replace("/", "_"))
                            mediaFile.outputStream().use { output ->
                                zipIn.copyTo(output)
                            }
                            mediaFiles[entry.name] = mediaFile
                        }
                        entry.name.startsWith("regions/") && entry.name.endsWith(".json") -> {
                            val content = zipIn.readBytes().toString(Charsets.UTF_8)
                            val regionData = gson.fromJson(content, RegionExportData::class.java)
                            regionsData.add(Pair(regionData, System.currentTimeMillis()))
                        }
                    }
                    zipIn.closeEntry()
                    entry = zipIn.nextEntry
                }
            }
            
            if (projectData == null) {
                // Check if this is an HTML report (export for PC) instead of a backup
                if (hasReportHtml) {
                    return ImportResult(
                        success = false, 
                        error = "This file is an HTML report, not a project backup. Use Share (not Export) to create importable backups."
                    )
                }
                return ImportResult(success = false, error = "Invalid project file: missing project.json")
            }
            
            // Copy photo to permanent storage with proper permissions
            val photoPath = if (photoFile != null && photoFile!!.exists()) {
                val permanentPhoto = File(mediaDir, "project_photo_${System.currentTimeMillis()}.jpg")
                photoFile!!.copyTo(permanentPhoto, overwrite = true)
                permanentPhoto.setReadable(true, false)
                permanentPhoto.setWritable(true, false)
                photoFile!!.delete()
                permanentPhoto.absolutePath
            } else null
            
            // Create project
            val project = Project(
                name = projectData!!.name,
                photoUri = photoPath ?: "",
                type1 = projectData!!.type1,
                type2 = projectData!!.type2,
                description = projectData!!.description,
                note = projectData!!.note,
                cellSize = projectData!!.cellSize
            )
            val projectId = projectRepository.insert(project)
            
            // Create regions and contents
            for ((regionData, _) in regionsData) {
                // Get or create state
                var stateId: Long? = null
                if (regionData.stateName != null && regionData.stateColor != null) {
                    val existingState = stateRepository.getStateByNameAndColor(regionData.stateName, regionData.stateColor)
                    stateId = existingState?.id ?: stateRepository.insertState(
                        com.plan.app.domain.model.State(
                            name = regionData.stateName,
                            color = regionData.stateColor
                        )
                    )
                }
                
                val region = com.plan.app.domain.model.Region(
                    projectId = projectId,
                    name = regionData.name,
                    stateId = stateId,
                    type1 = regionData.type1,
                    type2 = regionData.type2,
                    description = regionData.description,
                    note = regionData.note,
                    cells = regionData.cells.map { com.plan.app.domain.model.Cell(it.row, it.col) }
                )
                val regionId = regionRepository.insertRegion(region)
                
                // Create contents
                for (contentData in regionData.contents) {
                    val contentType = when (contentData.type) {
                        "PHOTO" -> ContentType.PHOTO
                        "VIDEO" -> ContentType.VIDEO
                        "FILE" -> ContentType.FILE
                        else -> ContentType.TEXT
                    }
                    
                    val contentPath = if (contentType != ContentType.TEXT) {
                        val mediaFile = mediaFiles[contentData.data]
                        if (mediaFile != null && mediaFile.exists()) {
                            // Use original file extension from originalFileName if available
                            val extension = when (contentType) {
                                ContentType.PHOTO -> {
                                    val ext = contentData.originalFileName?.substringAfterLast(".", "jpg") ?: "jpg"
                                    ".$ext"
                                }
                                ContentType.VIDEO -> {
                                    val ext = contentData.originalFileName?.substringAfterLast(".", "mp4") ?: "mp4"
                                    ".$ext"
                                }
                                ContentType.FILE -> {
                                    val ext = contentData.originalFileName?.substringAfterLast(".")
                                        ?: mediaFile.extension.ifEmpty { "bin" }
                                    ".$ext"
                                }
                                else -> ".bin"
                            }
                            val permanentMedia = File(mediaDir, "${contentType.name.lowercase()}_${regionId}_${System.currentTimeMillis()}$extension")
                            mediaFile.copyTo(permanentMedia, overwrite = true)
                            permanentMedia.setReadable(true, false)
                            permanentMedia.setWritable(true, false)
                            mediaFile.delete()
                            permanentMedia.absolutePath
                        } else contentData.data
                    } else contentData.data
                    
                    // Determine the original file name
                    val determinedOriginalFileName = contentData.originalFileName?.ifBlank { null }
                        ?: contentData.data.substringAfterLast("/")
                    
                    android.util.Log.d("ExportManager", "Importing content: type=$contentType, data=${contentData.data}, " +
                        "originalFileName in JSON=${contentData.originalFileName}, determined=$determinedOriginalFileName")
                    
                    contentRepository.insert(
                        Content(
                            regionId = regionId,
                            type = contentType,
                            data = contentPath,
                            originalFileName = determinedOriginalFileName,
                            sortOrder = contentData.sortOrder
                        )
                    )
                }
            }
            
            ImportResult(success = true, projectId = projectId)
        } catch (e: Exception) {
            e.printStackTrace()
            ImportResult(success = false, error = e.message)
        }
    }
    
    /**
     * Export all projects to a single ZIP file.
     */
    suspend fun exportAllProjects(outputFile: File): ZipExportResult {
        return withContext(Dispatchers.IO) {
            try {
                val projects = projectRepository.getAllProjectsOnce()
                
                ZipOutputStream(java.io.BufferedOutputStream(java.io.FileOutputStream(outputFile))).use { zip ->
                    for (project in projects) {
                        val projectZipFile = File.createTempFile("project_", ".zip", context.cacheDir)
                        val result = exportToZip(project, projectZipFile)
                        
                        if (result.success) {
                            val safeName = project.name.replace(Regex("[^a-zA-Z0-9_-]"), "_")
                            addFileToZip(zip, "projects/${safeName}_${project.id}.zip", projectZipFile)
                            projectZipFile.delete()
                        }
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
     * Export project as HTML report in ZIP file (for viewing on PC).
     */
    suspend fun exportForPCToZip(project: Project, outputFile: File, languageCode: String = "ru"): ZipExportResult {
        return withContext(Dispatchers.IO) {
            try {
                val tempDir = File(context.cacheDir, "export_pc_${System.currentTimeMillis()}")
                tempDir.mkdirs()
                
                val regions = regionRepository.getRegionsByProjectOnce(project.id)
                val states = stateRepository.getAllStatesOnce()
                
                // Create photo with areas overlay
                val photoFile = getFileFromUri(project.photoUri)
                if (photoFile != null && photoFile.exists()) {
                    val bitmap = loadBitmapWithExifOrientation(photoFile)
                    if (bitmap != null) {
                        val photoWithAreas = drawRegionsOnBitmap(bitmap, regions, states, project.cellSize)
                        File(tempDir, "photo_with_areas.jpg").writeBytes(
                            compressBitmap(photoWithAreas, Bitmap.CompressFormat.JPEG, 90)
                        )
                        bitmap.recycle()
                        photoWithAreas.recycle()
                    }
                }
                
                // Collect content data for each region
                val regionContentsMap = mutableMapOf<Long, List<ContentExportInfo>>()
                
                // Copy media files for each region and collect paths
                for (region in regions) {
                    val safeName = region.name.replace(Regex("[^a-zA-Z0-9_-]"), "_")
                    val regionDir = File(tempDir, safeName)
                    regionDir.mkdirs()
                    
                    val contents = contentRepository.getContentsByRegionOnce(region.id)
                    val contentInfos = mutableListOf<ContentExportInfo>()
                    
                    var photoIndex = 0
                    var videoIndex = 0
                    var fileIndex = 0
                    
                    for (content in contents) {
                        when (content.type) {
                            ContentType.TEXT -> {
                                File(regionDir, "comment.txt").writeText(content.data)
                            }
                            ContentType.PHOTO -> {
                                val sourceFile = getFileFromUri(content.data)
                                if (sourceFile != null && sourceFile.exists()) {
                                    // Use original file name if available
                                    val ext = content.originalFileName?.substringAfterLast(".", "jpg") ?: "jpg"
                                    val baseName = content.originalFileName?.substringBeforeLast(".")
                                        ?: "photo_$photoIndex"
                                    val fileName = "$baseName.$ext"
                                    sourceFile.copyTo(File(regionDir, fileName), overwrite = true)
                                    contentInfos.add(ContentExportInfo("photo", "$safeName/$fileName"))
                                    photoIndex++
                                }
                            }
                            ContentType.VIDEO -> {
                                val sourceFile = getFileFromUri(content.data)
                                if (sourceFile != null && sourceFile.exists()) {
                                    // Use original file name if available
                                    val ext = content.originalFileName?.substringAfterLast(".", "mp4") ?: "mp4"
                                    val baseName = content.originalFileName?.substringBeforeLast(".")
                                        ?: "video_$videoIndex"
                                    val fileName = "$baseName.$ext"
                                    sourceFile.copyTo(File(regionDir, fileName), overwrite = true)
                                    contentInfos.add(ContentExportInfo("video", "$safeName/$fileName"))
                                    videoIndex++
                                }
                            }
                            ContentType.FILE -> {
                                val sourceFile = getFileFromUri(content.data)
                                if (sourceFile != null && sourceFile.exists()) {
                                    // Use original file name if available
                                    val ext = content.originalFileName?.substringAfterLast(".")
                                        ?: sourceFile.extension.ifEmpty { "bin" }
                                    val baseName = content.originalFileName?.substringBeforeLast(".")
                                        ?: "file_$fileIndex"
                                    val fileName = "$baseName.$ext"
                                    sourceFile.copyTo(File(regionDir, fileName), overwrite = true)
                                    contentInfos.add(ContentExportInfo("file", "${safeName}/$fileName"))
                                    fileIndex++
                                }
                            }
                        }
                    }
                    
                    regionContentsMap[region.id] = contentInfos
                }
                
                // Create HTML report with content data
                val htmlContent = generateHtmlReportWithContents(project, regions, states, regionContentsMap, languageCode)
                File(tempDir, "report.html").writeText(htmlContent)
                
                // Pack everything into ZIP
                ZipOutputStream(java.io.BufferedOutputStream(java.io.FileOutputStream(outputFile))).use { zip ->
                    tempDir.walkTopDown().forEach { file ->
                        if (file.isFile) {
                            val relativePath = file.relativeTo(tempDir).path.replace("\\", "/")
                            addFileToZip(zip, relativePath, file)
                        }
                    }
                }
                
                // Cleanup temp directory
                tempDir.deleteRecursively()
                
                ZipExportResult(success = true, file = outputFile)
            } catch (e: Exception) {
                e.printStackTrace()
                ZipExportResult(success = false, error = e.message)
            }
        }
    }
    
    /**
     * Export all projects as a single HTML report in one ZIP file (for viewing on PC).
     */
    suspend fun exportAllProjectsForPC(outputFile: File, languageCode: String = "ru"): ZipExportResult {
        return withContext(Dispatchers.IO) {
            try {
                val tempDir = File(context.cacheDir, "export_all_${System.currentTimeMillis()}")
                tempDir.mkdirs()
                
                val projects = projectRepository.getAllProjectsOnce()
                val states = stateRepository.getAllStatesOnce()
                
                // Collect all data
                val allProjectsData = mutableListOf<AllProjectData>()
                
                for (project in projects) {
                    val regions = regionRepository.getRegionsByProjectOnce(project.id)
                    val regionContentsMap = mutableMapOf<Long, List<ContentExportInfo>>()
                    
                    // Create photo with areas
                    val photoFile = getFileFromUri(project.photoUri)
                    var photoPath: String? = null
                    if (photoFile != null && photoFile.exists()) {
                        val bitmap = loadBitmapWithExifOrientation(photoFile)
                        if (bitmap != null) {
                            val photoWithAreas = drawRegionsOnBitmap(bitmap, regions, states, project.cellSize)
                            val safeProjectName = project.name.replace(Regex("[^a-zA-Z0-9_-]"), "_")
                            photoPath = "photos/${safeProjectName}_${project.id}.jpg"
                            File(tempDir, photoPath).apply {
                                parentFile?.mkdirs()
                                writeBytes(compressBitmap(photoWithAreas, Bitmap.CompressFormat.JPEG, 90))
                            }
                            bitmap.recycle()
                            photoWithAreas.recycle()
                        }
                    }
                    
                    // Copy media files for each region
                    for (region in regions) {
                        val safeProjectName = project.name.replace(Regex("[^a-zA-Z0-9_-]"), "_")
                        val safeRegionName = region.name.replace(Regex("[^a-zA-Z0-9_-]"), "_")
                        val regionFolder = "media/${safeProjectName}/${safeRegionName}"
                        val regionDir = File(tempDir, regionFolder)
                        regionDir.mkdirs()
                        
                        val contents = contentRepository.getContentsByRegionOnce(region.id)
                        val contentInfos = mutableListOf<ContentExportInfo>()
                        
                        var photoIndex = 0
                        var videoIndex = 0
                    var fileIndex = 0
                        
                        for (content in contents) {
                            when (content.type) {
                                ContentType.TEXT -> {
                                    File(regionDir, "comment.txt").writeText(content.data)
                                }
                                ContentType.PHOTO -> {
                                    val sourceFile = getFileFromUri(content.data)
                                    if (sourceFile != null && sourceFile.exists()) {
                                        // Use original file name if available
                                        val ext = content.originalFileName?.substringAfterLast(".", "jpg") ?: "jpg"
                                        val baseName = content.originalFileName?.substringBeforeLast(".")
                                            ?: "photo_$photoIndex"
                                        val fileName = "$baseName.$ext"
                                        sourceFile.copyTo(File(regionDir, fileName), overwrite = true)
                                        contentInfos.add(ContentExportInfo("photo", "$regionFolder/$fileName"))
                                        photoIndex++
                                    }
                                }
                                ContentType.VIDEO -> {
                                    val sourceFile = getFileFromUri(content.data)
                                    if (sourceFile != null && sourceFile.exists()) {
                                        // Use original file name if available
                                        val ext = content.originalFileName?.substringAfterLast(".", "mp4") ?: "mp4"
                                        val baseName = content.originalFileName?.substringBeforeLast(".")
                                            ?: "video_$videoIndex"
                                        val fileName = "$baseName.$ext"
                                        sourceFile.copyTo(File(regionDir, fileName), overwrite = true)
                                        contentInfos.add(ContentExportInfo("video", "$regionFolder/$fileName"))
                                        videoIndex++
                                    }
                                }
                                ContentType.FILE -> {
                                    val sourceFile = getFileFromUri(content.data)
                                    if (sourceFile != null && sourceFile.exists()) {
                                        // Use original file name if available
                                        val ext = content.originalFileName?.substringAfterLast(".")
                                            ?: sourceFile.extension.ifEmpty { "bin" }
                                        val baseName = content.originalFileName?.substringBeforeLast(".")
                                            ?: "file_$fileIndex"
                                        val fileName = "$baseName.$ext"
                                        sourceFile.copyTo(File(regionDir, fileName), overwrite = true)
                                        contentInfos.add(ContentExportInfo("file", "${regionFolder}/$fileName"))
                                        fileIndex++
                                    }
                                }
                            }
                        }
                        
                        regionContentsMap[region.id] = contentInfos
                    }
                    
                    allProjectsData.add(AllProjectData(
                        project = project,
                        regions = regions,
                        photoPath = photoPath,
                        regionContentsMap = regionContentsMap
                    ))
                }
                
                // Generate single HTML with all projects
                val htmlContent = generateHtmlReportForAllProjects(allProjectsData, states, languageCode)
                File(tempDir, "report.html").writeText(htmlContent)
                
                // Pack everything into ZIP
                ZipOutputStream(java.io.BufferedOutputStream(java.io.FileOutputStream(outputFile))).use { zip ->
                    tempDir.walkTopDown().forEach { file ->
                        if (file.isFile) {
                            val relativePath = file.relativeTo(tempDir).path.replace("\\", "/")
                            addFileToZip(zip, relativePath, file)
                        }
                    }
                }
                
                // Cleanup temp directory
                tempDir.deleteRecursively()
                
                ZipExportResult(success = true, file = outputFile)
            } catch (e: Exception) {
                e.printStackTrace()
                ZipExportResult(success = false, error = e.message)
            }
        }
    }
    
    private data class AllProjectData(
        val project: Project,
        val regions: List<Region>,
        val photoPath: String?,
        val regionContentsMap: Map<Long, List<ContentExportInfo>>
    )
    
    private data class ContentExportInfo(
        val type: String,
        val path: String
    )
    
    /**
     * Translations for HTML report based on app language.
     */
    private data class HtmlTranslations(
        val lang: String,
        val htmlLang: String,
        // Panel headers
        val projectsAndRegions: String,
        val regionDetails: String,
        val mediaContent: String,
        // Field labels
        val regionName: String,
        val state: String,
        val type1: String,
        val type2: String,
        val description: String,
        val note: String,
        val regions: String,
        // Photo link
        val viewPhotoWithAreas: String,
        // Media
        val photo: String,
        val video: String,
        val file: String,
        val files: String,
        val items: String,
        val item: String,
        // Viewer
        val prev: String,
        val next: String,
        val backToGrid: String,
        // Empty states
        val selectProjectOrRegion: String,
        val selectRegionToViewMedia: String,
        val noMediaForRegion: String,
        val noPhotoAvailable: String,
        // Filter
        val filterByState: String,
        val allStates: String,
        // Search
        val searchPlaceholder: String,
        val noResultsFound: String,
        // Zoom
        val zoomIn: String,
        val zoomOut: String,
        val resetZoom: String,
        // Download
        val download: String,
        val downloadFile: String,
        val openInNewTab: String,
        val cannotPreview: String,
        // Search result types
        val projectType: String,
        val regionType: String
    ) {
        companion object {
            fun forLanguage(languageCode: String): HtmlTranslations {
                return when (languageCode) {
                    "ru" -> HtmlTranslations(
                        lang = "ru",
                        htmlLang = "ru",
                        projectsAndRegions = "Структура",
                        regionDetails = "Описание",
                        mediaContent = "Мультимедиа",
                        regionName = "Название области",
                        state = "Состояние",
                        type1 = "Тип 1",
                        type2 = "Тип 2",
                        description = "Описание",
                        note = "Заметка",
                        regions = "областей",
                        viewPhotoWithAreas = "📷 Фото с областями",
                        photo = "Фото",
                        video = "Видео",
                        file = "Файл",
                        files = "Файлы",
                        items = "элементов",
                        item = "элемент",
                        prev = "◀ Назад",
                        next = "Вперёд ▶",
                        backToGrid = "✕ К галерее",
                        selectProjectOrRegion = "Выберите проект или область",
                        selectRegionToViewMedia = "Выберите область для просмотра медиа",
                        noMediaForRegion = "Нет медиа для этой области",
                        noPhotoAvailable = "Нет фото",
                        filterByState = "Фильтр по состоянию:",
                        allStates = "Все состояния",
                        searchPlaceholder = "Поиск...",
                        noResultsFound = "Ничего не найдено",
                        zoomIn = "+",
                        zoomOut = "−",
                        resetZoom = "↺ Сброс",
                        download = "⬇ Скачать",
                        downloadFile = "Скачать файл",
                        openInNewTab = "Открыть в новой вкладке",
                        cannotPreview = "Скачайте файл и откройте соответствующей программой",
                        projectType = "проект",
                        regionType = "область"
                    )
                    "zh" -> HtmlTranslations(
                        lang = "zh",
                        htmlLang = "zh-CN",
                        projectsAndRegions = "结构",
                        regionDetails = "描述",
                        mediaContent = "多媒体",
                        regionName = "区域名称",
                        state = "状态",
                        type1 = "类型 1",
                        type2 = "类型 2",
                        description = "描述",
                        note = "备注",
                        regions = "个区域",
                        viewPhotoWithAreas = "📷 查看带区域的照片",
                        photo = "照片",
                        video = "视频",
                        file = "文件",
                        files = "文件",
                        items = "项",
                        item = "项",
                        prev = "◀ 上一个",
                        next = "下一个 ▶",
                        backToGrid = "✕ 返回",
                        selectProjectOrRegion = "选择项目或区域",
                        selectRegionToViewMedia = "选择区域以查看媒体内容",
                        noMediaForRegion = "此区域没有媒体内容",
                        noPhotoAvailable = "无照片",
                        filterByState = "按状态筛选：",
                        allStates = "所有状态",
                        searchPlaceholder = "搜索...",
                        noResultsFound = "未找到结果",
                        zoomIn = "+",
                        zoomOut = "−",
                        resetZoom = "↺ 重置",
                        download = "⬇ 下载",
                        downloadFile = "下载文件",
                        openInNewTab = "在新标签页中打开",
                        cannotPreview = "请下载文件并使用相应程序打开",
                        projectType = "项目",
                        regionType = "区域"
                    )
                    else -> HtmlTranslations(
                        lang = "en",
                        htmlLang = "en",
                        projectsAndRegions = "Projects & Regions",
                        regionDetails = "Region Details",
                        mediaContent = "Media Content",
                        regionName = "Region Name",
                        state = "State",
                        type1 = "Type 1",
                        type2 = "Type 2",
                        description = "Description",
                        note = "Note",
                        regions = "regions",
                        viewPhotoWithAreas = "📷 View photo with areas",
                        photo = "Photo",
                        video = "Video",
                        file = "File",
                        files = "Files",
                        items = "items",
                        item = "item",
                        prev = "◀ Prev",
                        next = "Next ▶",
                        backToGrid = "✕ Back to Grid",
                        selectProjectOrRegion = "Select a project or region",
                        selectRegionToViewMedia = "Select a region to view media content",
                        noMediaForRegion = "No media content for this region",
                        noPhotoAvailable = "No photo available",
                        filterByState = "Filter by State:",
                        allStates = "All States",
                        searchPlaceholder = "Search...",
                        noResultsFound = "No results found",
                        zoomIn = "+",
                        zoomOut = "−",
                        resetZoom = "↺ Reset",
                        download = "⬇ Download",
                        downloadFile = "Download File",
                        openInNewTab = "Open in New Tab",
                        cannotPreview = "Download the file and open with appropriate program",
                        projectType = "project",
                        regionType = "region"
                    )
                }
            }
        }
    }
    
    private fun generateHtmlReportForAllProjects(
        allProjectsData: List<AllProjectData>,
        states: List<State>,
        languageCode: String = "ru"
    ): String {
        val t = HtmlTranslations.forLanguage(languageCode)
        val builder = StringBuilder()
        builder.append("<!DOCTYPE html>")
        builder.append("<html lang='${t.htmlLang}'>")
        builder.append("<head>")
        builder.append("<meta charset='UTF-8'>")
        builder.append("<meta name='viewport' content='width=device-width, initial-scale=1.0'>")
        builder.append("<meta http-equiv='X-UA-Compatible' content='IE=edge'>")
        builder.append("<title>All Projects</title>")
        builder.append("<style>")
        // Reset and base styles
        builder.append("*{box-sizing:border-box;margin:0;padding:0;}")
        builder.append("html,body{height:100%;font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',Roboto,Helvetica,Arial,sans-serif;font-size:14px;background:#f0f2f5;color:#333;}")
        // Main container with search
        builder.append(".main-wrapper{display:flex;flex-direction:column;height:100vh;}")
        // Search bar
        builder.append(".search-bar{padding:12px 16px;background:#fff;border-bottom:1px solid #ddd;display:flex;align-items:center;gap:12px;flex-shrink:0;}")
        builder.append(".search-input{flex:1;padding:10px 16px;border:2px solid #e0e0e0;border-radius:8px;font-size:14px;transition:border-color 0.2s;}")
        builder.append(".search-input:focus{outline:none;border-color:#1976d2;}")
        builder.append(".search-results{position:absolute;top:100%;left:0;right:0;background:#fff;border:1px solid #ddd;border-radius:8px;box-shadow:0 4px 12px rgba(0,0,0,0.15);max-height:300px;overflow:auto;z-index:1000;display:none;margin-top:4px;}")
        builder.append(".search-results.active{display:block;}")
        builder.append(".search-result-item{padding:10px 16px;cursor:pointer;border-bottom:1px solid #eee;display:flex;align-items:center;gap:10px;}")
        builder.append(".search-result-item:last-child{border-bottom:none;}")
        builder.append(".search-result-item:hover{background:#e3f2fd;}")
        builder.append(".search-result-type{font-size:10px;padding:2px 6px;border-radius:4px;text-transform:uppercase;font-weight:600;}")
        builder.append(".search-result-type.project{background:#e8f5e9;color:#2e7d32;}")
        builder.append(".search-result-type.region{background:#fff3e0;color:#ef6c00;}")
        builder.append(".search-result-name{flex:1;font-weight:500;}")
        builder.append(".search-result-parent{color:#888;font-size:12px;}")
        builder.append(".search-wrapper{position:relative;flex:1;}")
        // Main container
        builder.append(".container{display:flex;flex:1;overflow:hidden;}")
        // Left column (1/3 width)
        builder.append(".left-column{width:33.333%;min-width:250px;max-width:50%;display:flex;flex-direction:column;border-right:1px solid #ddd;background:#fff;overflow:hidden;}")
        // Right column (2/3 width)
        builder.append(".right-column{flex:1;display:flex;flex-direction:column;background:#fafafa;min-width:300px;}")
        // Panel styles
        builder.append(".panel{display:flex;flex-direction:column;overflow:hidden;}")
        builder.append(".panel-header{padding:12px 16px;background:#1976d2;color:#fff;font-weight:600;font-size:15px;display:flex;align-items:center;justify-content:space-between;flex-shrink:0;}")
        builder.append(".panel-header.light{background:#f5f5f5;color:#333;border-bottom:1px solid #ddd;}")
        builder.append(".panel-content{flex:1;overflow:auto;padding:16px;}")
        // Tree panel (Part 1) - initial size 35%, resizable
        builder.append(".tree-panel{flex:0 0 35%;min-height:100px;max-height:60%;overflow:hidden;}")
        builder.append(".tree-panel .panel-content{padding:8px;overflow:auto;}")
        // Info panel (Part 2) - takes remaining space
        builder.append(".info-panel{flex:1 1 auto;min-height:100px;overflow:hidden;}")
        // Media panel (Part 3)
        builder.append(".media-panel{flex:1;}")
        // Tree styles
        builder.append(".tree-item{padding:6px 8px;cursor:pointer;border-radius:4px;margin:2px 0;display:flex;align-items:center;gap:8px;transition:background 0.2s;user-select:none;}")
        builder.append(".tree-item:hover{background:#e3f2fd;}")
        builder.append(".tree-item.active{background:#bbdefb;font-weight:500;}")
        builder.append(".tree-item.project{font-weight:600;color:#1976d2;padding-left:12px;}")
        builder.append(".tree-item.region{padding-left:32px;color:#555;}")
        builder.append(".tree-icon{width:18px;height:18px;display:flex;align-items:center;justify-content:center;font-size:12px;}")
        builder.append(".tree-children{margin-left:8px;}")
        builder.append(".tree-children.collapsed{display:none;}")
        // Color indicator
        builder.append(".color-indicator{width:12px;height:12px;border-radius:50%;display:inline-block;border:1px solid rgba(0,0,0,0.2);flex-shrink:0;}")
        // Info panel styles
        builder.append(".info-section{margin-bottom:16px;padding-bottom:16px;border-bottom:1px solid #eee;}")
        builder.append(".info-section:last-child{border-bottom:none;margin-bottom:0;padding-bottom:0;}")
        builder.append(".info-label{font-weight:600;color:#666;font-size:12px;text-transform:uppercase;margin-bottom:4px;}")
        builder.append(".info-value{color:#333;line-height:1.5;word-wrap:break-word;white-space:pre-wrap;}")
        // Media panel styles
        builder.append(".media-grid{display:grid;grid-template-columns:repeat(auto-fill,minmax(200px,1fr));gap:16px;}")
        builder.append(".media-item{background:#fff;border-radius:8px;overflow:hidden;box-shadow:0 2px 8px rgba(0,0,0,0.1);transition:transform 0.2s,box-shadow 0.2s;cursor:pointer;}")
        builder.append(".media-item:hover{transform:translateY(-2px);box-shadow:0 4px 16px rgba(0,0,0,0.15);}")
        builder.append(".media-item img,.media-item video{width:100%;height:150px;object-fit:cover;background:#eee;display:block;}")
        builder.append(".media-item .media-caption{padding:8px 12px;font-size:12px;color:#666;}")
        builder.append(".media-item .media-type{display:inline-block;padding:2px 6px;border-radius:4px;font-size:10px;text-transform:uppercase;font-weight:600;}")
        builder.append(".media-type.photo{background:#e8f5e9;color:#2e7d32;}")
        builder.append(".media-type.video{background:#fff3e0;color:#ef6c00;}")
        builder.append(".media-type.file{background:#e3f2fd;color:#1976d2;}")
        builder.append(".media-item .file-icon{width:100%;height:150px;display:flex;align-items:center;justify-content:center;background:#f5f5f5;font-size:48px;}")
        builder.append(".media-item .file-name{padding:8px 12px;font-size:11px;color:#333;word-break:break-all;max-height:40px;overflow:hidden;}")
        // Media viewer
        builder.append(".media-viewer{display:none;flex-direction:column;height:100%;background:#1a1a1a;}")
        builder.append(".media-viewer.active{display:flex;}")
        builder.append(".viewer-header{display:flex;align-items:center;gap:12px;padding:12px 16px;background:#2a2a2a;color:#fff;flex-shrink:0;}")
        builder.append(".viewer-title{font-weight:500;font-size:14px;flex:1;overflow:hidden;text-overflow:ellipsis;white-space:nowrap;}")
        builder.append(".viewer-nav{display:flex;gap:8px;}")
        builder.append(".viewer-btn{background:rgba(255,255,255,0.1);border:none;color:#fff;padding:8px 16px;border-radius:6px;cursor:pointer;font-size:13px;transition:background 0.2s;display:flex;align-items:center;gap:6px;}")
        builder.append(".viewer-btn:hover{background:rgba(255,255,255,0.2);}")
        builder.append(".viewer-btn:disabled{opacity:0.5;cursor:not-allowed;}")
        builder.append(".viewer-content{flex:1;display:flex;align-items:center;justify-content:center;padding:16px;overflow:auto;}")
        builder.append(".viewer-content img{max-width:100%;max-height:100%;object-fit:contain;border-radius:4px;transform-origin:center;transition:transform 0.15s ease-out;cursor:grab;}")
        builder.append(".viewer-content img.zoomed{cursor:move;}")
        builder.append(".viewer-content video{max-width:100%;max-height:100%;border-radius:4px;}")
        builder.append(".viewer-footer{display:flex;align-items:center;justify-content:center;gap:16px;padding:12px 16px;background:#2a2a2a;flex-shrink:0;}")
        builder.append(".viewer-counter{color:#aaa;font-size:13px;}")
        // Zoom controls - same style as viewer-btn
        builder.append(".zoom-controls{display:flex;align-items:center;gap:4px;}")
        builder.append(".zoom-btn{background:rgba(255,255,255,0.1);border:none;color:#fff;padding:8px 14px;border-radius:6px;cursor:pointer;font-size:16px;font-weight:bold;transition:background 0.2s;}")
        builder.append(".zoom-btn:hover{background:rgba(255,255,255,0.2);}")
        builder.append(".zoom-level{color:#fff;font-size:13px;min-width:50px;text-align:center;}")
        // Empty state
        builder.append(".empty-state{text-align:center;padding:40px 20px;color:#999;}")
        builder.append(".empty-state-icon{font-size:48px;margin-bottom:16px;opacity:0.5;}")
        // Resizer
        builder.append(".resizer{width:5px;background:#e0e0e0;cursor:col-resize;transition:background 0.2s;flex-shrink:0;}")
        builder.append(".resizer:hover,.resizer.active{background:#1976d2;}")
        // Horizontal resizer
        builder.append(".h-resizer{height:5px;background:#e0e0e0;cursor:row-resize;transition:background 0.2s;flex-shrink:0;}")
        builder.append(".h-resizer:hover,.h-resizer.active{background:#1976d2;}")
        // Project info header
        builder.append(".project-header{background:linear-gradient(135deg,#1976d2,#1565c0);color:#fff;padding:16px;border-radius:8px;margin-bottom:12px;}")
        builder.append(".project-header h1{font-size:18px;margin-bottom:4px;}")
        builder.append(".project-header .meta{font-size:12px;opacity:0.9;}")
        // Photo link
        builder.append(".photo-link{display:inline-flex;align-items:center;gap:6px;padding:8px 16px;background:#e3f2fd;color:#1976d2;border-radius:6px;text-decoration:none;font-size:13px;margin-top:12px;transition:background 0.2s;cursor:pointer;}")
        builder.append(".photo-link:hover{background:#bbdefb;}")
        // Scrollbar styling
        builder.append(".panel-content{scrollbar-width:thin;scrollbar-color:#ccc #f1f1f1;}")
        builder.append(".panel-content::-webkit-scrollbar{width:8px;height:8px;}")
        builder.append(".panel-content::-webkit-scrollbar-track{background:#f1f1f1;border-radius:4px;}")
        builder.append(".panel-content::-webkit-scrollbar-thumb{background:#ccc;border-radius:4px;}")
        // Footer
        builder.append(".footer{padding:12px 16px;background:#f5f5f5;border-top:1px solid #ddd;font-size:11px;color:#888;text-align:center;flex-shrink:0;}")
        // Region badge
        builder.append(".region-badge{display:inline-block;padding:2px 8px;border-radius:12px;font-size:11px;background:#e0e0e0;color:#666;margin-left:auto;}")
        builder.append("</style>")
        builder.append("</head>")
        builder.append("<body>")
        builder.append("<div class='main-wrapper'>")
        // Search bar
        builder.append("<div class='search-bar'>")
        builder.append("<div class='search-wrapper'>")
        builder.append("<input type='text' class='search-input' id='searchInput' placeholder='${t.searchPlaceholder}' autocomplete='off'>")
        builder.append("<div class='search-results' id='searchResults'></div>")
        builder.append("</div>")
        builder.append("</div>")
        // Main container
        builder.append("<div class='container'>")
        // Left column
        builder.append("<div class='left-column' id='leftColumn'>")
        // Part 1: Tree panel
        builder.append("<div class='panel tree-panel' id='treePanel'>")
        builder.append("<div class='panel-header light'><span>${t.projectsAndRegions}</span><span id='totalCount'>0 ${t.items}</span></div>")
        builder.append("<div class='panel-content' id='treeContent'>")
        // Tree structure
        for ((projectIndex, projectData) in allProjectsData.withIndex()) {
            val project = projectData.project
            val regions = projectData.regions
            builder.append("<div class='tree-item project' data-project-id='${project.id}' data-project-index='$projectIndex' onclick='selectProject($projectIndex)'>")
            builder.append("<span class='tree-icon'>📁</span>")
            builder.append("<span style='flex:1;overflow:hidden;text-overflow:ellipsis;white-space:nowrap;'>${escapeHtml(project.name)}</span>")
            if (regions.isNotEmpty()) {
                builder.append("<span class='region-badge'>${regions.size}</span>")
            }
            builder.append("</div>")
            builder.append("<div class='tree-children' id='project_${projectIndex}_regions'>")
            for ((regionIndex, region) in regions.withIndex()) {
                val state = region.stateId?.let { stateId -> states.find { it.id == stateId } }
                val colorHex = if (state != null) "#${Integer.toHexString(state.color).substring(2).uppercase()}" else "#9e9e9e"
                val contentCount = projectData.regionContentsMap[region.id]?.size ?: 0
                builder.append("<div class='tree-item region' data-region-id='${region.id}' data-project-index='$projectIndex' data-region-index='$regionIndex' onclick='selectRegion($projectIndex,$regionIndex)'>")
                builder.append("<span class='color-indicator' style='background-color:$colorHex;'></span>")
                builder.append("<span style='flex:1;overflow:hidden;text-overflow:ellipsis;white-space:nowrap;'>${escapeHtml(region.name)}</span>")
                if (contentCount > 0) {
                    builder.append("<span class='region-badge'>$contentCount</span>")
                }
                builder.append("</div>")
            }
            builder.append("</div>")
        }
        builder.append("</div>")
        builder.append("</div>")
        // Horizontal resizer between tree and info panels
        builder.append("<div class='h-resizer' id='hResizer'></div>")
        // Part 2: Info panel
        builder.append("<div class='panel info-panel' id='infoPanel'>")
        builder.append("<div class='panel-header light'>${t.regionDetails}</div>")
        builder.append("<div class='panel-content' id='infoContent'>")
        builder.append("<div class='empty-state'><div class='empty-state-icon'>📋</div><div>${t.selectProjectOrRegion}</div></div>")
        builder.append("</div>")
        builder.append("</div>")
        builder.append("</div>") // Close left-column
        // Vertical resizer
        builder.append("<div class='resizer' id='vResizer'></div>")
        // Right column (Part 3: Media panel)
        builder.append("<div class='right-column'>")
        builder.append("<div class='panel media-panel'>")
        builder.append("<div class='panel-header'><span>${t.mediaContent}</span><span id='mediaCount'>0 ${t.items}</span></div>")
        builder.append("<div class='panel-content' id='mediaContent'>")
        builder.append("<div class='empty-state'><div class='empty-state-icon'>📷</div><div>${t.selectRegionToViewMedia}</div></div>")
        builder.append("</div>")
        // Media viewer
        builder.append("<div class='media-viewer' id='mediaViewer'>")
        builder.append("<div class='viewer-header'>")
        // Left side: filename
        builder.append("<span class='viewer-title' id='viewerTitle'>${t.photo}</span>")
        // Download button
        builder.append("<button class='viewer-btn' id='btnDownload' onclick='downloadCurrentMedia()' style='margin-left:12px;'>${t.download}</button>")
        // Zoom controls in header
        builder.append("<div class='zoom-controls'>")
        builder.append("<button class='zoom-btn' onclick='zoomOut()' title='Zoom Out'>${t.zoomOut}</button>")
        builder.append("<span class='zoom-level' id='zoomLevel'>100%</span>")
        builder.append("<button class='zoom-btn' onclick='zoomIn()' title='Zoom In'>${t.zoomIn}</button>")
        builder.append("<button class='zoom-btn' onclick='resetZoom()' title='Reset'>${t.resetZoom}</button>")
        builder.append("</div>")
        // Right side: navigation buttons
        builder.append("<div class='viewer-nav'>")
        builder.append("<button class='viewer-btn' id='btnPrev' onclick='navigateMedia(-1)'>${t.prev}</button>")
        builder.append("<button class='viewer-btn' id='btnNext' onclick='navigateMedia(1)'>${t.next}</button>")
        builder.append("<button class='viewer-btn' onclick='closeViewer()'>${t.backToGrid}</button>")
        builder.append("</div>")
        builder.append("</div>")
        builder.append("<div class='viewer-content' id='viewerContent'></div>")
        // Footer with counter only
        builder.append("<div class='viewer-footer'>")
        builder.append("<span class='viewer-counter' id='viewerCounter'>1 / 1</span>")
        builder.append("</div>")
        builder.append("</div>")
        builder.append("</div>")
        builder.append("<div class='footer'>Generated on ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())} • ${allProjectsData.size} projects</div>")
        builder.append("</div>")
        builder.append("</div>")
        builder.append("</div>")
        // JavaScript
        builder.append("<script>")
        // Projects data
        builder.append("const projectsData = [")
        for (projectData in allProjectsData) {
            val project = projectData.project
            val regions = projectData.regions
            builder.append("{")
            builder.append("id:${project.id},")
            builder.append("name:'${escapeJs(project.name)}',")
            builder.append("type1:'${escapeJs(project.type1 ?: "")}',")
            builder.append("type2:'${escapeJs(project.type2 ?: "")}',")
            builder.append("description:'${escapeJs(project.description ?: "")}',")
            builder.append("note:'${escapeJs(project.note ?: "")}',")
            builder.append("photoPath:'${escapeJs(projectData.photoPath ?: "")}',")
            builder.append("regions:[")
            for (region in regions) {
                val state = region.stateId?.let { stateId -> states.find { it.id == stateId } }
                val colorHex = if (state != null) "#${Integer.toHexString(state.color).substring(2).uppercase()}" else "#9e9e9e"
                builder.append("{")
                builder.append("id:${region.id},")
                builder.append("name:'${escapeJs(region.name)}',")
                builder.append("stateName:'${escapeJs(state?.name ?: "N/A")}',")
                builder.append("stateColor:'$colorHex',")
                builder.append("type1:'${escapeJs(region.type1 ?: "")}',")
                builder.append("type2:'${escapeJs(region.type2 ?: "")}',")
                builder.append("description:'${escapeJs(region.description ?: "")}',")
                builder.append("note:'${escapeJs(region.note ?: "")}',")
                builder.append("contents:[")
                val contents = projectData.regionContentsMap[region.id] ?: emptyList()
                for (content in contents) {
                    builder.append("{type:'${content.type}',path:'${content.path}'},")
                }
                builder.append("]")
                builder.append("},")
            }
            builder.append("]")
            builder.append("},")
        }
        builder.append("];")
        // State
        builder.append("let currentProjectIndex=-1;")
        builder.append("let currentRegionIndex=-1;")
        builder.append("let currentMediaIndex=0;")
        builder.append("let currentMediaList=[];")
        // Translations
        builder.append("const i18n={")
        builder.append("regionName:'${escapeJs(t.regionName)}',")
        builder.append("project:'${escapeJs(t.projectsAndRegions)}',")
        builder.append("state:'${escapeJs(t.state)}',")
        builder.append("type1:'${escapeJs(t.type1)}',")
        builder.append("type2:'${escapeJs(t.type2)}',")
        builder.append("description:'${escapeJs(t.description)}',")
        builder.append("note:'${escapeJs(t.note)}',")
        builder.append("regions:'${escapeJs(t.regions)}',")
        builder.append("photo:'${escapeJs(t.photo)}',")
        builder.append("video:'${escapeJs(t.video)}',")
        builder.append("file:'${escapeJs(t.file)}',")
        builder.append("files:'${escapeJs(t.files)}',")
        builder.append("item:'${escapeJs(t.item)}',")
        builder.append("items:'${escapeJs(t.items)}',")
        builder.append("noMediaForRegion:'${escapeJs(t.noMediaForRegion)}',")
        builder.append("noPhotoAvailable:'${escapeJs(t.noPhotoAvailable)}',")
        builder.append("viewPhotoWithAreas:'${escapeJs(t.viewPhotoWithAreas)}',")
        builder.append("cannotPreview:'${escapeJs(t.cannotPreview)}',")
        builder.append("projectType:'${escapeJs(t.projectType)}',")
        builder.append("regionType:'${escapeJs(t.regionType)}',")
        builder.append("noResultsFound:'${escapeJs(t.noResultsFound)}'")
        builder.append("};")
        // Update total count
        builder.append("(function(){")
        builder.append("let total=0;")
        builder.append("projectsData.forEach(p=>total+=p.regions.length+1);")
        builder.append("document.getElementById('totalCount').textContent=total+' '+i18n.items;")
        builder.append("})();")
        // Search functionality
        builder.append("const searchInput=document.getElementById('searchInput');")
        builder.append("const searchResults=document.getElementById('searchResults');")
        builder.append("searchInput.addEventListener('input',function(e){")
        builder.append("const query=e.target.value.toLowerCase().trim();")
        builder.append("if(query.length<2){searchResults.classList.remove('active');return;}")
        builder.append("const results=[];")
        builder.append("projectsData.forEach(function(project,pi){")
        builder.append("if(project.name.toLowerCase().includes(query)||project.type1.toLowerCase().includes(query)||project.type2.toLowerCase().includes(query)){")
        builder.append("results.push({type:'project',name:project.name,projectIndex:pi,regionIndex:-1});")
        builder.append("}")
        builder.append("project.regions.forEach(function(region,ri){")
        builder.append("if(region.name.toLowerCase().includes(query)||region.type1.toLowerCase().includes(query)||region.type2.toLowerCase().includes(query)){")
        builder.append("results.push({type:'region',name:region.name,projectIndex:pi,regionIndex:ri,parent:project.name});")
        builder.append("}")
        builder.append("});")
        builder.append("});")
        builder.append("if(results.length===0){")
        builder.append("searchResults.innerHTML='<div class=\\'search-result-item\\' style=\\'color:#999;\\'>'+i18n.noResultsFound+'</div>';")
        builder.append("}else{")
        builder.append("let html='';")
        builder.append("results.slice(0,10).forEach(function(r){")
        builder.append("html+='<div class=\\'search-result-item\\' onclick=\\'selectSearchResult('+r.projectIndex+','+r.regionIndex+')\\'>';")
        builder.append("html+='<span class=\\'search-result-type '+r.type+'\\'>'+(r.type==='project'?i18n.projectType:i18n.regionType)+'</span>';")
        builder.append("html+='<span class=\\'search-result-name\\'>'+r.name+'</span>';")
        builder.append("if(r.parent)html+='<span class=\\'search-result-parent\\'>'+r.parent+'</span>';")
        builder.append("html+='</div>';")
        builder.append("});")
        builder.append("searchResults.innerHTML=html;")
        builder.append("}")
        builder.append("searchResults.classList.add('active');")
        builder.append("});")
        builder.append("document.addEventListener('click',function(e){")
        builder.append("if(!searchInput.contains(e.target)&&!searchResults.contains(e.target)){")
        builder.append("searchResults.classList.remove('active');")
        builder.append("}")
        builder.append("});")
        builder.append("function selectSearchResult(projectIndex,regionIndex){")
        builder.append("searchResults.classList.remove('active');")
        builder.append("searchInput.value='';")
        builder.append("if(regionIndex>=0){")
        builder.append("selectRegion(projectIndex,regionIndex);")
        builder.append("}else{")
        builder.append("selectProject(projectIndex);")
        builder.append("}")
        builder.append("}")
        // Select project
        builder.append("function selectProject(index){")
        builder.append("currentProjectIndex=index;")
        builder.append("currentRegionIndex=-1;")
        builder.append("closeViewer();")
        builder.append("document.querySelectorAll('.tree-item').forEach(i=>i.classList.remove('active'));")
        builder.append("const projectEl=document.querySelector('.tree-item.project[data-project-index=\"'+index+'\"]');")
        builder.append("if(projectEl){projectEl.classList.add('active');projectEl.scrollIntoView({behavior:'smooth',block:'nearest'});}")
        builder.append("const p=projectsData[index];")
        builder.append("if(!p)return;")
        builder.append("const infoContent=document.getElementById('infoContent');")
        builder.append("let infoHtml='<div class=\\'project-header\\'><h1>'+p.name+'</h1><div class=\\'meta\\'>'+p.regions.length+' '+i18n.regions+'</div></div>';")
        builder.append("if(p.description)infoHtml+='<div class=\\'info-section\\'><div class=\\'info-label\\'>'+i18n.description+'</div><div class=\\'info-value\\'>'+p.description+'</div></div>';")
        builder.append("if(p.type1)infoHtml+='<div class=\\'info-section\\'><div class=\\'info-label\\'>'+i18n.type1+'</div><div class=\\'info-value\\'>'+p.type1+'</div></div>';")
        builder.append("if(p.type2)infoHtml+='<div class=\\'info-section\\'><div class=\\'info-label\\'>'+i18n.type2+'</div><div class=\\'info-value\\'>'+p.type2+'</div></div>';")
        builder.append("if(p.note)infoHtml+='<div class=\\'info-section\\'><div class=\\'info-label\\'>'+i18n.note+'</div><div class=\\'info-value\\'>'+p.note+'</div></div>';")
        builder.append("infoContent.innerHTML=infoHtml;")
        // Show project photo in media panel
        builder.append("const mediaContent=document.getElementById('mediaContent');")
        builder.append("if(p.photoPath){")
        builder.append("currentMediaList=[{type:'photo',path:p.photoPath}];")
        builder.append("document.getElementById('mediaCount').textContent='1 '+i18n.item;")
        builder.append("mediaContent.innerHTML='<div class=\\'media-grid\\'><div class=\\'media-item\\' onclick=\\'openMediaItem(0)\\'><img src=\\''+p.photoPath+'\\' alt=\\'Photo with areas\\' loading=\\'lazy\\'><div class=\\'media-caption\\'><span class=\\'media-type photo\\'>'+i18n.viewPhotoWithAreas+'</span></div></div></div>';")
        builder.append("}else{")
        builder.append("currentMediaList=[];")
        builder.append("document.getElementById('mediaCount').textContent='0 '+i18n.items;")
        builder.append("mediaContent.innerHTML='<div class=\\'empty-state\\'><div class=\\'empty-state-icon\\'>📷</div><div>'+i18n.noPhotoAvailable+'</div></div>';")
        builder.append("}")
        builder.append("}")
        // Show project photo
        builder.append("function showProjectPhoto(index){")
        builder.append("const p=projectsData[index];")
        builder.append("if(!p||!p.photoPath)return;")
        builder.append("currentMediaList=[{type:'photo',path:p.photoPath}];")
        builder.append("currentMediaIndex=0;")
        builder.append("openViewer();")
        builder.append("document.getElementById('viewerTitle').textContent=i18n.photo+' - '+p.name;")
        builder.append("}")
        // Select region
        builder.append("function selectRegion(projectIndex,regionIndex){")
        builder.append("currentProjectIndex=projectIndex;")
        builder.append("currentRegionIndex=regionIndex;")
        builder.append("closeViewer();")
        builder.append("document.querySelectorAll('.tree-item').forEach(i=>i.classList.remove('active'));")
        builder.append("const regionEl=document.querySelector('.tree-item.region[data-project-index=\"'+projectIndex+'\"][data-region-index=\"'+regionIndex+'\"]');")
        builder.append("if(regionEl){regionEl.classList.add('active');regionEl.scrollIntoView({behavior:'smooth',block:'nearest'});}")
        builder.append("const p=projectsData[projectIndex];")
        builder.append("if(!p)return;")
        builder.append("const r=p.regions[regionIndex];")
        builder.append("if(!r)return;")
        builder.append("const infoContent=document.getElementById('infoContent');")
        builder.append("let infoHtml='<div class=\\'info-section\\'><div class=\\'info-label\\'>'+i18n.regionName+'</div><div class=\\'info-value\\' style=\\'font-size:18px;font-weight:600;\\'>'+r.name+'</div></div>';")
        builder.append("infoHtml+='<div class=\\'info-section\\'><div class=\\'info-label\\'>'+i18n.project+'</div><div class=\\'info-value\\'>'+p.name+'</div></div>';")
        builder.append("infoHtml+='<div class=\\'info-section\\'><div class=\\'info-label\\'>'+i18n.state+'</div><div class=\\'info-value\\'><span class=\\'color-indicator\\' style=\\'background-color:'+r.stateColor+';margin-right:8px;\\'></span>'+r.stateName+'</div></div>';")
        builder.append("if(r.type1)infoHtml+='<div class=\\'info-section\\'><div class=\\'info-label\\'>'+i18n.type1+'</div><div class=\\'info-value\\'>'+r.type1+'</div></div>';")
        builder.append("if(r.type2)infoHtml+='<div class=\\'info-section\\'><div class=\\'info-label\\'>'+i18n.type2+'</div><div class=\\'info-value\\'>'+r.type2+'</div></div>';")
        builder.append("if(r.description)infoHtml+='<div class=\\'info-section\\'><div class=\\'info-label\\'>'+i18n.description+'</div><div class=\\'info-value\\'>'+r.description+'</div></div>';")
        builder.append("if(r.note)infoHtml+='<div class=\\'info-section\\'><div class=\\'info-label\\'>'+i18n.note+'</div><div class=\\'info-value\\'>'+r.note+'</div></div>';")
        // Add files section in info panel
        builder.append("const files=(r.contents||[]).filter(c=>c.type==='file');")
        builder.append("if(files.length>0){")
        builder.append("infoHtml+='<div class=\\'info-section\\'><div class=\\'info-label\\'>'+i18n.files+'</div>';")
        builder.append("files.forEach(function(f){")
        builder.append("const fName=f.path.split('/').pop();")
        builder.append("infoHtml+='<div style=\\'margin:4px 0;\\'><a href=\\''+f.path+'\\' target=\\'_blank\\' style=\\'color:#1976d2;text-decoration:none;\\'>📄 '+fName+'</a>';")
        builder.append("infoHtml+=' <a href=\\''+f.path+'\\' download=\\''+fName+'\\' style=\\'color:#666;font-size:11px;margin-left:8px;\\'>⬇</a></div>';")
        builder.append("});")
        builder.append("infoHtml+='</div>';")
        builder.append("}")
        builder.append("infoContent.innerHTML=infoHtml;")
        builder.append("updateMediaPanel(r);")
        builder.append("}")
        // Update media panel
        builder.append("function updateMediaPanel(region){")
        builder.append("const mediaContent=document.getElementById('mediaContent');")
        builder.append("const mediaOnly=(region.contents||[]).filter(c=>c.type==='photo'||c.type==='video');")
        builder.append("currentMediaList=mediaOnly;")
        builder.append("document.getElementById('mediaCount').textContent=currentMediaList.length+' '+(currentMediaList.length===1?i18n.item:i18n.items);")
        builder.append("if(currentMediaList.length===0){")
        builder.append("mediaContent.innerHTML='<div class=\\'empty-state\\'><div class=\\'empty-state-icon\\'>📷</div><div>'+i18n.noMediaForRegion+'</div></div>';")
        builder.append("return;")
        builder.append("}")
        builder.append("let html='<div class=\\'media-grid\\'>';")
        builder.append("currentMediaList.forEach(function(item,index){")
        builder.append("if(item.type==='photo'){")
        builder.append("html+='<div class=\\'media-item\\' onclick=\\'openMediaItem('+index+')\\'><img src=\\''+item.path+'\\' alt=\\'Photo\\' loading=\\'lazy\\'><div class=\\'media-caption\\'><span class=\\'media-type photo\\'>'+i18n.photo+'</span></div></div>';")
        builder.append("}else if(item.type==='video'){")
        builder.append("html+='<div class=\\'media-item\\' onclick=\\'openMediaItem('+index+')\\'><video src=\\''+item.path+'\\' preload=\\'metadata\\' muted></video><div class=\\'media-caption\\'><span class=\\'media-type video\\'>'+i18n.video+'</span></div></div>';")
        builder.append("}")
        builder.append("});")
        builder.append("html+='</div>';")
        builder.append("mediaContent.innerHTML=html;")
        builder.append("}")
        // Media viewer functions
        builder.append("function openMediaItem(index){currentMediaIndex=index;openViewer();}")
        builder.append("function openViewer(){")
        builder.append("if(currentMediaList.length===0)return;")
        builder.append("resetZoom();")
        builder.append("document.getElementById('mediaContent').style.display='none';")
        builder.append("document.getElementById('mediaViewer').classList.add('active');")
        builder.append("updateViewerContent();")
        builder.append("}")
        builder.append("function closeViewer(){")
        builder.append("document.getElementById('mediaViewer').classList.remove('active');")
        builder.append("document.getElementById('mediaContent').style.display='block';")
        builder.append("}")
        builder.append("function updateViewerContent(){")
        builder.append("if(currentMediaList.length===0)return;")
        builder.append("resetZoom();")
        builder.append("const item=currentMediaList[currentMediaIndex];")
        builder.append("const viewerContent=document.getElementById('viewerContent');")
        builder.append("const viewerTitle=document.getElementById('viewerTitle');")
        builder.append("const viewerCounter=document.getElementById('viewerCounter');")
        builder.append("const zoomControls=document.querySelector('.zoom-controls');")
        builder.append("viewerCounter.textContent=(currentMediaIndex+1)+' / '+currentMediaList.length;")
        builder.append("const p=projectsData[currentProjectIndex];")
        builder.append("const r=p&&currentRegionIndex>=0?p.regions[currentRegionIndex]:null;")
        // Extract filename from path
        builder.append("const fileName=item.path.split('/').pop();")
        builder.append("if(item.type==='photo'){")
        builder.append("viewerContent.innerHTML='<img src=\\''+item.path+'\\' alt=\\'Photo\\' id=\\'viewerImg\\'>';")
        builder.append("viewerTitle.textContent=fileName;")
        builder.append("if(zoomControls)zoomControls.style.display='flex';")
        builder.append("}else if(item.type==='video'){")
        builder.append("viewerContent.innerHTML='<video controls autoplay><source src=\\''+item.path+'\\' type=\\'video/mp4\\'>Your browser does not support video.</video>';")
        builder.append("viewerTitle.textContent=fileName;")
        builder.append("if(zoomControls)zoomControls.style.display='none';")
        builder.append("}")
        builder.append("document.getElementById('btnPrev').disabled=currentMediaIndex===0;")
        builder.append("document.getElementById('btnNext').disabled=currentMediaIndex===currentMediaList.length-1;")
        builder.append("}")
        builder.append("function navigateMedia(direction){")
        builder.append("const newIndex=currentMediaIndex+direction;")
        builder.append("if(newIndex>=0&&newIndex<currentMediaList.length){currentMediaIndex=newIndex;updateViewerContent();}")
        builder.append("}")
        // Zoom functions
        builder.append("let currentZoom=1;")
        builder.append("const zoomStep=0.25;")
        builder.append("const minZoom=0.25;")
        builder.append("const maxZoom=4;")
        builder.append("function updateZoom(){")
        builder.append("const img=document.getElementById('viewerImg');")
        builder.append("if(img){")
        builder.append("img.style.transform='translate('+translateX+'px,'+translateY+'px) scale('+currentZoom+')';")
        builder.append("img.classList.toggle('zoomed',currentZoom!==1);")
        builder.append("}")
        builder.append("document.getElementById('zoomLevel').textContent=Math.round(currentZoom*100)+'%';")
        builder.append("}")
        builder.append("function zoomIn(){")
        builder.append("if(currentZoom<maxZoom){currentZoom=Math.min(maxZoom,currentZoom+zoomStep);updateZoom();}")
        builder.append("}")
        builder.append("function zoomOut(){")
        builder.append("if(currentZoom>minZoom){currentZoom=Math.max(minZoom,currentZoom-zoomStep);updateZoom();}")
        builder.append("}")
        builder.append("function resetZoom(){currentZoom=1;translateX=0;translateY=0;updateZoom();}")
        // Download function - open in new tab
        builder.append("function downloadCurrentMedia(){")
        builder.append("if(currentMediaList.length===0)return;")
        builder.append("const item=currentMediaList[currentMediaIndex];")
        builder.append("window.open(item.path,'_blank');")
        builder.append("}")
        // Drag functionality for zoomed images
        builder.append("let isDragging=false;")
        builder.append("let startX=0,startY=0;")
        builder.append("let translateX=0,translateY=0;")
        builder.append("document.addEventListener('mousedown',function(e){")
        builder.append("const img=document.getElementById('viewerImg');")
        builder.append("if(!img||currentZoom===1)return;")
        builder.append("if(e.target===img){")
        builder.append("isDragging=true;")
        builder.append("startX=e.clientX-translateX;")
        builder.append("startY=e.clientY-translateY;")
        builder.append("img.style.cursor='grabbing';")
        builder.append("e.preventDefault();")
        builder.append("}")
        builder.append("});")
        builder.append("document.addEventListener('mousemove',function(e){")
        builder.append("if(!isDragging)return;")
        builder.append("translateX=e.clientX-startX;")
        builder.append("translateY=e.clientY-startY;")
        builder.append("updateZoom();")
        builder.append("});")
        builder.append("document.addEventListener('mouseup',function(){")
        builder.append("if(isDragging){")
        builder.append("isDragging=false;")
        builder.append("const img=document.getElementById('viewerImg');")
        builder.append("if(img)img.style.cursor='grab';")
        builder.append("}")
        builder.append("});")
        builder.append("document.addEventListener('keydown',function(e){")
        builder.append("if(!document.getElementById('mediaViewer').classList.contains('active'))return;")
        builder.append("if(e.key==='ArrowLeft')navigateMedia(-1);")
        builder.append("else if(e.key==='ArrowRight')navigateMedia(1);")
        builder.append("else if(e.key==='Escape')closeViewer();")
        builder.append("else if(e.key==='+'||e.key==='=')zoomIn();")
        builder.append("else if(e.key==='-')zoomOut();")
        builder.append("else if(e.key==='0')resetZoom();")
        builder.append("});")
        // Resizers
        builder.append("(function(){")
        builder.append("const resizer=document.getElementById('vResizer');")
        builder.append("const leftColumn=document.getElementById('leftColumn');")
        builder.append("let isResizing=false;")
        builder.append("resizer.addEventListener('mousedown',function(e){isResizing=true;resizer.classList.add('active');document.body.style.cursor='col-resize';document.body.style.userSelect='none';});")
        builder.append("document.addEventListener('mousemove',function(e){if(!isResizing)return;const containerWidth=document.querySelector('.container').offsetWidth;const newWidth=Math.max(250,Math.min(e.clientX,containerWidth*0.5));leftColumn.style.width=newWidth+'px';});")
        builder.append("document.addEventListener('mouseup',function(){if(isResizing){isResizing=false;resizer.classList.remove('active');document.body.style.cursor='';document.body.style.userSelect='';}});")
        builder.append("})();")
        builder.append("(function(){")
        builder.append("const resizer=document.getElementById('hResizer');")
        builder.append("const treePanel=document.getElementById('treePanel');")
        builder.append("let isResizing=false;")
        builder.append("resizer.addEventListener('mousedown',function(e){isResizing=true;e.preventDefault();resizer.classList.add('active');document.body.style.cursor='row-resize';document.body.style.userSelect='none';});")
        builder.append("document.addEventListener('mousemove',function(e){if(!isResizing)return;const containerRect=document.getElementById('leftColumn').getBoundingClientRect();const relY=e.clientY-containerRect.top;const containerHeight=containerRect.height-5;const newHeight=Math.max(100,Math.min(relY,containerHeight-150));treePanel.style.flex='0 0 '+newHeight+'px';});")
        builder.append("document.addEventListener('mouseup',function(){if(isResizing){isResizing=false;resizer.classList.remove('active');document.body.style.cursor='';document.body.style.userSelect='';}});")
        builder.append("})();")
        builder.append("</script>")
        builder.append("</body>")
        builder.append("</html>")
        return builder.toString()
    }
}
