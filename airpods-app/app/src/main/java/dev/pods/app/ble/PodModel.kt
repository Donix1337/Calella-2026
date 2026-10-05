package dev.pods.app.ble

enum class PodKind {
    /** In-ear with silicone tips (AirPods Pro, Powerbeats Pro, ...). */
    PRO,

    /** Open-fit buds with short stems (AirPods 3, AirPods 4). */
    OPEN,

    /** Original long-stem AirPods. */
    CLASSIC,

    /** Over-ear headphones that report one battery level. */
    HEADPHONES,
}

/**
 * Apple product IDs as they appear in the proximity pairing advertisement
 * (bytes 3-4, little endian).
 */
enum class PodModel(val id: Int, val displayName: String, val kind: PodKind) {
    AIRPODS_1(0x2002, "AirPods", PodKind.CLASSIC),
    AIRPODS_2(0x200F, "AirPods 2", PodKind.CLASSIC),
    AIRPODS_3(0x2013, "AirPods 3", PodKind.OPEN),
    AIRPODS_4(0x2019, "AirPods 4", PodKind.OPEN),
    AIRPODS_4_ANC(0x201B, "AirPods 4 ANC", PodKind.OPEN),
    AIRPODS_PRO(0x200E, "AirPods Pro", PodKind.PRO),
    AIRPODS_PRO_2(0x2014, "AirPods Pro 2", PodKind.PRO),
    AIRPODS_PRO_2_USB_C(0x2024, "AirPods Pro 2 USB-C", PodKind.PRO),
    AIRPODS_MAX(0x200A, "AirPods Max", PodKind.HEADPHONES),
    POWERBEATS_PRO(0x200B, "Powerbeats Pro", PodKind.PRO),
    BEATS_FIT_PRO(0x2012, "Beats Fit Pro", PodKind.PRO),
    BEATS_STUDIO_BUDS(0x2011, "Beats Studio Buds", PodKind.PRO),
    BEATS_STUDIO_BUDS_PLUS(0x2016, "Beats Studio Buds +", PodKind.PRO),
    BEATS_SOLO_PRO(0x200C, "Beats Solo Pro", PodKind.HEADPHONES),
    BEATS_SOLO_3(0x2006, "Beats Solo3", PodKind.HEADPHONES),
    BEATS_STUDIO_3(0x2009, "Beats Studio3", PodKind.HEADPHONES),
    BEATS_STUDIO_PRO(0x2017, "Beats Studio Pro", PodKind.HEADPHONES);

    companion object {
        fun fromId(id: Int): PodModel? = entries.firstOrNull { it.id == id }
    }
}
