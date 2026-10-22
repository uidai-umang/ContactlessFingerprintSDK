package `in`.gov.uidai.network.model.local


import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlElementWrapper
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlRootElement
import com.fasterxml.jackson.annotation.JsonIgnoreProperties


@JacksonXmlRootElement(localName = "CustOpts")
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
data class CustOpts(
    @JacksonXmlElementWrapper(useWrapping = false)
    @JsonProperty("Param")
    val param: List<Param>
)
