package com.claustrophobDev.vinyl.core

// угадываем флаг страны по названию сервера
object Flags {

    private val countries = listOf(
        "RU" to listOf("russia", "россия", "moscow", "москва", "петербург"),
        "DE" to listOf("germany", "германия", "frankfurt", "франкфурт", "berlin", "берлин"),
        "NL" to listOf("netherlands", "нидерланды", "голландия", "amsterdam", "амстердам"),
        "US" to listOf("united states", "usa", "сша", "america", "америка", "new york", "los angeles"),
        "FI" to listOf("finland", "финляндия", "helsinki", "хельсинки"),
        "FR" to listOf("france", "франция", "paris", "париж"),
        "GB" to listOf("united kingdom", "england", "britain", "великобритания", "англия", "london", "лондон"),
        "JP" to listOf("japan", "япония", "tokyo", "токио"),
        "TR" to listOf("turkey", "türkiye", "турция", "istanbul", "стамбул"),
        "SE" to listOf("sweden", "швеция", "stockholm", "стокгольм"),
        "PL" to listOf("poland", "польша", "warsaw", "варшава"),
        "LV" to listOf("latvia", "латвия", "riga", "рига"),
        "LT" to listOf("lithuania", "литва", "vilnius", "вильнюс"),
        "EE" to listOf("estonia", "эстония", "tallinn", "таллин"),
        "KZ" to listOf("kazakhstan", "казахстан", "almaty", "алматы", "astana", "астана"),
        "AE" to listOf("emirates", "uae", "оаэ", "dubai", "дубай"),
        "SG" to listOf("singapore", "сингапур"),
        "HK" to listOf("hong kong", "гонконг"),
        "CH" to listOf("switzerland", "швейцария", "zurich", "цюрих"),
        "AT" to listOf("austria", "австрия", "vienna", "вена"),
        "IT" to listOf("italy", "италия", "milan", "милан"),
        "ES" to listOf("spain", "испания", "madrid", "мадрид"),
        "CA" to listOf("canada", "канада", "toronto", "торонто"),
        "UA" to listOf("ukraine", "украина", "kyiv", "киев"),
        "GE" to listOf("georgia", "грузия", "tbilisi", "тбилиси"),
        "AM" to listOf("armenia", "армения", "yerevan", "ереван"),
        "RS" to listOf("serbia", "сербия", "belgrade", "белград"),
        "CZ" to listOf("czech", "чехия", "prague", "прага"),
        "RO" to listOf("romania", "румыния", "bucharest"),
        "BG" to listOf("bulgaria", "болгария", "sofia", "софия"),
        "MD" to listOf("moldova", "молдова"),
        "HU" to listOf("hungary", "венгрия", "budapest"),
        "NO" to listOf("norway", "норвегия", "oslo"),
        "DK" to listOf("denmark", "дания", "copenhagen"),
        "IE" to listOf("ireland", "ирландия", "dublin"),
        "PT" to listOf("portugal", "португалия", "lisbon"),
        "GR" to listOf("greece", "греция", "athens"),
        "CY" to listOf("cyprus", "кипр"),
        "IL" to listOf("israel", "израиль"),
        "IN" to listOf("india", "индия", "mumbai"),
        "KR" to listOf("korea", "корея", "seoul"),
        "AU" to listOf("australia", "австралия", "sydney"),
        "BR" to listOf("brazil", "бразилия"),
        "BY" to listOf("belarus", "беларусь", "minsk", "минск"),
        "UZ" to listOf("uzbekistan", "узбекистан", "tashkent")
    )

    // эти коды часто просто английские слова (in, no, it...), по ним не угадываем
    private val skipCodes = setOf("IN", "NO", "IT", "ES", "AT", "AM", "CA", "BY")

    private val codeRegex = Regex(
        "(?<![A-Za-z])(" + countries.map { it.first }.filter { it !in skipCodes }.joinToString("|") + ")(?![A-Za-z])"
    )

    // возвращает флаг и название уже без флага
    fun split(name: String): Pair<String, String> {
        val cps = name.codePoints().toArray()
        for (i in 0 until cps.size - 1) {
            if (isRegional(cps[i]) && isRegional(cps[i + 1])) {
                val flag = String(cps, i, 2)
                val clean = name.replace(flag, " ")
                    .trim(' ', '|', '-', '_', '·', '•', '\t')
                    .replace(Regex("\\s{2,}"), " ")
                return Pair(flag, clean.ifEmpty { name })
            }
        }
        return Pair(guess(name), name)
    }

    private fun guess(name: String): String {
        val lower = name.lowercase()
        for ((code, words) in countries) {
            if (words.any { it in lower }) return flagOf(code)
        }
        val m = codeRegex.find(name)
        if (m != null) return flagOf(m.value)
        return "🌐"
    }

    private fun isRegional(cp: Int) = cp in 0x1F1E6..0x1F1FF

    fun flagOf(code: String): String {
        return code.uppercase().map { String(Character.toChars(0x1F1E6 + (it - 'A'))) }.joinToString("")
    }
}
