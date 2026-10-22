package io.github.vykosmolt.pluton.core

// A small, curated French sound syllabus. The UI speaks these with the device voice; no recorded audio is needed.
// Minimal pairs drive perception; phrase lists cover features that are not segmental (liaison, silent letters, e caduc).

data class SoundPair(val a: String, val b: String, val hint: String = "")

data class SoundLesson(
    val id: String,
    val title: String,
    val tip: String,
    val pairs: List<SoundPair> = emptyList(),
    val phrases: List<String> = emptyList(),
)

val SOUND_LESSONS: List<SoundLesson> = listOf(
    SoundLesson(
        id = "nasal",
        title = "Les nasales",
        tip = "Nasal vowels are made through the nose, without closing the mouth. /ɑ̃/ is the sound in “dans”, /ɔ̃/ in “don”, /ɛ̃/ in “vin”.",
        pairs = listOf(
            SoundPair("dans", "don", "/ɑ̃/ vs /ɔ̃/"),
            SoundPair("sans", "son", "/ɑ̃/ vs /ɔ̃/"),
            SoundPair("tant", "ton", "/ɑ̃/ vs /ɔ̃/"),
            SoundPair("vin", "vent", "/ɛ̃/ vs /ɑ̃/"),
            SoundPair("pain", "pan", "/ɛ̃/ vs /ɑ̃/"),
        ),
    ),
    SoundLesson(
        id = "yu",
        title = "Tu ou tout ?",
        tip = "/y/ is said with the lips rounded and the tongue forward, like a tight “ee” with round lips. /u/ is the “oo” in “tout”.",
        pairs = listOf(
            SoundPair("tu", "tout", "/y/ vs /u/"),
            SoundPair("rue", "roue", "/y/ vs /u/"),
            SoundPair("vu", "vous", "/y/ vs /u/"),
            SoundPair("dessus", "dessous", "/y/ vs /u/"),
            SoundPair("pur", "pour", "/y/ vs /u/"),
        ),
    ),
    SoundLesson(
        id = "r",
        title = "Le r français",
        tip = "The French /ʁ/ is made at the back of the throat, not with the tongue tip. It should not sound like the English r.",
        pairs = listOf(
            SoundPair("rire", "lire", "/ʁ/ vs /l/"),
            SoundPair("rue", "lue", "/ʁ/ vs /l/"),
        ),
    ),
    SoundLesson(
        id = "sz",
        title = "Poisson ou poison ?",
        tip = "A single s between vowels is usually /z/; “ss” is /s/. The difference changes the word.",
        pairs = listOf(
            SoundPair("poisson", "poison", "/s/ vs /z/"),
            SoundPair("coussin", "cousin", "/s/ vs /z/"),
            SoundPair("dessert", "désert", "/s/ vs /z/"),
        ),
    ),
    SoundLesson(
        id = "shj",
        title = "Bouche ou bouge ?",
        tip = "“ch” is /ʃ/ (as in “chat”); “j” and a soft “g” are /ʒ/ (as in “je”). Keep the vocal cords moving for /ʒ/.",
        pairs = listOf(
            SoundPair("bouche", "bouge", "/ʃ/ vs /ʒ/"),
            SoundPair("cache", "cage", "/ʃ/ vs /ʒ/"),
        ),
    ),
    SoundLesson(
        id = "finals",
        title = "La consonne finale",
        tip = "Many final consonants are silent in the masculine and pronounced in the feminine. Listen for the difference.",
        pairs = listOf(
            SoundPair("petit", "petite", "final t"),
            SoundPair("grand", "grande", "final d"),
            SoundPair("vert", "verte", "final t"),
        ),
    ),
    SoundLesson(
        id = "liaison",
        title = "La liaison",
        tip = "A normally silent final consonant links to a following vowel: les‿amis, vous‿avez, un‿homme.",
        phrases = listOf("les amis", "vous avez", "un homme", "trois ans", "c'est ici"),
    ),
    SoundLesson(
        id = "silent",
        title = "Les lettres muettes",
        tip = "Final letters are often silent: the t in “chat”, the p in “beaucoup”, the s in “temps”.",
        phrases = listOf("chat", "petit", "beaucoup", "temps", "parlent"),
    ),
    SoundLesson(
        id = "caduc",
        title = "Le e qui tombe",
        tip = "The small “e” (schwa) is often dropped in fast speech: “je te le dis” can sound like “j'te l'dis”. Dropping it is normal, not lazy.",
        phrases = listOf("je te le dis", "petit à petit", "samedi matin", "une petite fenêtre"),
    ),
)
