package app.gov.uidai.registration.model

import android.graphics.Bitmap
import `in`.gov.uidai.embedding.model.FingerQuality

open class Fingerprint(
    val fingerPosition: FingerPosition,
    val embedding: ByteArray,
    val fingerQuality: FingerQuality? = null
)

class CLFingerprint(
    fingerPosition: FingerPosition,
    embedding: ByteArray,
    fingerQuality: FingerQuality? = null,
    val jp2ByteArray: ByteArray,
    val bitmap: Bitmap,
    val imageBytes: ByteArray = jp2ByteArray,
    val blurScore: Double = 0.0,
    val brightnessScore: Double = 0.0,
    val glareScore: Double = 0.0
) : Fingerprint(
    fingerPosition = fingerPosition,
    embedding = embedding,
    fingerQuality = fingerQuality
) {

    fun copy(
        fingerPosition: FingerPosition = this.fingerPosition,
        embedding: ByteArray = this.embedding,
        jp2ByteArray: ByteArray = this.jp2ByteArray,
        fingerQuality: FingerQuality? = this.fingerQuality,
        bitmap: Bitmap = this.bitmap,
        imageBytes: ByteArray = this.imageBytes,
        blurScore: Double = this.blurScore,
        brightnessScore: Double = this.brightnessScore,
        glareScore: Double = this.glareScore
    ): CLFingerprint = CLFingerprint(
        fingerPosition = fingerPosition,
        embedding = embedding,
        fingerQuality = fingerQuality,
        jp2ByteArray = jp2ByteArray,
        bitmap = bitmap,
        imageBytes = imageBytes,
        blurScore = blurScore,
        brightnessScore = brightnessScore,
        glareScore = glareScore
    )
}

class CBFingerprint(
    fingerPosition: FingerPosition,
    embedding: ByteArray,
    fingerQuality: FingerQuality? = null
) : Fingerprint(
    fingerPosition = fingerPosition,
    embedding = embedding,
    fingerQuality = fingerQuality
)
