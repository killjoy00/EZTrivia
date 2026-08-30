import Foundation

/// One entry in the bundled flag catalog.
///
/// `askable` is false for two kinds of entry, both of which stay in the catalog
/// so the images still ship, but are never asked and never offered as an answer
/// choice:
///
/// - Dependencies whose official flag is byte-identical to another answer, so
///   the question would have no single right answer (BV, HM, MF, SJ, UM).
/// - Flags whose shipped artwork is outdated or politically contested enough
///   that grading an answer against it is not defensible (AF, NC, EH).
///
/// The second group is a content judgement rather than a measurement, so it is
/// deliberately narrow: a flag is only withdrawn when the artwork itself is
/// disputed, not merely because the place is.
///
/// `confusable` lists codes whose flag is close enough at phone size that
/// offering them together would be a coin flip rather than a question. The list
/// was measured, not guessed: every pair with a mean per-channel difference
/// below 12 on a 24x16 downsample of the shipped artwork.
struct FlagEntry: Sendable {
    let code: String
    let name: String
    let difficulty: TriviaDifficulty
    let askable: Bool
    let confusable: Set<String>
    let explanation: String?

    var asset: String { "flag-\(code.lowercased())" }

    init(_ code: String, _ name: String, _ difficulty: TriviaDifficulty,
         askable: Bool = true, confusable: Set<String> = [],
         explanation: String? = nil) {
        self.code = code
        self.name = name
        self.difficulty = difficulty
        self.askable = askable
        self.confusable = confusable
        self.explanation = explanation
    }
}

enum FlagCatalog {
    /// All 249 flags shipped in the asset catalog, ordered by difficulty then name.
    static let all: [FlagEntry] = [
        FlagEntry("AR", "Argentina", .easy),
        FlagEntry("AU", "Australia", .easy, confusable: ["NZ"]),
        FlagEntry("AT", "Austria", .easy),
        FlagEntry("BE", "Belgium", .easy),
        FlagEntry("BR", "Brazil", .easy),
        FlagEntry("CA", "Canada", .easy),
        FlagEntry("CL", "Chile", .easy),
        FlagEntry("CN", "China", .easy),
        FlagEntry("CO", "Colombia", .easy),
        FlagEntry("CU", "Cuba", .easy),
        FlagEntry("CZ", "Czechia", .easy),
        FlagEntry("DK", "Denmark", .easy),
        FlagEntry("EG", "Egypt", .easy, confusable: ["IQ", "YE"]),
        FlagEntry("FI", "Finland", .easy),
        FlagEntry("FR", "France", .easy),
        FlagEntry("DE", "Germany", .easy),
        FlagEntry("GR", "Greece", .easy),
        FlagEntry("IS", "Iceland", .easy),
        FlagEntry("IN", "India", .easy),
        FlagEntry("ID", "Indonesia", .easy),
        FlagEntry("IE", "Ireland", .easy),
        FlagEntry("IL", "Israel", .easy),
        FlagEntry("IT", "Italy", .easy),
        FlagEntry("JM", "Jamaica", .easy),
        FlagEntry("JP", "Japan", .easy),
        FlagEntry("KE", "Kenya", .easy),
        FlagEntry("YT", "Mayotte", .easy,
                  explanation: "This is an unofficial local flag of Mayotte; the official flag is the French tricolour."),
        FlagEntry("MX", "Mexico", .easy),
        FlagEntry("MA", "Morocco", .easy),
        FlagEntry("NL", "Netherlands", .easy),
        FlagEntry("NZ", "New Zealand", .easy, confusable: ["AU", "CK", "GS", "KY", "MS", "PN", "SH", "VG"]),
        FlagEntry("NG", "Nigeria", .easy),
        FlagEntry("NO", "Norway", .easy),
        FlagEntry("PK", "Pakistan", .easy),
        FlagEntry("PE", "Peru", .easy),
        FlagEntry("PH", "Philippines", .easy),
        FlagEntry("PL", "Poland", .easy),
        FlagEntry("PT", "Portugal", .easy),
        FlagEntry("RU", "Russia", .easy),
        FlagEntry("SA", "Saudi Arabia", .easy),
        FlagEntry("ZA", "South Africa", .easy),
        FlagEntry("KR", "South Korea", .easy),
        FlagEntry("ES", "Spain", .easy),
        FlagEntry("SE", "Sweden", .easy),
        FlagEntry("CH", "Switzerland", .easy),
        FlagEntry("TH", "Thailand", .easy),
        FlagEntry("TR", "Turkey", .easy),
        FlagEntry("UA", "Ukraine", .easy),
        FlagEntry("GB", "United Kingdom", .easy),
        FlagEntry("US", "United States", .easy),
        FlagEntry("VN", "Vietnam", .easy, confusable: ["KG"]),
        FlagEntry("AF", "Afghanistan", .medium, askable: false,
                  explanation: "This is Afghanistan's former 2004–2021 republic flag; the de facto authorities have used a different white flag since 2021."),
        FlagEntry("AL", "Albania", .medium),
        FlagEntry("DZ", "Algeria", .medium),
        FlagEntry("AD", "Andorra", .medium),
        FlagEntry("AO", "Angola", .medium),
        FlagEntry("AM", "Armenia", .medium),
        FlagEntry("AZ", "Azerbaijan", .medium),
        FlagEntry("BS", "Bahamas", .medium),
        FlagEntry("BH", "Bahrain", .medium),
        FlagEntry("BD", "Bangladesh", .medium),
        FlagEntry("BB", "Barbados", .medium),
        FlagEntry("BY", "Belarus", .medium),
        FlagEntry("BO", "Bolivia", .medium),
        FlagEntry("BA", "Bosnia and Herzegovina", .medium),
        FlagEntry("BW", "Botswana", .medium),
        FlagEntry("BN", "Brunei", .medium),
        FlagEntry("BG", "Bulgaria", .medium),
        FlagEntry("KH", "Cambodia", .medium),
        FlagEntry("CM", "Cameroon", .medium),
        FlagEntry("CR", "Costa Rica", .medium),
        FlagEntry("HR", "Croatia", .medium),
        FlagEntry("CY", "Cyprus", .medium),
        FlagEntry("DO", "Dominican Republic", .medium),
        FlagEntry("EC", "Ecuador", .medium),
        FlagEntry("SV", "El Salvador", .medium),
        FlagEntry("EE", "Estonia", .medium),
        FlagEntry("ET", "Ethiopia", .medium),
        FlagEntry("FJ", "Fiji", .medium),
        FlagEntry("GE", "Georgia", .medium),
        FlagEntry("GH", "Ghana", .medium),
        FlagEntry("GT", "Guatemala", .medium),
        FlagEntry("HT", "Haiti", .medium),
        FlagEntry("HN", "Honduras", .medium),
        FlagEntry("HU", "Hungary", .medium),
        FlagEntry("IR", "Iran", .medium),
        FlagEntry("IQ", "Iraq", .medium, confusable: ["EG", "YE"]),
        FlagEntry("CI", "Côte d’Ivoire", .medium),
        FlagEntry("JO", "Jordan", .medium),
        FlagEntry("KZ", "Kazakhstan", .medium),
        FlagEntry("KW", "Kuwait", .medium),
        FlagEntry("KG", "Kyrgyzstan", .medium, confusable: ["VN"]),
        FlagEntry("LA", "Laos", .medium),
        FlagEntry("LV", "Latvia", .medium),
        FlagEntry("LB", "Lebanon", .medium, confusable: ["PF"]),
        FlagEntry("LY", "Libya", .medium),
        FlagEntry("LI", "Liechtenstein", .medium),
        FlagEntry("LT", "Lithuania", .medium),
        FlagEntry("LU", "Luxembourg", .medium),
        FlagEntry("MG", "Madagascar", .medium),
        FlagEntry("MY", "Malaysia", .medium),
        FlagEntry("MT", "Malta", .medium),
        FlagEntry("MD", "Moldova", .medium),
        FlagEntry("MC", "Monaco", .medium),
        FlagEntry("MN", "Mongolia", .medium),
        FlagEntry("ME", "Montenegro", .medium),
        FlagEntry("MZ", "Mozambique", .medium),
        FlagEntry("MM", "Myanmar", .medium),
        FlagEntry("NA", "Namibia", .medium),
        FlagEntry("NP", "Nepal", .medium),
        FlagEntry("NI", "Nicaragua", .medium),
        FlagEntry("KP", "North Korea", .medium),
        FlagEntry("MK", "North Macedonia", .medium),
        FlagEntry("OM", "Oman", .medium),
        FlagEntry("PA", "Panama", .medium),
        FlagEntry("PG", "Papua New Guinea", .medium),
        FlagEntry("PY", "Paraguay", .medium),
        FlagEntry("QA", "Qatar", .medium),
        FlagEntry("RO", "Romania", .medium, confusable: ["TD"]),
        FlagEntry("SM", "San Marino", .medium),
        FlagEntry("SN", "Senegal", .medium),
        FlagEntry("RS", "Serbia", .medium),
        FlagEntry("SG", "Singapore", .medium),
        FlagEntry("SK", "Slovakia", .medium),
        FlagEntry("SI", "Slovenia", .medium),
        FlagEntry("LK", "Sri Lanka", .medium),
        FlagEntry("SD", "Sudan", .medium),
        FlagEntry("SY", "Syria", .medium),
        FlagEntry("TW", "Taiwan", .medium,
                  explanation: "This flag represents Taiwan, whose political and international status is disputed."),
        FlagEntry("TJ", "Tajikistan", .medium),
        FlagEntry("TZ", "Tanzania", .medium),
        FlagEntry("TT", "Trinidad and Tobago", .medium),
        FlagEntry("TN", "Tunisia", .medium),
        FlagEntry("TM", "Turkmenistan", .medium),
        FlagEntry("UG", "Uganda", .medium),
        FlagEntry("AE", "United Arab Emirates", .medium),
        FlagEntry("UY", "Uruguay", .medium),
        FlagEntry("UZ", "Uzbekistan", .medium),
        FlagEntry("VA", "Vatican City", .medium),
        FlagEntry("VE", "Venezuela", .medium),
        FlagEntry("YE", "Yemen", .medium, confusable: ["EG", "IQ"]),
        FlagEntry("ZM", "Zambia", .medium),
        FlagEntry("ZW", "Zimbabwe", .medium),
        FlagEntry("AS", "American Samoa", .hard),
        FlagEntry("AI", "Anguilla", .hard, confusable: ["FK", "KY", "MS", "SH", "TC"]),
        FlagEntry("AQ", "Antarctica", .hard,
                  explanation: "This is Graham Bartram's widely used unofficial design; Antarctica has no officially adopted sovereign flag."),
        FlagEntry("AG", "Antigua and Barbuda", .hard),
        FlagEntry("AW", "Aruba", .hard),
        FlagEntry("BZ", "Belize", .hard),
        FlagEntry("BJ", "Benin", .hard),
        FlagEntry("BM", "Bermuda", .hard),
        FlagEntry("BT", "Bhutan", .hard),
        FlagEntry("BV", "Bouvet Island", .hard, askable: false),
        FlagEntry("IO", "British Indian Ocean Territory", .hard),
        FlagEntry("VG", "British Virgin Islands", .hard,
                  confusable: ["FK", "GS", "KY", "MS", "NZ", "PN", "SH", "TC"]),
        FlagEntry("BF", "Burkina Faso", .hard),
        FlagEntry("BI", "Burundi", .hard),
        FlagEntry("CV", "Cabo Verde", .hard),
        FlagEntry("BQ", "Bonaire", .hard,
                  explanation: "This is Bonaire's flag. Bonaire, Sint Eustatius, and Saba do not share a single collective Caribbean Netherlands flag."),
        FlagEntry("KY", "Cayman Islands", .hard, confusable: ["AI", "FK", "GS", "MS", "NZ", "PN", "SH", "TC", "VG"]),
        FlagEntry("CF", "Central African Republic", .hard),
        FlagEntry("TD", "Chad", .hard, confusable: ["RO"]),
        FlagEntry("CX", "Christmas Island", .hard),
        FlagEntry("CC", "Cocos (Keeling) Islands", .hard),
        FlagEntry("KM", "Comoros", .hard),
        FlagEntry("CK", "Cook Islands", .hard, confusable: ["NZ"]),
        FlagEntry("CW", "Curaçao", .hard),
        FlagEntry("CD", "Democratic Republic of the Congo", .hard),
        FlagEntry("DJ", "Djibouti", .hard),
        FlagEntry("DM", "Dominica", .hard),
        FlagEntry("TL", "Timor-Leste", .hard),
        FlagEntry("GQ", "Equatorial Guinea", .hard),
        FlagEntry("ER", "Eritrea", .hard),
        FlagEntry("SZ", "Eswatini", .hard),
        FlagEntry("FK", "Falkland Islands", .hard, confusable: ["AI", "GS", "KY", "MS", "SH", "VG"]),
        FlagEntry("FO", "Faroe Islands", .hard),
        FlagEntry("GF", "French Guiana", .hard,
                  explanation: "This is an unofficial local flag of French Guiana; the official flag is the French tricolour."),
        FlagEntry("PF", "French Polynesia", .hard, confusable: ["LB"]),
        FlagEntry("TF", "French Southern and Antarctic Lands", .hard),
        FlagEntry("GA", "Gabon", .hard),
        FlagEntry("GM", "Gambia", .hard),
        FlagEntry("GI", "Gibraltar", .hard),
        FlagEntry("GL", "Greenland", .hard),
        FlagEntry("GD", "Grenada", .hard),
        FlagEntry("GP", "Guadeloupe", .hard,
                  explanation: "This is an unofficial local flag of Guadeloupe; the official flag is the French tricolour."),
        FlagEntry("GU", "Guam", .hard),
        FlagEntry("GG", "Guernsey", .hard),
        FlagEntry("GN", "Guinea", .hard),
        FlagEntry("GW", "Guinea-Bissau", .hard),
        FlagEntry("GY", "Guyana", .hard),
        FlagEntry("HM", "Heard Island and McDonald Islands", .hard, askable: false,
                  explanation: "Heard Island and the McDonald Islands use the Australian National Flag and have no separate official flag."),
        FlagEntry("HK", "Hong Kong", .hard),
        FlagEntry("IM", "Isle of Man", .hard),
        FlagEntry("JE", "Jersey", .hard),
        FlagEntry("KI", "Kiribati", .hard),
        FlagEntry("LS", "Lesotho", .hard),
        FlagEntry("LR", "Liberia", .hard),
        FlagEntry("MO", "Macau", .hard),
        FlagEntry("MW", "Malawi", .hard),
        FlagEntry("MV", "Maldives", .hard),
        FlagEntry("ML", "Mali", .hard),
        FlagEntry("MH", "Marshall Islands", .hard),
        FlagEntry("MQ", "Martinique", .hard,
                  explanation: "This is a locally used flag of Martinique rather than the official French tricolour."),
        FlagEntry("MR", "Mauritania", .hard),
        FlagEntry("MU", "Mauritius", .hard),
        FlagEntry("FM", "Micronesia", .hard),
        FlagEntry("MS", "Montserrat", .hard, confusable: ["AI", "FK", "GS", "KY", "NZ", "PN", "SH", "TC", "VG"]),
        FlagEntry("NR", "Nauru", .hard),
        FlagEntry("NC", "New Caledonia", .hard, askable: false,
                  explanation: "This is the FLNKS or Kanak flag, which is politically contested and is often flown alongside the French tricolour."),
        FlagEntry("NE", "Niger", .hard),
        FlagEntry("NU", "Niue", .hard),
        FlagEntry("NF", "Norfolk Island", .hard),
        FlagEntry("MP", "Northern Mariana Islands", .hard),
        FlagEntry("PW", "Palau", .hard),
        FlagEntry("PS", "Palestine", .hard, confusable: ["EH"],
                  explanation: "This is the Palestinian flag; Palestine's international status remains disputed."),
        FlagEntry("PN", "Pitcairn Islands", .hard, confusable: ["GS", "KY", "MS", "NZ", "SH", "VG"]),
        FlagEntry("PR", "Puerto Rico", .hard),
        FlagEntry("CG", "Republic of the Congo", .hard),
        FlagEntry("RW", "Rwanda", .hard),
        FlagEntry("RE", "Réunion", .hard,
                  explanation: "This is an unofficial local flag of Réunion; the official flag is the French tricolour."),
        FlagEntry("BL", "Saint Barthélemy", .hard,
                  explanation: "This is an unofficial armorial flag of Saint Barthélemy; the official flag is the French tricolour."),
        FlagEntry("SH", "Saint Helena", .hard, confusable: ["AI", "FK", "GS", "KY", "MS", "NZ", "PN", "TC", "VG"]),
        FlagEntry("KN", "Saint Kitts and Nevis", .hard),
        FlagEntry("LC", "Saint Lucia", .hard),
        FlagEntry("MF", "Saint Martin", .hard, askable: false),
        FlagEntry("PM", "Saint Pierre and Miquelon", .hard,
                  explanation: "This is a widely used unofficial local flag; the official flag of Saint Pierre and Miquelon is the French tricolour."),
        FlagEntry("VC", "Saint Vincent and the Grenadines", .hard),
        FlagEntry("WS", "Samoa", .hard),
        FlagEntry("SC", "Seychelles", .hard),
        FlagEntry("SL", "Sierra Leone", .hard),
        FlagEntry("SX", "Sint Maarten", .hard),
        FlagEntry("SB", "Solomon Islands", .hard),
        FlagEntry("SO", "Somalia", .hard),
        FlagEntry("GS", "South Georgia and the South Sandwich Islands", .hard,
                  confusable: ["FK", "KY", "MS", "NZ", "PN", "SH", "VG"]),
        FlagEntry("SS", "South Sudan", .hard),
        FlagEntry("SR", "Suriname", .hard),
        FlagEntry("SJ", "Svalbard and Jan Mayen", .hard, askable: false),
        FlagEntry("ST", "São Tomé and Príncipe", .hard),
        FlagEntry("TG", "Togo", .hard),
        FlagEntry("TK", "Tokelau", .hard),
        FlagEntry("TO", "Tonga", .hard),
        FlagEntry("TC", "Turks and Caicos Islands", .hard, confusable: ["AI", "KY", "MS", "SH", "VG"]),
        FlagEntry("TV", "Tuvalu", .hard),
        FlagEntry("UM", "United States Minor Outlying Islands", .hard, askable: false),
        FlagEntry("VI", "United States Virgin Islands", .hard),
        FlagEntry("VU", "Vanuatu", .hard),
        FlagEntry("WF", "Wallis and Futuna", .hard,
                  explanation: "This is a locally used flag of Wallis and Futuna rather than the official French tricolour."),
        FlagEntry("EH", "Western Sahara", .hard, askable: false, confusable: ["PS"],
                  explanation: "The design shown is used by the Sahrawi Arab Democratic Republic; Western Sahara is a disputed territory."),
        FlagEntry("AX", "Åland Islands", .hard),
    ]

    /// The flags that are actually asked about.
    static let askable: [FlagEntry] = all.filter(\.askable)

    static let byCode: [String: FlagEntry] = Dictionary(
        uniqueKeysWithValues: all.map { ($0.code, $0) }
    )

    /// Askable flags bucketed by tier, so drawing distractors does not rescan
    /// the whole catalog for every question in every round.
    private static let askableByDifficulty: [TriviaDifficulty: [FlagEntry]] = Dictionary(
        grouping: askable,
        by: \.difficulty
    )

    /// Whether two flags may appear as options in the same question.
    ///
    /// Confusability is declared on whichever entry the measurement happened to
    /// name, so both directions have to be checked — otherwise Egypt would
    /// exclude Yemen but Yemen would happily offer Egypt.
    static func mayAppearTogether(_ a: FlagEntry, _ b: FlagEntry) -> Bool {
        a.code != b.code
            && !a.confusable.contains(b.code)
            && !b.confusable.contains(a.code)
    }

    /// The four answer choices for a flag question, already shuffled.
    ///
    /// Distractors are drawn fresh from `generator` rather than being fixed in
    /// the question bank. A fixed set is memorable in a way the flag is not:
    /// after a few rounds a player recognises "the one offered with Chile and
    /// Uruguay" and stops looking at the artwork. Redrawing every time means
    /// the only stable signal in the question is the flag itself.
    ///
    /// The caller supplies the generator so the daily challenge can pass its
    /// seeded one and keep every player's round identical. There is deliberately
    /// no convenience overload that reaches for system randomness: a daily built
    /// with a stray `SystemRandomNumberGenerator` would show two players
    /// different options for the same question, and that is exactly the bug this
    /// signature exists to make unrepresentable.
    static func options(
        for entry: FlagEntry,
        distractors count: Int = 3,
        using generator: inout some RandomNumberGenerator
    ) -> [FlagEntry] {
        let sameTier = (askableByDifficulty[entry.difficulty] ?? [])
            .filter { mayAppearTogether(entry, $0) }

        var chosen = Array(sameTier.deterministicallyShuffled(using: &generator).prefix(count))

        // Unreachable today -- the smallest same-tier pool in the catalog is 48
        // candidates, and `everyFlagHasEnoughSameTierAnswers` fails CI if an
        // edit ever takes one below `count`. It stays as a fallback rather than
        // a precondition because this runs inside a lazily initialised `static
        // let`: trapping here would crash the app on launch, and one off-tier
        // option is a far better outcome than that.
        if chosen.count < count {
            let taken = Set(chosen.map(\.code))
            let filler = askable.filter {
                mayAppearTogether(entry, $0) && !taken.contains($0.code)
            }
            chosen += filler.deterministicallyShuffled(using: &generator).prefix(count - chosen.count)
        }

        return (chosen + [entry]).deterministicallyShuffled(using: &generator)
    }
}
