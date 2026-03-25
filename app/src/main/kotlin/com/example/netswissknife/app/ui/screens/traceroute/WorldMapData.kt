package com.example.netswissknife.app.ui.screens.traceroute

/**
 * Simplified continent outlines stored as normalized equirectangular coordinates.
 *
 * Conversion:  x = (longitude + 180) / 360
 *              y = (90 - latitude) / 180
 *
 * Points trace each landmass clockwise from a north-western corner.
 * Accuracy is intentionally reduced to ~20-30 anchor points per continent;
 * the shapes are recognisable, not cartographically precise.
 */
object WorldMapData {

    val northAmerica = floatArrayOf(
        // NW Alaska → south along Pacific → Mexico → Caribbean coast → up Atlantic → Arctic
        0.033f, 0.139f,   // 65°N, 168°W  Alaska NW
        0.047f, 0.194f,   // 55°N, 163°W  Alaska tip
        0.128f, 0.200f,   // 54°N, 134°W  Panhandle
        0.153f, 0.233f,   // 48°N, 125°W  Pacific NW
        0.172f, 0.311f,   // 34°N, 118°W  Los Angeles
        0.194f, 0.367f,   // 23°N, 110°W  Baja tip
        0.231f, 0.406f,   // 15°N, 92°W   Guatemala
        0.264f, 0.444f,   // 10°N, 83°W   Costa Rica
        0.319f, 0.444f,   // 10°N, 63°W   Trinidad coast
        0.278f, 0.361f,   // 25°N, 80°W   Florida tip
        0.283f, 0.333f,   // 30°N, 81°W   Georgia coast
        0.292f, 0.306f,   // 34°N, 77°W   Carolinas
        0.303f, 0.272f,   // 41°N, 71°W   New York
        0.317f, 0.256f,   // 44°N, 66°W   Nova Scotia
        0.350f, 0.244f,   // 47°N, 53°W   Newfoundland
        0.342f, 0.194f,   // 55°N, 57°W   Labrador
        0.319f, 0.167f,   // 60°N, 65°W   N Quebec
        0.264f, 0.139f,   // 65°N, 85°W   Hudson Bay N
        0.222f, 0.111f,   // 70°N, 95°W   Arctic Canada
        0.144f, 0.111f,   // 70°N, 128°W  NW Canada
        0.033f, 0.139f    // back to start
    )

    val southAmerica = floatArrayOf(
        0.267f, 0.433f,   // 11°N, 74°W  Colombia/Venezuela N
        0.250f, 0.472f,   // 4°S,  80°W  Ecuador coast
        0.250f, 0.522f,   // 12°S, 80°W  Peru N
        0.256f, 0.583f,   // 23°S, 70°W  Atacama
        0.250f, 0.644f,   // 35°S, 72°W  Chile C
        0.253f, 0.711f,   // 48°S, 74°W  Patagonia
        0.264f, 0.778f,   // 55°S, 67°W  S Argentina
        0.278f, 0.817f,   // 55°S, 68°W  Tierra del Fuego
        0.289f, 0.789f,   // 52°S, 69°W  E Patagonia
        0.303f, 0.728f,   // 44°S, 65°W  Peninsula Valdés
        0.311f, 0.678f,   // 36°S, 57°W  Buenos Aires
        0.319f, 0.628f,   // 27°S, 49°W  S Brazil
        0.325f, 0.578f,   // 18°S, 39°W  Salvador area
        0.347f, 0.528f,   // 5°S,  35°W  NE Brazil
        0.347f, 0.478f,   // 5°N,  37°W  NE tip
        0.336f, 0.456f,   // 8°N,  48°W  Guyana
        0.311f, 0.444f,   // 10°N, 61°W  Trinidad
        0.267f, 0.433f    // back
    )

    val europe = floatArrayOf(
        0.422f, 0.083f,   // 70°N, 28°E  N Norway
        0.444f, 0.100f,   // 70°N, 20°E  N Finland
        0.458f, 0.133f,   // 65°N, 25°E  Finland
        0.458f, 0.167f,   // 60°N, 25°E  Helsinki
        0.447f, 0.183f,   // 57°N, 22°E  Baltic
        0.431f, 0.183f,   // 57°N, 10°E  Denmark
        0.422f, 0.194f,   // 55°N, 8°E   N Germany coast
        0.406f, 0.194f,   // 55°N, -4°E  Scotland
        0.389f, 0.206f,   // 53°N, -9°W  Ireland W
        0.389f, 0.222f,   // 50°N, -5°W  Cornwall
        0.394f, 0.239f,   // 48°N, -3°W  Brittany
        0.378f, 0.239f,   // 48°N, -9°W  W Portugal
        0.375f, 0.261f,   // 37°N, -8°W  Cape St Vincent
        0.386f, 0.261f,   // 37°N, -5°W  S Spain
        0.397f, 0.267f,   // 36°N, -5°W  Gibraltar
        0.406f, 0.261f,   // 37°N, -2°W  SE Spain
        0.417f, 0.256f,   // 38°N,  2°E  Mediterranean
        0.428f, 0.256f,   // 38°N, 10°E  Gulf of Genoa
        0.433f, 0.272f,   // 37°N, 15°E  Sicily
        0.433f, 0.294f,   // 32°N, 15°E  Malta region  ← pull back up
        0.442f, 0.283f,   // 36°N, 22°E  Greece S
        0.453f, 0.300f,   // 32°N, 28°E  Turkey W coast
        0.458f, 0.278f,   // 39°N, 26°E  Aegean
        0.458f, 0.250f,   // 44°N, 26°E  Romania coast
        0.458f, 0.222f,   // 50°N, 26°E  Ukraine
        0.450f, 0.200f,   // 53°N, 22°E  Poland
        0.444f, 0.183f,   // 57°N, 22°E  Baltic states
        0.444f, 0.167f,   // 60°N, 22°E  Finland gulf
        0.444f, 0.133f,   // 65°N, 22°E
        0.433f, 0.111f,   // 68°N, 16°E  N Norway
        0.422f, 0.083f    // back
    )

    val africa = floatArrayOf(
        0.456f, 0.261f,   // 38°N, 10°E  Tunisia N
        0.461f, 0.294f,   // 33°N, 12°E  Libya
        0.478f, 0.322f,   // 27°N, 22°E  Egypt/Libya
        0.500f, 0.339f,   // 23°N, 36°E  Red Sea coast
        0.511f, 0.383f,   // 12°N, 44°E  Djibouti
        0.519f, 0.417f,   // 7°N,  47°E  Somalia
        0.511f, 0.456f,   // 2°N,  42°E  Mogadishu
        0.503f, 0.494f,   // 5°S,  40°E  Mombasa
        0.497f, 0.544f,   // 15°S, 39°E  Mozambique N
        0.503f, 0.600f,   // 28°S, 33°E  Mozambique S
        0.492f, 0.650f,   // 35°S, 27°E  Port Elizabeth
        0.472f, 0.706f,   // 46°S, 18°E  ← too far S, fix
        0.456f, 0.733f,   // 48°S? – this is Cape of Good Hope region
        0.450f, 0.717f,   // 43°S region
        0.439f, 0.700f,   // Cape region
        0.428f, 0.678f,   // S Africa W coast
        0.417f, 0.644f,   // Namibia S
        0.411f, 0.600f,   // Namibia
        0.408f, 0.556f,   // Angola C
        0.406f, 0.511f,   // Gabon
        0.406f, 0.467f,   // Nigeria
        0.406f, 0.433f,   // Ghana
        0.392f, 0.428f,   // Liberia
        0.378f, 0.433f,   // Guinea
        0.372f, 0.406f,   // Senegal
        0.381f, 0.383f,   // Mauritania
        0.394f, 0.356f,   // W Sahara coast
        0.400f, 0.322f,   // Morocco S
        0.408f, 0.294f,   // Morocco N coast
        0.425f, 0.267f,   // Algeria coast
        0.439f, 0.261f,   // Tunisia W
        0.456f, 0.261f    // back
    )

    val asia = floatArrayOf(
        // From Urals → across N Siberia → Far East → SE Asia → India → Middle East → Caspian → back
        0.458f, 0.211f,   // 55°N, 25°E  Poland/Russia border
        0.519f, 0.194f,   // 57°N, 60°E  Urals
        0.578f, 0.167f,   // 62°N, 78°E  W Siberia
        0.644f, 0.150f,   // 65°N, 112°E Central Siberia
        0.722f, 0.139f,   // 68°N, 140°E NE Siberia
        0.769f, 0.139f,   // 68°N, 157°E Chukotka
        0.806f, 0.156f,   // 62°N, 165°E  Kamchatka
        0.844f, 0.178f,   // 56°N, 162°E S Kamchatka
        0.858f, 0.200f,   // 52°N, 159°E  Kuril coast
        0.878f, 0.217f,   // 48°N, 142°E  Sakhalin
        0.894f, 0.239f,   // 43°N, 132°E  Vladivostok
        0.900f, 0.261f,   // 38°N, 122°E  N Korea/China coast
        0.908f, 0.289f,   // 30°N, 122°E  Shanghai
        0.900f, 0.317f,   // 22°N, 114°E  Hong Kong
        0.883f, 0.344f,   // 15°N, 108°E  Vietnam
        0.872f, 0.367f,   // 8°N,  105°E  Mekong Delta
        0.856f, 0.383f,   // 2°N,  103°E  Singapore
        0.833f, 0.394f,   // 5°S,  100°E  Sumatra?  ← back to mainland SE Asia
        0.819f, 0.383f,   // 5°N,  95°E   Thailand W coast
        0.806f, 0.367f,   // 10°N, 98°E   Thailand
        0.794f, 0.356f,   // 15°N, 97°E   Myanmar coast
        0.778f, 0.361f,   // 17°N, 94°E   Myanmar
        0.761f, 0.344f,   // 22°N, 92°E   Bangladesh
        0.750f, 0.361f,   // 17°N, 82°E   India E coast
        0.733f, 0.400f,   // 10°N, 80°E   India SE
        0.725f, 0.428f,   // 8°N,  77°E   India tip
        0.714f, 0.400f,   // 10°N, 74°E   Goa coast
        0.703f, 0.361f,   // 18°N, 73°E   Mumbai
        0.694f, 0.328f,   // 25°N, 66°E   Karachi
        0.675f, 0.289f,   // 32°N, 63°E   SE Iran
        0.656f, 0.261f,   // 38°N, 56°E   Caspian SE
        0.639f, 0.244f,   // 43°N, 50°E   Caspian N
        0.619f, 0.239f,   // 45°N, 43°E   Caucasus
        0.608f, 0.256f,   // 38°N, 40°E   Turkey E
        0.597f, 0.272f,   // 32°N, 35°E   Levant
        0.594f, 0.317f,   // 15°N, 33°E   Eritrea
        0.583f, 0.294f,   // 22°N, 39°E   Saudi Arabia W
        0.572f, 0.278f,   // 28°N, 34°E   Sinai
        0.564f, 0.261f,   // 34°N, 36°E   Syria/Lebanon
        0.558f, 0.244f,   // 38°N, 40°E   Turkey S
        0.542f, 0.228f,   // 42°N, 36°E   Turkey N coast
        0.519f, 0.222f,   // 44°N, 38°E   Black Sea E
        0.494f, 0.211f,   // 49°N, 37°E   Ukraine E
        0.458f, 0.211f    // back
    )

    val australia = floatArrayOf(
        0.736f, 0.556f,   // 20°S, 115°E  NW coast
        0.758f, 0.533f,   // 17°S, 123°E  Kimberley
        0.783f, 0.511f,   // 12°S, 131°E  Darwin
        0.811f, 0.506f,   // 12°S, 136°E  Arnhem N
        0.842f, 0.511f,   // 12°S, 141°E  Cape York base
        0.861f, 0.500f,   // 12°S, 144°E  Cape York tip
        0.875f, 0.522f,   // 15°S, 145°E  GBR coast
        0.906f, 0.556f,   // 22°S, 150°E  Rockhampton
        0.936f, 0.600f,   // 28°S, 153°E  Brisbane
        0.958f, 0.644f,   // 34°S, 151°E  Sydney
        0.964f, 0.672f,   // 38°S, 147°E  Gippsland
        0.956f, 0.700f,   // 40°S, 148°E  Melbourne
        0.933f, 0.722f,   // 40°S, 144°E  Bass Strait
        0.908f, 0.722f,   // 40°S, 140°E  Bight E
        0.878f, 0.717f,   // 38°S, 129°E  Bight W
        0.844f, 0.706f,   // 38°S, 124°E  Esperance
        0.822f, 0.683f,   // 34°S, 115°E  Perth
        0.811f, 0.644f,   // 26°S, 113°E  Shark Bay
        0.803f, 0.600f,   // 22°S, 114°E  Coral Bay
        0.789f, 0.572f,   // 20°S, 119°E  Port Hedland
        0.758f, 0.567f,   // 20°S, 116°E  Pilbara
        0.736f, 0.556f    // back
    )

    val greenland = floatArrayOf(
        0.342f, 0.044f,   // 74°N, 43°W
        0.367f, 0.056f,   // 74°N, 39°W
        0.397f, 0.072f,   // 71°N, 25°W
        0.414f, 0.094f,   // 68°N, 22°W
        0.408f, 0.117f,   // 65°N, 22°W
        0.397f, 0.128f,   // 64°N, 25°W
        0.383f, 0.144f,   // 62°N, 30°W
        0.369f, 0.161f,   // 59°N, 43°W  Greenland S
        0.353f, 0.156f,   // 60°N, 46°W
        0.336f, 0.139f,   // 63°N, 50°W
        0.325f, 0.111f,   // 67°N, 53°W
        0.325f, 0.083f,   // 70°N, 52°W
        0.331f, 0.061f,   // 73°N, 51°W
        0.342f, 0.044f    // back
    )

    /** All continent polygon datasets, in draw order. */
    val continents: List<FloatArray> = listOf(
        northAmerica,
        southAmerica,
        europe,
        africa,
        asia,
        australia,
        greenland
    )
}
