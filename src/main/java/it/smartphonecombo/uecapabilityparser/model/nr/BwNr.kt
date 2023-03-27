package it.smartphonecombo.uecapabilityparser.model.nr

data class BwNr(val scs: Int, val bwsDL: IntArray, val bwsUL: IntArray) {
    constructor(scs: Int, bwsDL: IntArray) : this(scs, bwsDL, bwsDL)
}