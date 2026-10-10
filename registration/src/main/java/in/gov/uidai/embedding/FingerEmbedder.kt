package `in`.gov.uidai.embedding

import android.content.Context
import `in`.gov.uidai.embedding.model.FingerQuality

/**
 * Contract for components that can generate and compare fingerprint embeddings.
 *
 * A [FingerEmbedder] is responsible for:
 * - Managing its own initialization lifecycle.
 * - Converting raw fingerprint image data into embeddings with associated quality metadata.
 * - Comparing embeddings to produce a similarity score.
 *
 * Implementations may wrap native libraries, ML models, or external SDKs.
 * Consumers should always check [isInitialized] before invoking [embed] or [match].
 */
interface FingerEmbedder {
    /**
     * Indicates whether the embedder has been successfully initialized.
     *
     * This flag should return `true` only after [initialize] has completed successfully.
     */
    val isInitialized: Boolean

    /**
     * Initializes the embedder with the given [context].
     *
     * Typical responsibilities include:
     * - Loading native libraries or ML models.
     * - Allocating resources required for embedding and matching.
     *
     * This method is `suspend` to allow asynchronous initialization (e.g. I/O, model loading).
     *
     * @param context the Android [Context] used for initialization.
     * @throws IllegalStateException if initialization fails.
     */
    suspend fun initialize(context: Context)

    /**
     * Generates an embedding vector from the given fingerprint [byteArray].
     *
     * @param byteArray raw fingerprint image data, typically in grayscale format.
     * @return a [Pair] consisting of:
     *  - the generated embedding as a [ByteArray]
     *  - a [FingerQuality] assessment of the input data
     *
     * @throws IllegalStateException if called before [initialize].
     * @throws IllegalArgumentException if the input data is invalid.
     */
    fun embed(byteArray: ByteArray): Pair<ByteArray, FingerQuality>

    /**
     * Compares two fingerprint embeddings and returns a similarity score.
     *
     * @param currEmbeddings the embedding of the current fingerprint sample.
     * @param savedEmbeddings the embedding of a previously stored fingerprint.
     * @return a [Float] similarity score, where higher values indicate greater similarity.
     *
     * Implementations should document the score range (e.g. 0.0–1.0).
     *
     * @throws IllegalStateException if called before [initialize].
     * @throws IllegalArgumentException if either embedding is invalid.
     */
    fun match(currEmbeddings: ByteArray, savedEmbeddings: ByteArray): Float
}