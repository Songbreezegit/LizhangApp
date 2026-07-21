package com.yangsong.lizhang.data.ocr

import android.content.Context
import android.net.Uri
import androidx.room.withTransaction
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions
import com.yangsong.lizhang.data.local.LiZhangDatabase
import com.yangsong.lizhang.data.local.entity.ContactEntity
import com.yangsong.lizhang.data.local.entity.GiftRecordEntity
import com.yangsong.lizhang.domain.model.EventType
import com.yangsong.lizhang.domain.model.GiftDirection
import com.yangsong.lizhang.domain.ocr.OcrImportDraft
import com.yangsong.lizhang.domain.ocr.OcrTextLine
import com.yangsong.lizhang.domain.repository.OcrImportRepository
import com.yangsong.lizhang.domain.repository.OcrRecognitionRepository
import java.time.ZoneId
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

class MlKitOcrRecognitionRepository(private val context: Context) : OcrRecognitionRepository {
    private val recognizer = TextRecognition.getClient(ChineseTextRecognizerOptions.Builder().build())

    override suspend fun recognize(imageUri: String): List<OcrTextLine> {
        val image = InputImage.fromFilePath(context, Uri.parse(imageUri))
        return suspendCoroutine { continuation ->
            recognizer.process(image)
                .addOnSuccessListener { result ->
                    continuation.resume(
                        result.textBlocks.flatMap { block ->
                            block.lines.mapNotNull { line ->
                                val bounds = line.boundingBox ?: return@mapNotNull null
                                OcrTextLine(
                                    text = line.text,
                                    confidence = line.confidence,
                                    left = bounds.left,
                                    top = bounds.top,
                                    right = bounds.right,
                                    bottom = bounds.bottom,
                                )
                            }
                        },
                    )
                }
                .addOnFailureListener(continuation::resumeWithException)
        }
    }
}

class RoomOcrImportRepository(private val database: LiZhangDatabase) : OcrImportRepository {
    override suspend fun import(records: List<OcrImportDraft>): Int = database.withTransaction {
        val contactDao = database.contactDao()
        val giftRecordDao = database.giftRecordDao()
        val contactsByName = contactDao.getAllForBackup().associateBy { it.name.trim() }.toMutableMap()
        val now = System.currentTimeMillis()
        val entities = records.map { draft ->
            val normalizedName = draft.name.trim()
            val contact = contactsByName[normalizedName] ?: ContactEntity(
                id = contactDao.insert(ContactEntity(name = normalizedName, notes = "由礼簿识别创建", createdTime = now)),
                name = normalizedName,
                notes = "由礼簿识别创建",
                createdTime = now,
            ).also { contactsByName[normalizedName] = it }
            GiftRecordEntity(
                contactId = contact.id,
                amountInCents = draft.amountInCents,
                eventType = EventType.OTHER,
                eventDate = draft.date.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli(),
                direction = GiftDirection.RECEIVED,
                notes = "由礼簿识别导入",
                createdTime = now,
            )
        }
        giftRecordDao.insertAll(entities)
        entities.size
    }
}
