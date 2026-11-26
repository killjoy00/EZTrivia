#!/usr/bin/env python3
"""Produce a Play native-symbol ZIP from exactly matching Android bundle libraries.

Never invent symbols: select only ELF files that have .symtab (or DWARF)
and whose GNU Build ID matches the corresponding bundled .so. Prefer
unstripped native libraries from Gradle intermediates; otherwise the AAB.
"""
import argparse
import json
import re
import subprocess
import sys
import tempfile
import zipfile
from pathlib import Path

BUILD_ID = re.compile(r"Build ID:\s*([0-9a-fA-F]+)")
SYMBOL = re.compile(r"\s\.(?:symtab|debug_info)\s")


def inspect_elf(path):
    try:
        sections = subprocess.run(
            ["readelf", "-W", "-S", str(path)],
            capture_output=True, text=True, check=True,
        ).stdout
        notes = subprocess.run(
            ["readelf", "-n", str(path)],
            capture_output=True, text=True, check=True,
        ).stdout
    except (FileNotFoundError, subprocess.CalledProcessError):
        return {"build_id": None, "has_symbols": False, "has_debug_info": False}
    match = BUILD_ID.search(notes)
    return {
        "build_id": match.group(1).lower() if match else None,
        "has_symbols": bool(SYMBOL.search(sections)),
        "has_debug_info": ".debug_info" in sections,
    }


def prepare(aab_path, output_zip, report_path, intermediates=None):
    available = []
    unavailable = []
    candidates = {}
    with tempfile.TemporaryDirectory() as scratch:
        tmpdir = Path(scratch)
        if intermediates and Path(intermediates).is_dir():
            for path in Path(intermediates).rglob("*.so"):
                info = inspect_elf(path)
                if info["build_id"] and info["has_symbols"]:
                    candidates.setdefault((path.name, info["build_id"]), []).append((path, info))
        with zipfile.ZipFile(aab_path) as aab, zipfile.ZipFile(
            output_zip, "w", zipfile.ZIP_DEFLATED
        ) as symbols:
            names = [
                n for n in aab.namelist()
                if re.fullmatch(r"base/lib/[^/]+/[^/]+\.so", n)
            ]
            for idx, name in enumerate(sorted(names)):
                abi, filename = name.split("/")[2:4]
                bundled = tmpdir / f"{idx}-{filename}"
                bundled.write_bytes(aab.read(name))
                info = inspect_elf(bundled)
                choices = candidates.get(
                    (filename, info["build_id"]), []
                ) if info["build_id"] else []
                # Avoid picking the same binary from a different architecture.
                choices = [x for x in choices if abi in str(x[0])]
                choices.sort(
                    key=lambda x: (x[1]["has_debug_info"], x[1]["has_symbols"]),
                    reverse=True,
                )
                if choices:
                    source, chosen_info = choices[0]
                elif info["has_symbols"]:
                    source, chosen_info = bundled, info
                else:
                    unavailable.append({
                        "library": f"{abi}/{filename}",
                        "build_id": info["build_id"],
                        "reason": "Already stripped; no matching symbol-bearing binary available",
                    })
                    continue
                symbols.write(source, arcname=f"{abi}/{filename}")
                available.append({
                    "library": f"{abi}/{filename}",
                    "build_id": info["build_id"],
                    "source": "gradle-intermediates" if source != bundled else "bundle",
                    "debug_info": chosen_info["has_debug_info"],
                })
    report = {
        "bundle": str(aab_path),
        "symbol_count": len(available),
        "unavailable_count": len(unavailable),
        "available": available,
        "unavailable": unavailable,
    }
    Path(report_path).write_text(json.dumps(report, indent=2) + "\n")
    print(
        f"Native symbol audit: {len(available)} / "
        f"{len(available) + len(unavailable)} libraries available"
    )
    for item in unavailable:
        print(f"  no symbols: {item['library']}")
    if not available:
        print(
            "No authentic native symbol files found; cannot claim symbolication",
            file=sys.stderr,
        )
        Path(output_zip).unlink(missing_ok=True)
        return 2
    return 0


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--aab", type=Path, required=True)
    parser.add_argument("--out", type=Path, required=True)
    parser.add_argument("--report", type=Path, required=True)
    parser.add_argument("--intermediates", type=Path)
    args = parser.parse_args()
    return prepare(args.aab, args.out, args.report, args.intermediates)


if __name__ == "__main__":
    raise SystemExit(main())
