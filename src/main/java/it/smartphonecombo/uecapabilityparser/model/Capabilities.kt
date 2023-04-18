package it.smartphonecombo.uecapabilityparser.model

import it.smartphonecombo.uecapabilityparser.model.band.BandNrDetails
import it.smartphonecombo.uecapabilityparser.model.combo.ComboEnDc
import it.smartphonecombo.uecapabilityparser.model.combo.ComboLte
import it.smartphonecombo.uecapabilityparser.model.combo.ComboNr
import it.smartphonecombo.uecapabilityparser.model.combo.ComboNrDc
import it.smartphonecombo.uecapabilityparser.model.component.ComponentLte
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Capabilities(
    @SerialName("lteca") var lteCombos: List<ComboLte> = emptyList(),
    @SerialName("lte-bands") var lteBands: List<ComponentLte> = emptyList(),
    @SerialName("nr-nsa-bands-eutra") var nrNSAbands: List<BandNrDetails> = emptyList(),
    @SerialName("nr-sa-bands-eutra") var nrSAbands: List<BandNrDetails> = emptyList(),
    @SerialName("nr-bands") var nrBands: List<BandNrDetails> = emptyList(),
    @SerialName("lte-category-dl") var lteCategoryDL: Int = 0,
    @SerialName("lte-category-ul") var lteCategoryUL: Int = 0,
    @SerialName("endc") var enDcCombos: List<ComboEnDc> = emptyList(),
    @SerialName("nrca") var nrCombos: List<ComboNr> = emptyList(),
    @SerialName("nrdc") var nrDcCombos: List<ComboNrDc> = emptyList(),
    @SerialName("metadata") val metadata: MutableMap<String, String> = mutableMapOf()
) {
    fun setMetadata(key: String, value: Any) {
        metadata[key] = value.toString()
    }

    fun getStringMetadata(key: String): String? {
        return metadata.getOrDefault(key, null)
    }

    fun getIntMetadata(key: String): Int? {
        return metadata.getOrDefault(key, null)?.toIntOrNull()
    }
}
