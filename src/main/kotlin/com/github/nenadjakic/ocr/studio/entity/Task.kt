package com.github.nenadjakic.ocr.studio.entity

import org.springframework.data.annotation.*
import org.springframework.data.mongodb.core.mapping.Document
import org.springframework.data.mongodb.core.mapping.Field
import java.util.*

@Document(collection = "ocr_collection")
class Task(
    @Id @Field(name = "_id") var id: UUID? = null
) : Auditable<UUID>() {

    lateinit var name: String
    var ocrConfig: OcrConfig = OcrConfig()
    var schedulerConfig: SchedulerConfig = SchedulerConfig()
    var ocrProgress: OcrProgress = OcrProgress()
    var inDocuments: DocumentMutableList = DocumentMutableList()
}