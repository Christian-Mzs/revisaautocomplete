"""Build Revisa's offline suggestion index from the pinned VERO Hunspell sources.

Python standard library only. Run from any directory; no network is used.
This exports standalone lowercase words, not Hunspell's compound/spelling engine.
"""
from pathlib import Path
from dataclasses import dataclass
from collections import defaultdict
import gzip
import hashlib
import json
import re
import struct
import unicodedata

HERE = Path(__file__).resolve().parent
SOURCE = HERE / "source"
ASSETS = HERE.parents[1] / "app/src/main/assets/dictionaries"


def fold(word):
    return "".join(c for c in unicodedata.normalize("NFD", word.lower())
                   if unicodedata.category(c) != "Mn")


@dataclass
class Rule:
    prefix: bool
    strip: str
    add: str
    continuation: str
    condition: re.Pattern
    cross: bool

    def apply(self, word):
        if not self.condition.search(word):
            return None
        if self.prefix:
            if not word.startswith(self.strip):
                return None
            return self.add + word[len(self.strip):]
        if self.strip and not word.endswith(self.strip):
            return None
        return (word[:-len(self.strip)] if self.strip else word) + self.add


def read_rules(text):
    prefixes, suffixes = defaultdict(list), defaultdict(list)
    cross, excluded = {}, set()
    for line in text.splitlines():
        parts = line.split("#", 1)[0].split()
        if not parts:
            continue
        tag = parts[0]
        if tag in ("NOSUGGEST", "FORBIDDENWORD"):
            excluded.add(parts[1])
        if tag not in ("PFX", "SFX"):
            continue
        if len(parts) == 4 and parts[2] in ("Y", "N"):
            cross[tag, parts[1]] = parts[2] == "Y"
            continue
        strip = "" if parts[2] == "0" else parts[2]
        addition, _, continuation = parts[3].partition("/")
        addition = "" if addition == "0" else addition
        condition = parts[4] if len(parts) > 4 else "."
        pattern = re.compile("^" + condition if tag == "PFX" else condition + "$")
        rule = Rule(tag == "PFX", strip, addition, continuation, pattern,
                    cross[tag, parts[1]])
        (prefixes if rule.prefix else suffixes)[parts[1]].append(rule)
    return prefixes, suffixes, excluded


def expand(word, flags, prefixes, suffixes, excluded):
    if excluded.intersection(flags):
        return set()
    result = {word}
    # Prefix and suffix cross-products only when both affix classes permit it.
    prefixed = [(word, None)]
    for flag in flags:
        for rule in prefixes[flag]:
            new = rule.apply(word)
            if new and not excluded.intersection(rule.continuation):
                result.add(new)
                prefixed.append((new, rule))
    for stem, prefix_rule in prefixed:
        for flag in flags:
            for rule in suffixes[flag]:
                if prefix_rule and not (prefix_rule.cross and rule.cross):
                    continue
                new = rule.apply(stem)
                if new is None or excluded.intersection(rule.continuation):
                    continue
                result.add(new)
                # Hunspell continuation classes allow a second suffix.
                for continuation in rule.continuation:
                    for following in suffixes[continuation]:
                        more = following.apply(new)
                        if more and not excluded.intersection(following.continuation):
                            result.add(more)
    return result


def eligible(word):
    # CurrentWordExtractor handles letter-only tokens; omit proper names and
    # hyphenated compounds instead of offering incomplete compound fragments.
    return 2 <= len(word) <= 32 and word.isalpha() and word == word.lower()


def build():
    prefixes, suffixes, excluded = read_rules((SOURCE / "pt_BR.aff").read_text(encoding="utf-8-sig"))
    entries = (SOURCE / "pt_BR.dic").read_text(encoding="utf-8-sig").splitlines()
    words, related, excluded_entries = set(), {}, set()
    for entry in entries[1:]:
        if not entry.strip():
            continue
        head = entry.split()[0]
        word, _, flags = head.partition("/")
        if excluded.intersection(flags):
            excluded_entries.add(word)
            continue
        if not eligible(word):
            continue
        forms = {form for form in expand(word, flags, prefixes, suffixes, excluded) if eligible(form)}
        words.update(forms)
        # Link forms to a gerund actually produced by this verb's affix rules.
        gerunds = [form for form in forms if form.endswith(("ando", "endo", "indo"))]
        if word.endswith(("ar", "er", "ir")) and gerunds:
            gerund = min(gerunds, key=lambda form: (len(form), form))
            for form in sorted(forms):
                related.setdefault(form, gerund)
    # An explicit exclusion overrides a form generated from another entry.
    words.difference_update(excluded_entries)
    ordered = sorted(words, key=lambda word: (fold(word), word))
    ids = {word: index for index, word in enumerate(ordered)}
    data, offsets, previous = bytearray(), [], b""
    for index, word in enumerate(ordered):
        encoded = word.encode("utf-8")
        if index % 16 == 0:
            offsets.append(len(data))
            previous = b""
        common = 0
        while common < min(len(previous), len(encoded)) and previous[common] == encoded[common]:
            common += 1
        suffix = encoded[common:]
        data.extend(bytes((common, len(suffix))))
        data.extend(suffix)
        previous = encoded
    offsets.append(len(data))
    # Little-endian RVD2: count, byte count, block size, block offsets,
    # related-word IDs, front-coded UTF-8. A full word restarts every 16 entries.
    ASSETS.mkdir(parents=True, exist_ok=True)
    output = ASSETS / "pt_br.rvd.gz"
    with output.open("wb") as raw:
        with gzip.GzipFile(fileobj=raw, mode="wb", filename="", mtime=0, compresslevel=9) as stream:
            stream.write(b"RVD2" + struct.pack("<III", len(ordered), len(data), 16))
            for offset in offsets:
                stream.write(struct.pack("<I", offset))
            for word in ordered:
                stream.write(struct.pack("<i", ids.get(related.get(word), -1)))
            stream.write(data)
    manifest = {
        "source": "https://github.com/LibreOffice/dictionaries/tree/" + (SOURCE / "upstream-commit.txt").read_text().strip() + "/pt_BR",
        "word_count": len(ordered), "front_coded_bytes": len(data),
        "runtime_index_bytes": len(data) + 4 * len(offsets) + 4 * len(ordered),
        "asset_bytes": output.stat().st_size,
        "asset_sha256": hashlib.sha256(output.read_bytes()).hexdigest(),
        "sources_sha256": {p.name: hashlib.sha256(p.read_bytes()).hexdigest()
                           for p in sorted(SOURCE.iterdir()) if p.is_file()},
    }
    (ASSETS / "manifest.json").write_text(json.dumps(manifest, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    for name in ("README_pt_BR.txt", "README_en.txt"):
        (ASSETS / name).write_bytes((SOURCE / name).read_bytes())
    print(json.dumps(manifest, ensure_ascii=True, indent=2))


if __name__ == "__main__":
    build()
