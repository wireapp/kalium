#!/usr/bin/env python3

import importlib.util
import io
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest
import zipfile


SCRIPT_DIR = Path(__file__).parent


def load_script(name, module_name):
    spec = importlib.util.spec_from_file_location(module_name, SCRIPT_DIR / name)
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


class FastSbomTest(unittest.TestCase):
    def test_selective_extraction_includes_nested_legal_files_only(self):
        evidence = load_script("prepare-fast-sbom-evidence.py", "sbom_evidence")
        nested = io.BytesIO()
        with zipfile.ZipFile(nested, "w") as archive:
            archive.writestr("META-INF/NOTICE.txt", "nested notice")
            archive.writestr("Thing.class", b"bytecode")
        outer = io.BytesIO()
        with zipfile.ZipFile(outer, "w") as archive:
            archive.writestr("LICENSE", "outer license")
            archive.writestr("classes.jar", nested.getvalue())
            archive.writestr("Example.class", b"bytecode")

        with tempfile.TemporaryDirectory() as directory:
            destination = Path(directory)
            count = evidence.write_zip_legal_files(outer, destination)
            self.assertEqual(2, count)
            self.assertEqual("outer license", (destination / "LICENSE").read_text())
            self.assertEqual(
                "nested notice",
                (destination / "classes.jar/nested/META-INF/NOTICE.txt").read_text(),
            )
            self.assertFalse(any(destination.rglob("*.class")))

    def test_tracked_notice_supplies_lightweight_license_database(self):
        notice = load_script("generate-third-party-notice.py", "sbom_notice_test")
        content = """# Third-Party Notices

## License Texts

### MIT
<a id="license-mit"></a>

*MIT License*

```
Permission is hereby granted.
```
"""
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "NOTICE.md"
            path.write_text(content)
            database = notice.load_tracked_license_texts(path)
            self.assertEqual("MIT", database["MIT"].spdx_license_key)
            self.assertEqual("Permission is hereby granted.", database["MIT"].text)

    def test_cyclonedx_validation_rejects_unknown_dependency(self):
        finalize = load_script("finalize-fast-sbom.py", "sbom_finalize")
        document = {
            "bomFormat": "CycloneDX",
            "specVersion": "1.6",
            "metadata": {"component": {"bom-ref": "root"}},
            "components": [{"bom-ref": "component"}],
            "dependencies": [
                {"ref": "root", "dependsOn": ["missing"]},
                {"ref": "component", "dependsOn": []},
            ],
        }
        with self.assertRaisesRegex(ValueError, "unknown refs"):
            finalize.validate(document)

    def test_spdx_export_preserves_packages_licenses_and_dependencies(self):
        converter = load_script("cyclonedx-to-spdx.py", "sbom_spdx")
        converter.creation_time = lambda: "2026-01-01T00:00:00Z"
        document = {
            "metadata": {
                "component": {
                    "bom-ref": "pkg:maven/com.wire/kalium@1",
                    "name": "kalium",
                    "version": "1",
                    "purl": "pkg:maven/com.wire/kalium@1",
                }
            },
            "components": [
                {
                    "bom-ref": "pkg:maven/example/demo@2",
                    "name": "demo",
                    "version": "2",
                    "purl": "pkg:maven/example/demo@2",
                    "licenses": [{"license": {"id": "Apache-2.0"}}],
                }
            ],
            "dependencies": [
                {
                    "ref": "pkg:maven/com.wire/kalium@1",
                    "dependsOn": ["pkg:maven/example/demo@2"],
                },
                {"ref": "pkg:maven/example/demo@2", "dependsOn": []},
            ],
        }
        content = converter.convert(document)
        converter.validate_tag_value(content)
        self.assertEqual(2, content.count("PackageName: "))
        self.assertIn("PackageLicenseDeclared: Apache-2.0", content)
        self.assertIn(" DEPENDS_ON ", content)

    def test_customer_bundle_has_exact_shared_contract(self):
        bundler = load_script("bundle-sbom.py", "sbom_bundle")
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            output = root / "output"
            output.mkdir()
            (output / "scan.cdx.json").write_text("{}")
            (output / "scan.spdx").write_text("SPDXVersion: SPDX-2.3\n")
            notice = root / "notice.md"
            notice.write_text("notice")
            archive_path = bundler.bundle(output, notice)
            first_archive = archive_path.read_bytes()
            with zipfile.ZipFile(archive_path) as archive:
                self.assertEqual(list(bundler.CONTRACT_FILENAMES), archive.namelist())
            self.assertEqual(first_archive, bundler.bundle(output, notice).read_bytes())

    def test_duplicate_poms_emit_one_unresolved_component_row(self):
        pom = """<project xmlns="http://maven.apache.org/POM/4.0.0">
  <modelVersion>4.0.0</modelVersion>
  <groupId>example</groupId><artifactId>demo</artifactId><version>1.0</version>
</project>"""
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            (root / "poms/a").mkdir(parents=True)
            (root / "poms/b").mkdir(parents=True)
            (root / "poms/a/demo.pom").write_text(pom)
            (root / "poms/b/demo.pom").write_text(pom)
            (root / "overrides.yaml").write_text("[]\n")
            result = subprocess.run(
                [
                    sys.executable,
                    str(SCRIPT_DIR / "parse-pom-licenses.py"),
                    str(root / "poms"),
                    str(root / "licenses.tsv"),
                    str(root / "missing.txt"),
                    str(root / "overrides.yaml"),
                ],
                check=True,
                capture_output=True,
                text=True,
            )
            self.assertIn("Parsed 1 POMs", result.stdout)
            rows = (root / "licenses.tsv").read_text().splitlines()
            self.assertEqual(2, len(rows))
            self.assertTrue(rows[1].endswith("\tmissing"))
            self.assertEqual("example:demo:1.0\n", (root / "missing.txt").read_text())


if __name__ == "__main__":
    unittest.main()
