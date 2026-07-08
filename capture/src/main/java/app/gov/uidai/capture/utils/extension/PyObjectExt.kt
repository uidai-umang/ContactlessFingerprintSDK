package app.gov.uidai.capture.utils.extension

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import com.chaquo.python.PyObject

fun PyObject.getBool(name: String): Boolean {
    val resultMap: Map<PyObject, PyObject> = this.asMap()
    val pyVal = resultMap[PyObject.fromJava(name)]
    return pyVal?.toBoolean() ?: false
}

fun PyObject.getInt(name: String): Int {
    val resultMap: Map<PyObject, PyObject> = this.asMap()
    val pyVal = resultMap[PyObject.fromJava(name)]
    return pyVal?.toInt() ?: 0
}

fun PyObject.getFloat(name: String): Float {
    val resultMap: Map<PyObject, PyObject> = this.asMap()
    val pyVal = resultMap[PyObject.fromJava(name)]
    return pyVal?.toFloat() ?: 0f
}

fun PyObject.getStatus(): Int {
    val resultMap: Map<PyObject, PyObject> = this.asMap()
    val pyVal = resultMap[PyObject.fromJava("status")]
    return pyVal?.toInt() ?: 0
}

fun PyObject.getConfidence(): Float {
    val resultMap: Map<PyObject, PyObject> = this.asMap()
    val pyVal = resultMap[PyObject.fromJava("confidence")]
    return pyVal?.toFloat() ?: 0f
}

fun PyObject.getBox(): Rect {
    // Convert to a strongly-typed Map
    val resultMap: Map<PyObject, PyObject> = this.asMap()
    val pyVal = resultMap[PyObject.fromJava("box")]
    return pyVal?.let { res ->
        val rect = res.asList().map { it.toFloat() }
        Rect(offset = Offset(rect[0], rect[1]), size = Size(rect[2], rect[3]))
    } ?: Rect(offset = Offset(0f, 0f), size = Size(0f, 0f))
}