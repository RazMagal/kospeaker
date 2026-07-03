package com.k2fsa.sherpa.onnx.tts.engine.phonikud

/**
 * Char-level tokenizer for the DictaBERT char-menaked diacritizer
 * (dicta-il/dictabert-large-char-menaked). Pure logic over a caller-supplied vocab map.
 *
 * The real model uses a WordPiece/char tokenizer whose vocabulary maps each single character
 * to an id and uses the standard BERT special tokens. This class reproduces the framing:
 *   [CLS] c0 c1 c2 ... [SEP]
 * with attention_mask, token_type_ids (all 0), char offsets, and optional padding.
 *
 * Expected special-token keys in the vocab map (BERT convention):
 *   "[CLS]", "[SEP]", "[PAD]", "[UNK]"
 * Any character not present in the vocab is mapped to the id of "[UNK]".
 *
 * PURE Kotlin/JVM: no Android, no ONNX. The vocab map is loaded elsewhere (later round) and
 * passed in here.
 */
object HebrewCharTokenizer {

    const val CLS = "[CLS]"
    const val SEP = "[SEP]"
    const val PAD = "[PAD]"
    const val UNK = "[UNK]"

    /**
     * @param inputIds      token ids: [CLS] ... [SEP] (+ [PAD] padding if requested)
     * @param attentionMask 1 for real tokens, 0 for padding
     * @param tokenTypeIds  all zeros (single-segment)
     * @param offsets       (start, end) char offset in the original text for each token;
     *                      special tokens and padding use (0, 0)
     */
    data class Encoding(
        val inputIds: LongArray,
        val attentionMask: LongArray,
        val tokenTypeIds: LongArray,
        val offsets: List<Pair<Int, Int>>,
    )

    /**
     * Tokenize [text] character by character.
     *
     * @param vocab     maps token string -> id. MUST contain [CLS], [SEP] and (recommended)
     *                  [PAD] and [UNK]. Characters absent from the vocab fall back to [UNK].
     * @param padToLength if non-null and greater than the produced length, right-pad with [PAD].
     *                    (No truncation is performed; the caller should chunk long inputs.)
     */
    fun tokenize(text: String, vocab: Map<String, Int>, padToLength: Int? = null): Encoding {
        val clsId = requireNotNull(vocab[CLS]) { "vocab is missing the \"$CLS\" token" }
        val sepId = requireNotNull(vocab[SEP]) { "vocab is missing the \"$SEP\" token" }
        val unkId = vocab[UNK]

        val ids = ArrayList<Long>(text.length + 2)
        val offsets = ArrayList<Pair<Int, Int>>(text.length + 2)

        ids.add(clsId.toLong())
        offsets.add(0 to 0)

        text.forEachIndexed { i, c ->
            val key = c.toString()
            val id = vocab[key] ?: unkId
                ?: error("character '$key' not in vocab and no \"$UNK\" token provided")
            ids.add(id.toLong())
            offsets.add(i to i + 1)
        }

        ids.add(sepId.toLong())
        offsets.add(0 to 0)

        val realLen = ids.size
        val mask = ArrayList<Long>(realLen).apply { repeat(realLen) { add(1L) } }

        if (padToLength != null && padToLength > realLen) {
            val padId = requireNotNull(vocab[PAD]) { "vocab is missing the \"$PAD\" token" }.toLong()
            repeat(padToLength - realLen) {
                ids.add(padId)
                mask.add(0L)
                offsets.add(0 to 0)
            }
        }

        val tokenTypeIds = LongArray(ids.size) // all zeros
        return Encoding(
            inputIds = ids.toLongArray(),
            attentionMask = mask.toLongArray(),
            tokenTypeIds = tokenTypeIds,
            offsets = offsets,
        )
    }
}
