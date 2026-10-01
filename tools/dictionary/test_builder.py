import unittest
from build_dictionary import read_rules, expand, fold


class BuilderTest(unittest.TestCase):
    def test_suffix_condition_and_plural(self):
        p, s, excluded = read_rules("SFX A Y 1\nSFX A 0 s [aeiou]\n")
        self.assertEqual({"casa", "casas"}, expand("casa", "A", p, s, excluded))
        self.assertEqual({"mar"}, expand("mar", "A", p, s, excluded))

    def test_prefix_suffix_cross_permission(self):
        text = "PFX P Y 1\nPFX P 0 re .\nSFX S Y 1\nSFX S ar ando ar\n"
        p, s, excluded = read_rules(text)
        self.assertEqual({"testar", "retestar", "testando", "retestando"}, expand("testar", "PS", p, s, excluded))
        p, s, excluded = read_rules(text.replace("PFX P Y", "PFX P N"))
        self.assertNotIn("retestando", expand("testar", "PS", p, s, excluded))

    def test_continuation_and_excluded_flags(self):
        p, s, excluded = read_rules("NOSUGGEST X\nFORBIDDENWORD Z\nSFX A Y 1\nSFX A 0 a/B .\nSFX B Y 1\nSFX B 0 s .\n")
        self.assertEqual({"cas", "casa", "casas"}, expand("cas", "A", p, s, excluded))
        self.assertEqual(set(), expand("cas", "AX", p, s, excluded))
        self.assertEqual(set(), expand("cas", "AZ", p, s, excluded))

    def test_accent_fold(self):
        self.assertEqual("aviacao", fold("AVIAÇÃO"))


if __name__ == "__main__":
    unittest.main()
