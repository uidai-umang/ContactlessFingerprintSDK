package `in`.gov.uidai.network.model.local

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlRootElement

@JacksonXmlRootElement(localName = "PidOptions")
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
data class PidOptions(
    @JacksonXmlProperty(isAttribute = true, localName = "ver")
    val ver: String,

    @JacksonXmlProperty(isAttribute = true, localName = "env")
    val env: String,

    @JacksonXmlProperty(localName = "Opts")
    val opts: Opts? = null,

    @JacksonXmlProperty(localName = "CustOpts")
    val custOpts: CustOpts? = null
)

@JacksonXmlRootElement(localName = "Opts")
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
data class Opts(
    val fCount: String? = null,
    val fType: String? = null,
    val iCount: String? = null,
    val iType: String? = null,
    val pCount: String? = null,
    val pType: String? = null,
    val format: String? = null,
    val pidVer: String? = null,
    val timeout: String? = null,
    val otp: String? = null,
    val wadh: String? = null,
    val posh: String? = null
)