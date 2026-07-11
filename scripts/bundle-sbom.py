#!/usr/bin/env python3
"""Create Kalium's mode-independent customer SBOM delivery archive."""

from pathlib import Path
import sys
import zipfile


CONTRACT_FILENAMES = ("scan.cdx.json", "scan.spdx", "THIRD-PARTY-NOTICE.md")


def bundle(output_dir, notice_path):
    output_dir = Path(output_dir)
    notice_path = Path(notice_path)
    inputs = (
        output_dir / CONTRACT_FILENAMES[0],
        output_dir / CONTRACT_FILENAMES[1],
        notice_path,
    )
    missing = [str(path) for path in inputs if not path.is_file()]
    if missing:
        raise FileNotFoundError("missing canonical bundle input(s): " + ", ".join(missing))
    output = output_dir / "SBOM-and-license.zip"
    with zipfile.ZipFile(output, "w", compression=zipfile.ZIP_DEFLATED) as archive:
        for path, archive_name in zip(inputs, CONTRACT_FILENAMES):
            # Fixed metadata makes the archive byte-identical across fast and
            # deep modes when their canonical inputs are identical.
            info = zipfile.ZipInfo(archive_name, date_time=(1980, 1, 1, 0, 0, 0))
            info.compress_type = zipfile.ZIP_DEFLATED
            info.external_attr = 0o100644 << 16
            archive.writestr(info, path.read_bytes())
    return output


def main():
    if len(sys.argv) != 3:
        print("Usage: bundle-sbom.py OUTPUT_DIR THIRD_PARTY_NOTICE", file=sys.stderr)
        sys.exit(2)
    output = bundle(sys.argv[1], sys.argv[2])
    print(f"  Wrote {output} ({len(CONTRACT_FILENAMES)} files)")


if __name__ == "__main__":
    main()
