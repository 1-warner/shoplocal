package com.noor.shoplocal.data

/**
 * A small lookup of South African cities to coordinates, used by the "sell an
 * item" form so a member can pick where their item is without needing GPS
 * permission — the chosen city drops the pin on the embedded map.
 */
object SaCities {

    data class City(val name: String, val lat: Double, val lng: Double)

    val ALL: List<City> = listOf(
        City("Cape Town, WC", -33.9249, 18.4241),
        City("Johannesburg, GP", -26.2041, 28.0473),
        City("Pretoria, GP", -25.7479, 28.2293),
        City("Durban, KZN", -29.8587, 31.0218),
        City("Gqeberha, EC", -33.9608, 25.6022),
        City("Bloemfontein, FS", -29.0852, 26.1596),
        City("East London, EC", -33.0153, 27.9116),
        City("Polokwane, LP", -23.9045, 29.4689),
        City("Nelspruit, MP", -25.4753, 30.9694),
        City("Kimberley, NC", -28.7282, 24.7499),
        City("Stellenbosch, WC", -33.9346, 18.8610),
        City("Knysna, WC", -34.0363, 23.0471),
        City("Hermanus, WC", -34.4187, 19.2345),
        City("Pietermaritzburg, KZN", -29.6006, 30.3794),
        City("Rustenburg, NW", -25.6672, 27.2424)
    )

    val NAMES: List<String> = ALL.map { it.name }

    fun byName(name: String): City? = ALL.firstOrNull { it.name == name }
}
