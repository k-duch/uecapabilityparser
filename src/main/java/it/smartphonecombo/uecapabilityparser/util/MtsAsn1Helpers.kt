package it.smartphonecombo.uecapabilityparser.util

import com.ericsson.mts.asn1.ASN1Converter
import com.ericsson.mts.asn1.ASN1Lexer
import com.ericsson.mts.asn1.ASN1Parser
import com.ericsson.mts.asn1.ASN1Translator
import com.ericsson.mts.asn1.KotlinJsonFormatWriter
import com.ericsson.mts.asn1.PERTranslatorFactory
import com.ericsson.mts.asn1.converter.AbstractConverter
import it.smartphonecombo.uecapabilityparser.extension.decodeHex
import it.smartphonecombo.uecapabilityparser.extension.getArrayAtPath
import it.smartphonecombo.uecapabilityparser.extension.getString
import it.smartphonecombo.uecapabilityparser.extension.preformatHex
import it.smartphonecombo.uecapabilityparser.model.Rat
import java.io.InputStream
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import org.antlr.v4.runtime.CharStreams
import org.antlr.v4.runtime.CommonTokenStream
import org.antlr.v4.runtime.atn.PredictionMode
import org.antlr.v4.runtime.tree.ParseTree

object MtsAsn1Helpers {

    private val lteTree: ParseTree by
        lazy(LazyThreadSafetyMode.PUBLICATION) {
            val definition = getResourceAsStream("/definition/EUTRA-RRC-Definitions.asn")!!
            parseTree(definition)
        }

    private val nrTree: ParseTree by
        lazy(LazyThreadSafetyMode.PUBLICATION) {
            val definition = getResourceAsStream("/definition/NR-RRC-Definitions.asn")!!
            parseTree(definition)
        }

    fun getAsn1Converter(rat: Rat, converter: AbstractConverter): ASN1Converter {
        val tree =
            if (rat == Rat.EUTRA) {
                lteTree
            } else {
                nrTree
            }
        return ASN1Converter(converter, tree)
    }

    private fun getAsn1Translator(rat: Rat): ASN1Translator {
        val tree =
            if (rat == Rat.EUTRA) {
                lteTree
            } else {
                nrTree
            }
        return ASN1Translator(PERTranslatorFactory(false), tree)
    }

    fun getUeCapabilityJsonFromHex(defaultRat: Rat, hexString: String): JsonObject {
        val data = hexString.preformatHex().decodeHex()

        if (data.isEmpty()) {
            return buildJsonObject {}
        }

        val isLteCapInfo = data[0] in 0x38.toByte()..0x3E.toByte()
        val isNrCapInfo = data[0] in 0x48.toByte()..0x4E.toByte()

        if (!isLteCapInfo && !isNrCapInfo) {
            return ratContainerToJson(defaultRat, data)
        }

        val jsonWriter = KotlinJsonFormatWriter()
        val translator: ASN1Translator
        val ratContainerListPath: String
        val octetStringKey: String

        if (isLteCapInfo) {
            translator = getAsn1Translator(Rat.EUTRA)
            ratContainerListPath =
                "message.c1.ueCapabilityInformation.criticalExtensions.c1.ueCapabilityInformation-r8.ue-CapabilityRAT-ContainerList"
            octetStringKey = "ueCapabilityRAT-Container"
        } else {
            translator = getAsn1Translator(Rat.NR)
            ratContainerListPath =
                "message.c1.ueCapabilityInformation.criticalExtensions.ueCapabilityInformation.ue-CapabilityRAT-ContainerList"
            octetStringKey = "ue-CapabilityRAT-Container"
        }

        translator.decode("UL-DCCH-Message", data.inputStream(), jsonWriter)

        val ueCap =
            jsonWriter.jsonNode?.getArrayAtPath(ratContainerListPath) ?: JsonArray(emptyList())
        val map = mutableMapOf<String, JsonElement>()

        for (ueCapContainer in ueCap) {
            val ratType = Rat.of(ueCapContainer.getString("rat-Type"))
            val octetString = ueCapContainer.getString(octetStringKey)
            if (ratType != null && octetString != null) {
                map += ratContainerToJson(ratType, octetString.decodeHex())
            }
        }
        return JsonObject(map)
    }

    private fun getResourceAsStream(path: String): InputStream? =
        javaClass.getResourceAsStream(path)

    private fun ratContainerToJson(rat: Rat, bytes: ByteArray): JsonObject {
        if (rat != Rat.EUTRA && rat != Rat.EUTRA_NR && rat != Rat.NR) {
            return JsonObject(emptyMap())
        }

        val jsonWriter = KotlinJsonFormatWriter()
        val translator =
            if (rat == Rat.EUTRA) getAsn1Translator(Rat.EUTRA) else getAsn1Translator(Rat.NR)

        return buildJsonObject {
            translator.decode(rat.ratCapabilityIdentifier, bytes.inputStream(), jsonWriter)
            jsonWriter.jsonNode?.let { put(rat.toString(), it) }
        }
    }

    private fun parseTree(stream: InputStream): ParseTree {
        val inputStream = CharStreams.fromStream(stream)
        val asn1Lexer = ASN1Lexer(inputStream)
        val commonTokenStream = CommonTokenStream(asn1Lexer)
        val asn1Parser = ASN1Parser(commonTokenStream)
        asn1Parser.interpreter.predictionMode = PredictionMode.SLL
        return asn1Parser.moduleDefinition()
    }
}
