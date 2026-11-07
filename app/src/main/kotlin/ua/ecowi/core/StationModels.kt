package ua.ecowi.core

/**
 * Regional variant of a station. Only the number of AC sockets differs between them, and the
 * station reports all AC sockets as one value anyway.
 */
enum class Region {
    /** 220–240 V (EU, UK, UA, AU, …) */
    V230,

    /** 100–120 V (US, CA, MX, JP, …) */
    V120;

    companion object {
        private val V120_COUNTRIES = setOf("US", "CA", "MX", "JP", "TW", "PR", "CO", "EC", "PA", "DO", "GT", "HN", "SV", "NI", "CR")

        fun forCountry(isoCountry: String?): Region =
            if (isoCountry?.uppercase() in V120_COUNTRIES) V120 else V230
    }
}

/**
 * One station model: which outputs it reports separately and how many AC sockets it has.
 *
 * [ports] lists only outputs that the DELTA 3 protocol reports one by one (see [Port]). Car outlet,
 * DC5521 and Anderson outputs are reported together as [Port.DC_12V]; a third USB-C port (DELTA 3 Max
 * Plus) has no field of its own.
 */
data class StationModel(
    val id: String,
    val name: String,
    val ports: Set<Port>,
    val acOutlets: Map<Region, Int>,
    /** Product names used by EcoFlow's cloud, normalised with [StationModels.normalise]. */
    val aliases: List<String>,
)

/**
 * The known stations. Port layouts come from EcoFlow's published specifications (2025–2026); add a model
 * by appending an entry here (see CONTRIBUTING.md, "Station models").
 */
object StationModels {

    private val FULL_DC = setOf(Port.AC, Port.USB_A1, Port.USB_A2, Port.USB_C1, Port.USB_C2, Port.DC_12V)

    val DELTA_3_CLASSIC = StationModel(
        id = "delta3_classic",
        name = "DELTA 3 Classic",
        // 1× USB-A, USB-C 100 W + 30 W, no 12 V / DC section at all
        ports = setOf(Port.AC, Port.USB_A1, Port.USB_C1, Port.USB_C2),
        acOutlets = mapOf(Region.V230 to 2, Region.V120 to 3),
        aliases = listOf("delta3classic"),
    )

    val DELTA_3 = StationModel(
        id = "delta3",
        name = "DELTA 3",
        // 2× USB-A, 2× USB-C, car outlet + 2× DC5521
        ports = FULL_DC,
        acOutlets = mapOf(Region.V230 to 4, Region.V120 to 6),
        aliases = listOf("delta3"),
    )

    val DELTA_3_PLUS = StationModel(
        id = "delta3_plus",
        name = "DELTA 3 Plus",
        ports = FULL_DC,
        acOutlets = mapOf(Region.V230 to 4, Region.V120 to 6),
        aliases = listOf("delta3plus"),
    )

    val DELTA_3_1500 = StationModel(
        id = "delta3_1500",
        name = "DELTA 3 1500",
        ports = FULL_DC,
        acOutlets = mapOf(Region.V230 to 4, Region.V120 to 6),
        aliases = listOf("delta31500"),
    )

    val DELTA_3_MAX_PLUS = StationModel(
        id = "delta3_max_plus",
        name = "DELTA 3 Max Plus",
        // 1× USB-A, USB-C 140 W + 2× 45 W (the third isn't reported separately), car outlet + Anderson
        ports = setOf(Port.AC, Port.USB_A1, Port.USB_C1, Port.USB_C2, Port.DC_12V),
        acOutlets = mapOf(Region.V230 to 4, Region.V120 to 4),
        aliases = listOf("delta3maxplus"),
    )

    val ALL: List<StationModel> = listOf(DELTA_3_CLASSIC, DELTA_3, DELTA_3_PLUS, DELTA_3_1500, DELTA_3_MAX_PLUS)

    fun byId(id: String?): StationModel? = ALL.firstOrNull { it.id == id }

    /** "DELTA 3 Classic", "Delta3-Classic" → "delta3classic". */
    fun normalise(name: String): String = name.lowercase().filter { it.isLetterOrDigit() }

    /**
     * Model for a cloud product name: an exact alias wins, otherwise the longest alias contained in the
     * name ("DELTA 3 Plus EU" → DELTA 3 Plus, not DELTA 3).
     */
    fun match(productName: String?): StationModel? {
        if (productName.isNullOrBlank()) return null
        val n = normalise(productName)
        ALL.firstOrNull { n in it.aliases }?.let { return it }
        return ALL.flatMap { m -> m.aliases.map { it to m } }
            .filter { (alias, _) -> n.contains(alias) }
            .maxByOrNull { (alias, _) -> alias.length }
            ?.second
    }

    /** Manual choice first, then what the cloud reported, else unknown. */
    fun resolve(manualId: String?, detectedId: String?): StationModel? = byId(manualId) ?: byId(detectedId)
}

/** Which ports to show: the model's own plus any port that has ever drawn power. */
object PortLayout {

    /** A port that delivered power is certainly there, whatever the model table says. */
    fun learn(known: Set<Port>, snapshot: PowerSnapshot): Set<Port> =
        known + snapshot.ports.filterValues { it > 0 }.keys

    /**
     * Known model → its ports (plus learned extras). Unknown model → AC and the learned ports only, so
     * that ports the station doesn't have never show up as idle chips.
     */
    fun visible(model: StationModel?, learned: Set<Port>): List<Port> {
        val base = model?.ports ?: setOf(Port.AC)
        return Port.entries.filter { it in base || it in learned }
    }
}
