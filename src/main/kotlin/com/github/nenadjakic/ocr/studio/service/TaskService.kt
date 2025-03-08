package com.github.nenadjakic.ocr.studio.service

import com.github.nenadjakic.ocr.studio.config.MessageConst
import com.github.nenadjakic.ocr.studio.config.OcrProperties
import com.github.nenadjakic.ocr.studio.entity.*
import com.github.nenadjakic.ocr.studio.exception.IllegalStateOcrException
import com.github.nenadjakic.ocr.studio.exception.MissingDocumentOcrException
import com.github.nenadjakic.ocr.studio.repository.TaskRepository
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.stereotype.Service
import org.springframework.web.multipart.MultipartFile
import java.util.*

@Service
class TaskService(
    private val taskRepository: TaskRepository,
    private val taskFileSystemService: TaskFileSystemService,
    private val ocrProperties: OcrProperties
) {

    fun findAll(): List<Task> =
        taskRepository.findAll(Sort.by(Sort.Order.asc("id")))

    fun findById(id: UUID): Task? =
        taskRepository.findById(id).orElse(null)

    fun findPage(pageNumber: Int, pageSize: Int): Page<Task> =
        taskRepository.findAll(PageRequest.of(pageNumber, pageSize, Sort.by(Sort.Order.asc("id"))))

    fun insert(task: Task, files: Collection<MultipartFile>? = emptyList()): Task {
        val createdEntity = insert(task)
        if (!files.isNullOrEmpty()) {
            upload(createdEntity.id!!, files)
        }
        return createdEntity
    }

    private fun insert(task: Task): Task =
        task.apply {
            id = UUID.randomUUID()
            taskFileSystemService.createDirectories(id!!)
        }.let(taskRepository::insert)

    fun update(entity: Task): Task =
        taskRepository.save(entity)

    fun delete(task: Task) {
        if (task.ocrProgress.status != Status.CREATED) {
            throw IllegalStateOcrException(MessageConst.ILLEGAL_STATUS.description)
        }

        removeAllFiles(task)
        taskRepository.delete(task)
    }

    fun deleteById(id: UUID) =
        taskRepository
            .findById(id)
            .orElseThrow { MissingDocumentOcrException(MessageConst.MISSING_DOCUMENT.description) }
            .also  { delete(it) }

    fun upload(id: UUID, multipartFiles: Collection<MultipartFile>): List<Document> {
        val task = taskRepository.findById(id).orElseThrow { MissingDocumentOcrException(MessageConst.MISSING_DOCUMENT.description) }

        val createdDocuments = multipartFiles.map { multipartFile ->
            Document(multipartFile.originalFilename!!, UUID.randomUUID().toString()).apply {
                type = TaskFileSystemService.getContentType(multipartFile)
            }.also { document ->
                taskFileSystemService.uploadFile(multipartFile, id, document.randomizedFileName)
                task.addInDocument(document)
            }
        }

        taskRepository.save(task)
        return createdDocuments
    }

    fun removeFile(id: UUID, originalFileName: String) {
        taskRepository.findById(id)
            .orElseThrow { MissingDocumentOcrException(MessageConst.MISSING_DOCUMENT.description) }
            .let { task ->
                if (task.ocrProgress.status != Status.CREATED) {
                    throw IllegalStateException(MessageConst.ILLEGAL_STATUS.description)
                }

                task.inDocuments.find { it.originalFileName == originalFileName }?.let {
                    taskFileSystemService.deleteFile(taskFileSystemService.getInputFile(id, it.randomizedFileName).toPath())
                    task.inDocuments.remove(it)
                }
                taskRepository.save(task)
            }
    }

    fun removeAllFiles(task: Task) {
        if (task.ocrProgress.status != Status.CREATED) {
            throw IllegalStateException(MessageConst.ILLEGAL_STATUS.description)
        }
        task.inDocuments.forEach { taskFileSystemService.deleteFile(taskFileSystemService.getInputFile(task.id!!, it.randomizedFileName).toPath()) }
        task.inDocuments.clear()
        taskRepository.save(task)
    }

    fun removeAllFiles(id: UUID) =
        taskRepository.findById(id)
            .orElseThrow { MissingDocumentOcrException(MessageConst.MISSING_DOCUMENT.description) }
            .also { removeAllFiles(it) }

    fun update(id: UUID, properties: Map<Object, Object>) {
        TODO()
    }

    fun update(id: UUID, language: String): Int =
        taskRepository.updateLanguageById(id, language)

    fun update(id: UUID, ocrConfig: OcrConfig): Int =
        taskRepository.updateOcrConfigById(id, ocrConfig)

    fun update(id: UUID, schedulerConfig: SchedulerConfig): Int =
        taskRepository.updateSchedulerConfigById(id, schedulerConfig)
}