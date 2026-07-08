package `in`.gov.uidai.utility.mapper

import com.fasterxml.jackson.dataformat.xml.JacksonXmlModule
import com.fasterxml.jackson.dataformat.xml.XmlMapper
import com.fasterxml.jackson.module.kotlin.registerKotlinModule

object XmlMapper {
    // XmlMapper with configuration and KotlinModule
    private val xmlModule = JacksonXmlModule().apply {
        setDefaultUseWrapper(false) // Example configuration
    }

    @PublishedApi
    internal val configuredXmlMapper = XmlMapper(xmlModule).registerKotlinModule()

    inline fun <reified T> read(content: String): T {
        return configuredXmlMapper.readValue(content, T::class.java)
    }

    fun <T> write(value: T): String {
        return configuredXmlMapper.writeValueAsString(value)
    }

    fun <T> writePretty(value: T): String {
        return configuredXmlMapper.writerWithDefaultPrettyPrinter().writeValueAsString(value)
    }
}