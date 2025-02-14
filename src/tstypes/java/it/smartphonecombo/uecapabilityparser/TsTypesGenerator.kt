package it.smartphonecombo.uecapabilityparser

import dev.adamko.kxstsgen.KxsTsGenerator
import it.smartphonecombo.uecapabilityparser.model.Capabilities
import it.smartphonecombo.uecapabilityparser.util.Output

internal object TsTypesGenerator {

    @JvmStatic
    fun main(args: Array<String>) {
        val tsGenerator = KxsTsGenerator()
        val warning =
            """
            |// Automatically generated. Don't edit.
            |// Run gradlew genTsTypes to update this file.
            |
            """
                .trimMargin()
        val typescriptDefinitions = tsGenerator.generate(Capabilities.serializer())
        Output.outputFileOrStdout(warning + typescriptDefinitions, "uecapabilityparser.d.ts")
        println("Typescript definitions exported to uecapabilityparser.d.ts")
    }
}
