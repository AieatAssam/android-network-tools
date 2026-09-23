import unittest

from check_snmp_classes import REQUIRED_CLASSES, missing_classes


def class_row(class_name: str) -> str:
    return f"C d 1 1 85 {class_name}"


class SnmpClassGateTest(unittest.TestCase):
    def test_all_required_defined_class_rows_pass(self) -> None:
        output = "\n".join(class_row(name) for name in sorted(REQUIRED_CLASSES))

        self.assertEqual(missing_classes(output), set())

    def test_each_missing_class_is_reported_independently(self) -> None:
        for missing in REQUIRED_CLASSES:
            with self.subTest(missing=missing):
                output = "\n".join(class_row(name) for name in REQUIRED_CLASSES - {missing})

                self.assertEqual(missing_classes(output), {missing})

    def test_references_and_member_signatures_do_not_count_as_defined_classes(self) -> None:
        output = "\n".join(
            (
                "C r 0 1 40 org.snmp4j.security.AuthSHA",
                "M d 1 1 45 org.snmp4j.security.PrivAES128 g.a get()",
                "F d 1 0 4 org.snmp4j.security.USM g.a value",
                "C d 1 1 10 org.snmp4j.security.USM$Nested",
            )
        )

        self.assertEqual(missing_classes(output), REQUIRED_CLASSES)

    def test_class_prefixes_do_not_match_longer_class_names(self) -> None:
        output = "\n".join(
            (
                class_row("org.snmp4j.security.AuthSHAExtra"),
                class_row("org.snmp4j.security.PrivAES128$Inner"),
                class_row("org.snmp4j.security.USMImpl"),
            )
        )

        self.assertEqual(missing_classes(output), REQUIRED_CLASSES)


if __name__ == "__main__":
    unittest.main()
