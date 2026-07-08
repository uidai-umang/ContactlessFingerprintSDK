package app.gov.uidai.capture.domain.method.final_processing

import android.graphics.Bitmap
import app.gov.uidai.capture.utils.extension.toBitmap
import app.gov.uidai.capture.utils.extension.toByteArray
import app.gov.uidai.capture.utils.logExecutionTime
import com.chaquo.python.Python

object FinalProcessingU2Net {

    private val TAG = FinalProcessingU2Net::class.simpleName

    private val py = Python.getInstance()
    private val preprocessingModule = py.getModule("preprocessing_u2net")

    fun run(image: Bitmap, maskedImage: Bitmap?): Bitmap {
        if(maskedImage == null) return image
        try {
            val imageByteArray = image.toByteArray()
            val maskedImageByteArray = maskedImage.toByteArray()

            val result = logExecutionTime(TAG, "Preprocessing U2Net") {
                preprocessingModule.callAttr(
                    "process",
                    imageByteArray,
                    maskedImageByteArray
                )
            }

            val resultImageByteArray = result.toJava(ByteArray::class.java)
            return resultImageByteArray.toBitmap()
        } catch (e: Exception) {
            return image
        }
    }
}