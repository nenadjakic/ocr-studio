package com.github.nenadjakic.ocr.studio.entity

data class DocumentMutableList(
    val documents: MutableList<Document> = mutableListOf(),
    var mergedDocumentName: String? = null
)