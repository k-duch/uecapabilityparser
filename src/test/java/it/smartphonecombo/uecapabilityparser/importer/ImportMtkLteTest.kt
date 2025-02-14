package it.smartphonecombo.uecapabilityparser.importer

import it.smartphonecombo.uecapabilityparser.util.Output
import java.io.File
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

internal class ImportMtkLteTest {
    private fun parse(inputFilename: String, oracleFilename: String) {
        val path = "src/test/resources/mtkLte/"

        val inputPath = "$path/input/$inputFilename"
        val oraclePath = "$path/oracle/$oracleFilename"

        val capabilities = ImportMTKLte.parse(File(inputPath).readBytes())
        val actualCsv = Output.toCsv(capabilities).lines().dropLastWhile { it.isBlank() }
        val expectedCsv =
            File(oraclePath).bufferedReader().readLines().dropLastWhile { it.isBlank() }

        Assertions.assertLinesMatch(expectedCsv, actualCsv)
    }

    @Test
    fun parsePreCaCombInfo() {
        parse("PRE_CA_COMB_INFO.txt", "PRE_CA_COMB_INFO.csv")
    }

    @Test
    fun parseUeCaCombInfo() {
        parse("UE_CA_COMB_INFO.txt", "UE_CA_COMB_INFO.csv")
    }
}
