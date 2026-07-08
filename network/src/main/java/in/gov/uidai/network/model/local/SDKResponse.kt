package `in`.gov.uidai.network.model.local

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlRootElement

@JacksonXmlRootElement(localName = "Response")
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
data class SDKResponse(
    @JacksonXmlProperty(isAttribute = true, localName = "fullImage")
    val fullImage: String? = null,

    @JacksonXmlProperty(isAttribute = true, localName = "croppedImage")
    val croppedImage: String? = null,
)