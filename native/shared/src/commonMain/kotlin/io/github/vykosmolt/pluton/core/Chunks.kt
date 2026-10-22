package io.github.vykosmolt.pluton.core

// Common French chunks, kept local and small. A chunk becomes a normal SRS card with a synthetic lemma,
// so it uses the same FSRS scheduler, cloze grading and run queue as a single word.

data class Chunk(
    val text: String,
    val gloss: String,
    val example: String,
    val translation: String,
    val lemmas: List<String>,
    val level: String = "A2",
)

val CHUNKS: List<Chunk> = listOf(
    Chunk("il y a", "there is / there are", "Il y a un chat dans la maison.", "There is a cat in the house.", listOf("il", "y", "avoir"), "A1"),
    Chunk("avoir besoin de", "to need", "Je vais avoir besoin de ton aide.", "I am going to need your help.", listOf("avoir", "besoin", "de"), "A1"),
    Chunk("avoir envie de", "to want / to feel like", "Il va avoir envie de chocolat.", "He is going to want chocolate.", listOf("avoir", "envie", "de"), "A2"),
    Chunk("faire attention", "to be careful", "Il faut faire attention.", "You have to be careful.", listOf("faire", "attention"), "A1"),
    Chunk("en train de", "in the middle of (doing)", "Je suis en train de lire.", "I am in the middle of reading.", listOf("être", "train", "de"), "A2"),
    Chunk("tout à coup", "suddenly", "Tout à coup, il a plu.", "Suddenly, it started raining.", listOf("tout", "à", "coup"), "A2"),
    Chunk("en fait", "actually / in fact", "En fait, je ne sais pas.", "Actually, I don't know.", listOf("en", "fait"), "A2"),
    Chunk("par exemple", "for example", "Par exemple, le chat dort.", "For example, the cat is sleeping.", listOf("par", "exemple"), "A1"),
    Chunk("au revoir", "goodbye", "Au revoir et à demain !", "Goodbye and see you tomorrow!", listOf("au", "revoir"), "A1"),
    Chunk("s'il vous plaît", "please (formal)", "Un café, s'il vous plaît.", "A coffee, please.", listOf("si", "il", "vous", "plaire"), "A1"),
    Chunk("prendre rendez-vous", "to make an appointment", "Je dois prendre rendez-vous chez le médecin.", "I have to make an appointment with the doctor.", listOf("prendre", "rendez-vous"), "A2"),
    Chunk("se rendre compte", "to realise", "Il va se rendre compte de l'erreur.", "He is going to realise the mistake.", listOf("se", "rendre", "compte"), "B1"),
    Chunk("au fait", "by the way", "Au fait, tu viens ce soir ?", "By the way, are you coming tonight?", listOf("au", "fait"), "A2"),
    Chunk("d'un coup", "all at once", "Il a tout mangé d'un coup.", "He ate everything all at once.", listOf("coup"), "B1"),
    Chunk("peu à peu", "little by little", "Peu à peu, il a compris.", "Little by little, he understood.", listOf("peu", "à"), "A2"),
    Chunk("de temps en temps", "from time to time", "De temps en temps, je vais au cinéma.", "From time to time, I go to the cinema.", listOf("temps"), "A2"),
    Chunk("à propos", "by the way / about", "À propos, tu as vu le chat ?", "By the way, have you seen the cat?", listOf("à", "propos"), "A2"),
    Chunk("quand même", "anyway / still", "C'est cher, mais je vais quand même l'acheter.", "It's expensive, but I'm still going to buy it.", listOf("quand", "même"), "B1"),
    Chunk("bien sûr", "of course", "Bien sûr, tu peux venir.", "Of course, you can come.", listOf("bien", "sûr"), "A1"),
    Chunk("en retard", "late", "Je suis en retard.", "I am late.", listOf("retard"), "A1"),
    Chunk("à pied", "on foot", "Je vais au travail à pied.", "I go to work on foot.", listOf("pied"), "A1"),
    Chunk("tout de suite", "right away", "Viens tout de suite !", "Come right away!", listOf("tout", "suite"), "A1"),
    Chunk("mal à la tête", "a headache", "J'ai mal à la tête.", "I have a headache.", listOf("mal", "tête"), "A2"),
    Chunk("faire les courses", "to do the shopping", "Je dois faire les courses.", "I have to do the shopping.", listOf("faire", "course"), "A2"),
)

fun chunksFor(lemma: String): List<Chunk> = CHUNKS.filter { lemma in it.lemmas }

/** Synthetic chunk lemmas must not be counted as single words (e.g. in AI prompts). */
fun isChunk(lemma: String): Boolean = lemma.startsWith("chunk:")

/** A normal Word for the SRS: the synthetic lemma keeps chunks out of the frequency deck until added. */
fun chunkWord(chunk: Chunk): Word = Word(
    lemma = "chunk:${chunk.text}",
    pos = "chunk",
    display = chunk.text,
    gloss = chunk.gloss,
    sentence = chunk.example,
    translation = chunk.translation,
    form = chunk.text,
)
