"""Validate the shipped binary and representative offline lookups (no network)."""
from array import array
import gzip
import hashlib
import json
from pathlib import Path
import struct
import sys
from build_dictionary import ASSETS, SOURCE, fold, read_rules


class Index:
    def __init__(self, path):
        with gzip.open(path, "rb") as stream:
            assert stream.read(4) == b"RVD2"
            self.count, size, self.block = struct.unpack("<III", stream.read(12))
            assert self.block == 16
            self.offsets = array("I", stream.read(4 * ((self.count + 15) // 16 + 1)))
            self.related = array("i", stream.read(4 * self.count))
            if sys.byteorder != "little":
                self.offsets.byteswap()
                self.related.byteswap()
            self.data = stream.read(size)
            assert len(self.data) == size and stream.read() == b""

    def word(self, index):
        position = self.offsets[index // self.block]
        encoded = b""
        for _ in range(index % self.block + 1):
            prefix, suffix = self.data[position:position + 2]
            position += 2
            encoded = encoded[:prefix] + self.data[position:position + suffix]
            position += suffix
        return encoded.decode("utf-8")

    def lower_bound(self, key):
        low, high = 0, self.count
        while low < high:
            middle = (low + high) // 2
            if fold(self.word(middle)) < key:
                low = middle + 1
            else:
                high = middle
        return low

    def exact(self, word):
        start, key = self.lower_bound(fold(word)), fold(word)
        for index in range(start, min(start + 128, self.count)):
            candidate = self.word(index)
            if fold(candidate) != key:
                break
            if candidate == word:
                return index
        return None

    def suggest(self, typed):
        key = fold(typed)
        if not 2 <= len(key) <= 32 or not key.isalpha():
            return []
        start = self.lower_bound(key)
        exact, completions, result = [], [], []
        for index in range(start, min(start + 512, self.count)):
            word = self.word(index)
            if not fold(word).startswith(key):
                break
            if fold(word) == key:
                exact.append((index, word))
            else:
                completions.append(word)
        def add(word):
            if word not in result:
                result.append(word)
        for _, word in sorted(exact, key=lambda item: (0 if item[1] == typed.lower() else 1, item[1])):
            add(word)
        for index, _ in exact:
            if self.related[index] >= 0:
                add(self.word(self.related[index]))
        for word in sorted(completions, key=lambda word: (len(word), word)):
            add(word)
        return result[:3]


def validate():
    path = ASSETS / "pt_br.rvd.gz"
    manifest = json.loads((ASSETS / "manifest.json").read_text(encoding="utf-8"))
    assert hashlib.sha256(path.read_bytes()).hexdigest() == manifest["asset_sha256"]
    index = Index(path)
    assert index.count == manifest["word_count"]
    previous_key, position, encoded = ("", ""), 0, b""
    for number in range(index.count):
        if number % 16 == 0:
            assert position == index.offsets[number // 16]
            encoded = b""
        prefix, suffix = index.data[position:position + 2]
        assert prefix <= len(encoded) and suffix > 0
        position += 2
        encoded = encoded[:prefix] + index.data[position:position + suffix]
        position += suffix
        word = encoded.decode("utf-8")
        key = fold(word), word
        assert key > previous_key and word.isalpha() and len(encoded) <= 128
        assert all(ord(c) <= 65535 for c in word)
        assert index.related[number] == -1 or 0 <= index.related[number] < index.count
        previous_key = key
    assert position == len(index.data) == index.offsets[-1]
    for word in ("teste", "testa", "testando", "testar", "testes", "casa", "casas", "avião", "computador", "computadores"):
        assert index.exact(word) is not None, word
    for word in ("testa", "teste"):
        assert index.word(index.related[index.exact(word)]) == "testando", word
    assert index.exact("testandoooo") is None
    _, _, excluded = read_rules((SOURCE / "pt_BR.aff").read_text(encoding="utf-8-sig"))
    for entry in (SOURCE / "pt_BR.dic").read_text(encoding="utf-8-sig").splitlines()[1:]:
        if not entry.strip():
            continue
        word, _, flags = entry.split()[0].partition("/")
        if excluded.intersection(flags):
            assert index.exact(word) is None, f"Excluded upstream word: {word}"
    start = index.lower_bound("testan")
    assert index.word(start).startswith("testan")
    for typed in ("testa", "teste", "testan"):
        candidates = index.suggest(typed)
        assert "testando" in candidates, (typed, candidates)
        assert len(candidates) <= 3 and len(candidates) == len(set(candidates))
        print(json.dumps({typed: candidates}, ensure_ascii=True))
    assert "avião" in index.suggest("aviao")
    assert "computador" in index.suggest("computador")
    assert index.suggest("testandoooo") == []
    print(f"Validated all {index.count:,} records, UTF-8, ordering, offsets, links, checksum and sample words.")
    print(f"Compressed asset: {path.stat().st_size / 1_000_000:.2f} MB; packed runtime arrays: {manifest['runtime_index_bytes'] / 1_000_000:.2f} MB.")


if __name__ == "__main__":
    validate()
