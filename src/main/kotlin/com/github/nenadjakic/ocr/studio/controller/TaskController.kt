package com.github.nenadjakic.ocr.studio.controller

import com.github.nenadjakic.ocr.studio.dto.*
import com.github.nenadjakic.ocr.studio.entity.OcrConfig
import com.github.nenadjakic.ocr.studio.entity.SchedulerConfig
import com.github.nenadjakic.ocr.studio.entity.Task
import com.github.nenadjakic.ocr.studio.exception.IllegalStateOcrException
import com.github.nenadjakic.ocr.studio.exception.MissingDocumentOcrException
import com.github.nenadjakic.ocr.studio.extension.collectionMap
import com.github.nenadjakic.ocr.studio.service.TaskService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Encoding
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.modelmapper.ModelMapper
import org.springframework.core.io.ByteArrayResource
import org.springframework.core.io.Resource
import org.springframework.data.domain.Page
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.*
import org.springframework.web.multipart.MultipartFile
import org.springframework.web.servlet.support.ServletUriComponentsBuilder
import java.util.UUID

@Tag(name = "Task controller", description = "API endpoints for managing task entities.")
@RestController
@RequestMapping("/task")
@Validated
open class TaskController(
    private val modelMapper: ModelMapper,
    private val taskService: TaskService
) {

    @Operation(
        operationId = "findAllTasks",
        summary = "Get all tasks.",
        description = "Returns all tasks.")
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "Successfully retrieved tasks.")
        ]
    )
    @GetMapping(produces = [MediaType.APPLICATION_JSON_VALUE])
    fun findAll(): ResponseEntity<List<Task>> = ResponseEntity.ok(taskService.findAll())

    @Operation(
        operationId = "findPageWithTasks",
        summary = "Get tasks by page.",
        description = "Returns a page of tasks based on page number and page size.")
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "Successfully retrieved page of tasks.")
        ]
    )
    @GetMapping(value = ["/page"], produces = [MediaType.APPLICATION_JSON_VALUE])
    fun findPage(@RequestParam pageNumber: Int, @RequestParam(required = false) pageSize: Int?): ResponseEntity<Page<Task>> = ResponseEntity.ok(taskService.findPage(pageNumber, pageSize ?: 20))

    @Operation(
        operationId = "findTaskById",
        summary = "Get task by id.",
        description = "Returns an task with the specified id."
    )
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "Successfully retrieved task."),
            ApiResponse(responseCode = "404", description = "Task not found.")
        ]
    )
    @GetMapping(value = ["/{id}"], produces = [MediaType.APPLICATION_JSON_VALUE])
    fun findById(@PathVariable id: UUID): ResponseEntity<Task> = ResponseEntity.ofNullable(taskService.findById(id))

    @Operation(
        operationId = "downloadInDocumentsById",
        summary = "Get in documents as archive file.",
        description = "Returns in documents as archive file with specified id."
    )
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "Successfully downloaded in documents.")
        ]
    )
    @GetMapping(value = ["download/in/{id}"])
    fun downloadInDocuments(@PathVariable id: UUID) =
        getResourceEntity(taskService.findInDocuments(id))

    @Operation(
        operationId = "downloadInDocumentByIdAndRandomizedName",
        summary = "Get in document.",
        description = "Returns in document file with specified id and randomized name."
    )
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "Successfully downloaded in document.")
        ]
    )
    @GetMapping(value = ["download/in/{id}/{randomizedFileName}"])
    fun downloadInDocumentByRandomizedName(@PathVariable id: UUID,  @PathVariable randomizedFileName: String) =
        getResourceEntity(taskService.findInDocument(id, randomizedFileName))

    @Operation(
        operationId = "downloadOutDocumentsById",
        summary = "Get out document/documents.",
        description = "Returns out document file/s with specified id."
    )
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "Successfully downloaded out documents.")
        ]
    )
    @GetMapping(value = ["download/out/{id}"])
    fun downloadOutDocument(@PathVariable id: UUID) {
        getResourceEntity(taskService.findOutDocuments(id))
    }

    @Operation(
        operationId = "createTask",
        summary = "Create task.",
        description = "Creates a new task based on the provided model.",
        requestBody =
        io.swagger.v3.oas.annotations.parameters.RequestBody(
            content = [Content(encoding = [Encoding(name = "model", contentType = "application/json")]
            )]
        )
    )
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "201", description = "Task created successfully."),
            ApiResponse(responseCode = "400", description = "Invalid request data.")
        ]
    )
    @PostMapping(consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
    fun create(
        @Valid @RequestPart(name = "model") model: TaskAddRequest,
        @RequestPart(value = "files", required = false) files: Collection<MultipartFile>?
    ): ResponseEntity<Void> {
        val task = modelMapper.map(model, Task::class.java)
        return insert(task, files)
    }

    @Operation(
        operationId = "createDraftTask",
        summary = "Create draft task.",
        description = "Creates a new task based on the provided model."
    )
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "201", description = "Task created successfully."),
            ApiResponse(responseCode = "400", description = "Invalid request data.")
        ]
    )
    @PostMapping(value = ["/draft"], consumes = [MediaType.APPLICATION_JSON_VALUE])
    fun create(@Valid @RequestBody model: TaskDraftRequest): ResponseEntity<Void> {
        val task = modelMapper.map(model, Task::class.java)
        return insert(task)
    }

    @Operation(
        operationId = "updateTaskConfig",
        summary = "Updates ocr configuration for task with given id.",
        description = "Updates ocr configuration for task with given id."
    )
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "204", description = "Task's ocr configuration successfully updated."),
            ApiResponse(responseCode = "400", description = "Invalid request data.")
        ]
    )
    @PutMapping("/config/{id}")
    fun update(@PathVariable id: UUID, @RequestBody ocrConfigRequest: OcrConfigRequest): ResponseEntity<Void> {
        val ocrConfig = modelMapper.map(ocrConfigRequest, OcrConfig::class.java)
        taskService.update(id, ocrConfig)
        return ResponseEntity.noContent().build()
    }

    @Operation(
        operationId = "updateTaskScheduler",
        summary = "Updates scheduler configuration for task with given id.",
        description = "Updates scheduler configuration for task with given id."
    )
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "204", description = "Task's scheduler configuration successfully updated."),
            ApiResponse(responseCode = "400", description = "Invalid request data.")
        ]
    )
    @PutMapping("/scheduler/{id}")
    fun update(@PathVariable id: UUID, @RequestBody schedulerConfigRequest: SchedulerConfigRequest): ResponseEntity<Void> {
        val schedulerConfig = modelMapper.map(schedulerConfigRequest, SchedulerConfig::class.java)
        taskService.update(id, schedulerConfig)
        return ResponseEntity.noContent().build()
    }

    @Operation(
        operationId = "updateTaskLanguage",
        summary = "Updates language of ocr configuration for task with given id.",
        description = "Updates language of ocr configuration for task with given id."
    )
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "204", description = "Task's language of ocr configuration successfully updated."),
            ApiResponse(responseCode = "400", description = "Invalid request data.")
        ]
    )
    @PatchMapping("/language/{id}")
    fun updateLanguage(@PathVariable id: UUID, @RequestParam(required = true) language: String): ResponseEntity<Void> {
        taskService.update(id, language)
        return ResponseEntity.noContent().build()
    }

    @Operation(
        operationId = "uploadFiles",
        summary = "Upload files and create task's in documents for given id.",
        description = "Upload files and create task's in documents for given id."
    )
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "204", description = "Task's in documents configuration successfully updated created and files successfully uploaded."),
            ApiResponse(responseCode = "400", description = "Invalid request data.")
        ]
    )
    @PutMapping("upload/{id}", consumes = [MediaType.MULTIPART_FORM_DATA_VALUE], produces = [MediaType.APPLICATION_JSON_VALUE])
    fun uploadFile(
        @PathVariable id: UUID,
        @RequestPart("files") multipartFiles: Collection<MultipartFile>
    ): ResponseEntity<List<UploadDocumentResponse>> = ResponseEntity.ok(modelMapper.collectionMap(taskService.upload(id, multipartFiles), UploadDocumentResponse::class.java))

    @Operation(
        operationId = "removeFile",
        summary = "Remove file or all files and document from task.",
        description = "Remove file or all files (in case that param originalFileName is not give) and document from task."
    )
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "204", description = "File removed from file system successfully. Also document task updated successfully."),
            ApiResponse(responseCode = "400", description = "Invalid request data.")
        ]
    )
    @DeleteMapping("/file/{id}")
    @Throws(MissingDocumentOcrException::class)
    fun removeFile(@PathVariable id: UUID, @RequestParam(required = false) originalFileName: String): ResponseEntity<Void> {
        if (originalFileName.isEmpty()) {
            taskService.removeAllFiles(id)
        } else {
            taskService.removeFile(id, originalFileName)
        }
        return ResponseEntity.noContent().build()
    }

    @Operation(
        operationId = "deleteTask",
        summary = "Delete task and remove all files.",
        description = "Delete task and remove all files."
    )
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "204", description = "Task deleted and all files removed from file system successfully."),
            ApiResponse(responseCode = "400", description = "Invalid request data.")
        ]
    )
    @DeleteMapping("/{id}")
    @Throws(MissingDocumentOcrException::class, IllegalStateOcrException::class)
    fun deleteById (@PathVariable id: UUID) {
        taskService.deleteById(id)
    }

    private fun insert(task: Task, files: Collection<MultipartFile>? = null): ResponseEntity<Void> {
        val createdTask = taskService.insert(task, files)

        val location = ServletUriComponentsBuilder
            .fromCurrentRequest()
            .path("/{id}")
            .buildAndExpand(createdTask.id)
            .toUri()

        return ResponseEntity.created(location).build()
    }

    private fun getResourceEntity(resouceByName: Pair<String, ByteArrayResource>?): ResponseEntity<ByteArrayResource?> =
        ResponseEntity.ok()
            .headers { headers ->
                headers.setContentDispositionFormData("attachment", resouceByName?.first)
                headers.contentType = MediaType.APPLICATION_OCTET_STREAM
            }
            .body(resouceByName?.second)
}