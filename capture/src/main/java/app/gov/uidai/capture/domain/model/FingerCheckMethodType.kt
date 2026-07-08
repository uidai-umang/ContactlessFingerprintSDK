package app.gov.uidai.capture.domain.model

enum class FingerCheckMethodType {
    MediapipeSelfieSegmenter,
    MediapipeDeepLabV3,
    PythonHSVContourDetection
}