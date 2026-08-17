package `in`.gov.uidai.embedding

import android.content.Context
import `in`.gov.uidai.embedding.model.FingerQuality
import javax.inject.Inject

/**
 * A demonstration implementation of the [FingerEmbedder] interface.
 *
 * This class provides a stubbed, non-functional embedder intended for testing,
 * scaffolding, or SDK integration examples. All methods currently return
 * placeholder values and must be replaced with production logic.
 *
 * ### Behavior
 * - [isInitialized] always returns `false`.
 * - [initialize] is a no-op.
 * - [embed] returns the input [ByteArray] unchanged, paired with a dummy [FingerQuality].
 * - [match] always returns a fixed score of `100f`.
 *
 * ### Intended Use
 * - As a template for developers implementing their own embedding logic.
 * - As a mock implementation for unit tests or UI prototyping.
 *
 * ### Limitations
 * - Does not perform real fingerprint embedding or matching.
 * - Always reports uninitialized state.
 * - Always returns constant or dummy values.
 */
class DemoFingerEmbedderImpl @Inject constructor(): FingerEmbedder {
    override val isInitialized: Boolean
        get() = false

    override suspend fun initialize(context: Context) {
        // TODO
    }

    override fun embed(byteArray: ByteArray): Pair<ByteArray, FingerQuality> {
        // TODO
        val dummyFingerQuality = FingerQuality(
            overallScore = 0.0,
            compliance = "NA",
            attributes = listOf(),
            comment = "NA"
        )
        return byteArray to dummyFingerQuality
    }

    override fun match(
        currEmbeddings: ByteArray,
        savedEmbeddings: ByteArray
    ): Float {
        // TODO
        return 100f
    }
}

