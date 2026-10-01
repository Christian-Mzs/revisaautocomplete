package com.example.ime

import java.io.DataInputStream
import java.io.BufferedInputStream
import java.io.InputStream
import java.text.Normalizer
import java.util.Locale
import java.util.zip.GZIPInputStream

/** Packed dictionary: byte storage and integer offsets, rather than millions of Strings. */
class OfflineDictionary private constructor(
    private val offsets: IntArray,
    private val related: IntArray,
    private val bytes: ByteArray
) {
    val wordCount: Int get() = related.size

    fun suggest(typed: String): List<String> {
        val key = fold(typed)
        if (key.length < 2 || key.length > 32 || !key.all { it.isLetter() }) return emptyList()
        val start = lowerBound(key)
        val exact = ArrayList<Pair<Int, String>>()
        val completions = ArrayList<String>()
        // Bounded prefix search: never scan the entire dictionary on a keypress.
        for (index in start until minOf(start + MAX_PREFIX_CANDIDATES, wordCount)) {
            val candidate = word(index)
            val normalized = fold(candidate)
            if (!normalized.startsWith(key)) break
            if (normalized == key) exact.add(index to candidate) else completions.add(candidate)
        }
        val result = LinkedHashSet<String>()
        val lower = typed.lowercase(LOCALE)
        exact.sortedWith(compareBy<Pair<Int, String>> { if (it.second == lower) 0 else 1 }.thenBy { it.second })
            .forEach { result.add(it.second) }
        // Related verb form comes from the exported affix rules, not a guessed ending.
        for ((index, _) in exact) {
            val target = related[index]
            if (target >= 0) result.add(word(target))
        }
        completions.sortedWith(compareBy<String> { it.length }.thenBy { it })
            .forEach { result.add(it) }
        return result.take(3)
    }

    private fun lowerBound(key: String): Int {
        var low = 0
        var high = wordCount
        while (low < high) {
            val middle = low + (high - low) / 2
            if (fold(word(middle)) < key) low = middle + 1 else high = middle
        }
        return low
    }

    private fun word(index: Int): String =
        decodeWord(index)

    private fun decodeWord(index: Int): String {
        val block = index / BLOCK_SIZE
        var position = offsets[block]
        val buffer = ByteArray(128)
        var length = 0
        repeat(index % BLOCK_SIZE + 1) {
            val prefix = bytes[position++].toInt() and 255
            val suffix = bytes[position++].toInt() and 255
            System.arraycopy(bytes, position, buffer, prefix, suffix)
            position += suffix
            length = prefix + suffix
        }
        return String(buffer, 0, length, Charsets.UTF_8)
    }

    companion object {
        private val LOCALE = Locale.forLanguageTag("pt-BR")
        const val ASSET_PATH = "dictionaries/pt_br.rvd.gz"
        private const val BLOCK_SIZE = 16
        private const val MAX_PREFIX_CANDIDATES = 512

        fun fold(word: String): String = Normalizer.normalize(word.lowercase(LOCALE), Normalizer.Form.NFD)
            .filter { Character.getType(it) != Character.NON_SPACING_MARK.toInt() }

        fun read(input: InputStream): OfflineDictionary {
            DataInputStream(BufferedInputStream(
                GZIPInputStream(BufferedInputStream(input, 64 * 1024), 64 * 1024), 64 * 1024
            )).use { stream ->
                val magic = ByteArray(4)
                stream.readFully(magic)
                require(String(magic, Charsets.US_ASCII) == "RVD2") { "Invalid dictionary format" }
                fun integer() = Integer.reverseBytes(stream.readInt())
                val count = integer()
                val byteCount = integer()
                require(integer() == BLOCK_SIZE) { "Invalid dictionary block size" }
                require(count in 1..8_000_000 && byteCount in 1..160_000_000) { "Invalid dictionary size" }
                val blockCount = (count + BLOCK_SIZE - 1) / BLOCK_SIZE
                val offsets = IntArray(blockCount + 1) { integer() }
                require(offsets.first() == 0 && offsets.last() == byteCount)
                for (i in 0 until blockCount) require(offsets[i] < offsets[i + 1])
                val related = IntArray(count) { integer() }
                require(related.all { it == -1 || it in 0 until count })
                val bytes = ByteArray(byteCount)
                stream.readFully(bytes)
                require(stream.read() == -1) { "Unexpected dictionary data" }
                var position = 0
                var previousLength = 0
                for (index in 0 until count) {
                    if (index % BLOCK_SIZE == 0) {
                        require(position == offsets[index / BLOCK_SIZE])
                        previousLength = 0
                    }
                    require(position + 2 <= byteCount)
                    val prefix = bytes[position++].toInt() and 255
                    val suffix = bytes[position++].toInt() and 255
                    require(prefix <= previousLength && suffix > 0 && prefix + suffix <= 128)
                    position += suffix
                    require(position <= byteCount)
                    previousLength = prefix + suffix
                }
                require(position == byteCount)
                return OfflineDictionary(offsets, related, bytes)
            }
        }
    }
}
