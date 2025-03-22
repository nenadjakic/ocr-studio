package com.github.nenadjakic.ocr.studio.service

import com.github.nenadjakic.ocr.studio.config.MessageConst
import com.github.nenadjakic.ocr.studio.config.OcrProperties
import com.github.nenadjakic.ocr.studio.entity.*
import com.github.nenadjakic.ocr.studio.exception.IllegalStateOcrException
import com.github.nenadjakic.ocr.studio.exception.MissingDocumentOcrException
import com.github.nenadjakic.ocr.studio.repository.TaskRepository
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry
import org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream
import org.springframework.core.io.ByteArrayResource
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.stereotype.Service
import org.springframework.web.multipart.MultipartFile
import java.io.BufferedOutputStream
import java.io.ByteArrayOutputStream
import java.io.FileInputStream
import java.io.OutputStream
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

    private fun getResource(inDocuments: DocumentMutableList, id: UUID): ByteArrayResource? {
        if (inDocuments.isEmpty()) {
            return null
        }

        ByteArrayOutputStream().use { zipByteArrayOutputStream ->
            BufferedOutputStream(zipByteArrayOutputStream).use { zipBufferedOutputStream ->
                ZipArchiveOutputStream(zipBufferedOutputStream).use { zipArchiveOutputStream ->
                    inDocuments.forEach {
                        var file = taskFileSystemService.getInputFile(id, it.randomizedFileName)
                        val fileExtension = getFileExtension(it.type)

                        FileInputStream(file).use { fileInputStream ->
                            val zipEntry = ZipArchiveEntry("${file.name}${fileExtension}")
                            zipArchiveOutputStream.putArchiveEntry(zipEntry)

                            val bytes = ByteArray(1024)
                            var length: Int
                            while (fileInputStream.read(bytes).also { length = it } >= 0) {
                                zipArchiveOutputStream.write(bytes, 0, length)
                            }

                            zipArchiveOutputStream.closeArchiveEntry()
                        }

                    }
                    zipArchiveOutputStream.finish()
                }
            }
            return ByteArrayResource(zipByteArrayOutputStream.toByteArray())
        }
    }

    private fun getFileExtension(type: String?): String =
        if (type != null) {
            when (type) {
                "text/plain" -> ".txt"
                "application/pdf" -> ".pdf"
                "image/jpeg" -> ".jpg"
                "image/png" -> ".png"
                else -> ""
            }
        } else {
            ""
        }

    fun findInDocuments(id: UUID): Pair<String, ByteArrayResource>? {
        val inDocuments = taskRepository.findById(id).orElse(null).let { it.inDocuments }
        return if (inDocuments.isEmpty()) {
            null
        } else {
            val byteArrayResource = getResource(inDocuments, id)
            if (byteArrayResource == null) {
                null
            } else {
                Pair("${id}.zip", byteArrayResource)
            }
        }
    }

    fun findInDocument(id: UUID, randomizedFileName: String): Pair<String, ByteArrayResource>? {
        val inDocument = taskRepository.findById(id).orElse(null)?.let {
            it.inDocuments.firstOrNull { document -> document.randomizedFileName == randomizedFileName }
        }

        return if (inDocument == null) {
            null
        } else {
            val file = taskFileSystemService.getInputFile(id, inDocument.randomizedFileName)
            val fileExtension =
                getFileExtension(inDocument.type)
            Pair("${id}-${inDocument.randomizedFileName}${fileExtension}", ByteArrayResource(file.readBytes()))
        }
    }

    fun findOutDocuments(id: UUID): Pair<String, ByteArrayResource>? {
        val task = taskRepository.findById(id).orElse(null)
        return if (task == null) {
            null
        } else {
            val inDocuments = task.inDocuments

            return if (inDocuments.mergedDocumentName != null) {
                    val file = taskFileSystemService.getOutputFile(id, inDocuments.mergedDocumentName!!)

                    Pair("${inDocuments.mergedDocumentName}.${task.ocrConfig.fileFormat.getExtension()}", ByteArrayResource(file.readBytes()))
                } else {
                    val byteArrayResource = getResource(inDocuments, id)
                    if (byteArrayResource == null) {
                        null
                    } else {
                        return Pair("${id}.zip", byteArrayResource)
                    }
                }
        }
    }

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
            .also { delete(it) }

    fun upload(id: UUID, multipartFiles: Collection<MultipartFile>): List<Document> {
        val task = taskRepository.findById(id)
            .orElseThrow { MissingDocumentOcrException(MessageConst.MISSING_DOCUMENT.description) }

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
                    taskFileSystemService.deleteFile(
                        taskFileSystemService.getInputFile(id, it.randomizedFileName).toPath()
                    )
                    task.inDocuments.remove(it)
                }
                taskRepository.save(task)
            }
    }

    fun removeAllFiles(task: Task) {
        if (task.ocrProgress.status != Status.CREATED) {
            throw IllegalStateException(MessageConst.ILLEGAL_STATUS.description)
        }
        task.inDocuments.forEach {
            taskFileSystemService.deleteFile(
                taskFileSystemService.getInputFile(
                    task.id!!,
                    it.randomizedFileName
                ).toPath()
            )
        }
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