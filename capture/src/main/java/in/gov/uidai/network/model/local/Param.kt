package `in`.gov.uidai.network.model.local

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlRootElement

@JacksonXmlRootElement(localName = "Param")
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
data class Param(
    @JacksonXmlProperty(isAttribute = true, localName = "name")
    val name: String, // Name of the parameter

    @JacksonXmlProperty(isAttribute = true, localName = "value")
    val value: String, // Value of the parameter
)