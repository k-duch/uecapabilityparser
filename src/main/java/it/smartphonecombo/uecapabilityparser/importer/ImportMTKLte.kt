package it.smartphonecombo.uecapabilityparser.importer

import it.smartphonecombo.uecapabilityparser.extension.mutableListWithCapacity
import it.smartphonecombo.uecapabilityparser.model.BCS
import it.smartphonecombo.uecapabilityparser.model.BwClass
import it.smartphonecombo.uecapabilityparser.model.Capabilities
import it.smartphonecombo.uecapabilityparser.model.EmptyMimo
import it.smartphonecombo.uecapabilityparser.model.combo.ComboLte
import it.smartphonecombo.uecapabilityparser.model.component.ComponentLte
import it.smartphonecombo.uecapabilityparser.model.toMimo
import java.io.InputStreamReader
import java.lang.NumberFormatException
import java.math.BigInteger
import java.util.NoSuchElementException

/**
 * A parser for *MSG_ID_ERRC_RCM_UE_PRE_CA_COMB_INFO* and *MSG_ID_ERRC_RCM_UE_CA_COMB_INFO*.
 *
 * *UE_PRE_CA_COMB_INFO* contains the LTE combos supported by a MTK device before any filtering.
 * While *UE_CA_COMB_INFO* contains the LTE combos supported after filtering (carrier policy/ue cap
 * enquiry).
 */
object ImportMTKLte : ImportCapabilities {

    /**
     * This parser take as [input] a [ByteArray] containing the ELT text representation of the
     * *MSG_ID_ERRC_RCM_UE_PRE_CA_COMB_INFO* and *MSG_ID_ERRC_RCM_UE_CA_COMB_INFO*.
     *
     * The output is a [Capabilities] with the list of parsed LTE combos stored in
     * [lteCombos][Capabilities.lteCombos].
     *
     * It can parse multiple messages in the same input.
     */
    override fun parse(input: ByteArray): Capabilities {
        val listCombos: MutableList<ComboLte> = mutableListOf()
        val lines =
            input.inputStream().use {
                it.reader().use(InputStreamReader::readLines).map(String::trim)
            }
        try {
            val bcsIterator = getBCSArray(lines.iterator()).iterator()
            val iterator = lines.iterator() // New iterator
            while (iterator.firstOrNull { it.startsWith("band_comb[") } != null) {
                val bcs = bcsIterator.next()
                val bands = parseCombo(iterator) ?: continue

                bands.sortDescending()

                listCombos.add(ComboLte(bands, bcs))
            }
        } catch (ignored: NoSuchElementException) {
            // Do nothing
        }
        return Capabilities(listCombos)
    }

    /**
     * Parse a single combo.
     *
     * Return the list of lte Components found.
     *
     * Return null if parsing fails or if there is no component.
     */
    @Throws(NoSuchElementException::class)
    private fun parseCombo(input: Iterator<String>): MutableList<ComponentLte>? {
        val numCCs = extractInt(input.next())

        // Check if combo contains any CCs
        if (numCCs < 1) {
            return null
        }

        val arrayLength = extractArraySize(input.next())
        val bands = parseComponents(minOf(numCCs, arrayLength), input)

        val line = input.firstOrNull { it.startsWith("band_mimo = Array") } ?: return null
        val mimoArrayLength = extractArraySize(line)

        parseMimo(minOf(numCCs, mimoArrayLength), input, bands)
        return bands
    }

    /** Extract MIMO information and update [bands] accordingly. */
    @Throws(NoSuchElementException::class)
    private fun parseMimo(numCCs: Int, input: Iterator<String>, bands: List<ComponentLte>) {
        for (i in 0 until numCCs) {
            input.firstOrNull { it.startsWith("band_mimo[$i]") } ?: break
            val line = extractValue(input.next()).split(" ").first()
            if (line == "ERRC_CAPA_CA_MIMO_CAPA_FOUR_LAYERS") {
                bands[i].mimoDL = 4.toMimo()
            } else if (line == "ERRC_CAPA_CA_MIMO_CAPA_TWO_LAYERS") {
                bands[i].mimoDL = 2.toMimo()
            }
        }
    }

    /** Extract BCS information */
    @Throws(NoSuchElementException::class)
    private fun getBCSArray(input: Iterator<String>): List<BCS> {
        // Array size is typically 111 or 117
        val bcsList = mutableListWithCapacity<BCS>(117)
        while (true) {
            val line = input.firstOrNull { it.startsWith("bandwidth_comb_set = Array") } ?: break
            val bcsArrayLength = extractArraySize(line)
            for (i in 0 until bcsArrayLength) {
                val bcs = extractBigInt(input.next()).toString(2)
                bcsList.add(BCS.fromBinaryString(bcs))
            }
        }
        return bcsList
    }

    /** Parse [numCCs] components. */
    @Throws(NoSuchElementException::class)
    private fun parseComponents(numCCs: Int, input: Iterator<String>): MutableList<ComponentLte> {
        val bands = mutableListWithCapacity<ComponentLte>(numCCs)
        for (i in 0 until numCCs) {
            input.firstOrNull { it.startsWith("band_param[$i]") }
            val baseBand = extractInt(input.next())
            val classUL = BwClass.valueOfMtkIndex(extractInt(input.next()))
            // no support for UL MIMO
            val mimoUL = if (classUL != BwClass.NONE) 1.toMimo() else EmptyMimo
            val classDL = BwClass.valueOfMtkIndex(extractInt(input.next()))
            val band = ComponentLte(baseBand, classDL, classUL, mimoUL = mimoUL)
            bands.add(band)
        }
        return bands
    }

    /** Extract the field value from the given line */
    private fun extractValue(line: String): String {
        return line.split("=").last().trim()
    }

    private val arrayRegex = """Array\[(\d+)]""".toRegex()
    /** Get the size of the array from the given line */
    private fun extractArraySize(line: String): Int {
        return arrayRegex.find(line)?.groupValues?.get(1)?.toInt() ?: 0
    }

    /** Extract the field value from the given line and converts it to int */
    @Throws(NumberFormatException::class)
    private fun extractInt(line: String): Int {
        return Integer.decode(extractValue(line))
    }

    /** Extract the field value from the given line and converts it to biginteger */
    @Throws(NumberFormatException::class)
    private fun extractBigInt(line: String): BigInteger {
        val value = extractValue(line).drop(2)
        return BigInteger(value, 16)
    }

    /**
     * Return the first element matching the given predicate or null if not found.
     *
     * NB: This function will update iterator cursor.
     */
    private inline fun Iterator<String>.firstOrNull(predicate: (String) -> Boolean): String? {
        for (item in this) {
            if (predicate(item)) {
                return item
            }
        }
        return null
    }
}
