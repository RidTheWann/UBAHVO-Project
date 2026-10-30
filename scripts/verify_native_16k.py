#!/usr/bin/env python3
"""Audit ELF PT_LOAD alignment in Android APK/AAB files, including nested ZIP-backed .so payloads.

Beta workflows use report-only mode while the known upstream FFmpeg payload is incompatible.
Use --strict as a production release gate once every packaged native dependency is rebuilt.
"""
from __future__ import annotations

import argparse
import io
import posixpath
import stat
import subprocess
import sys
import tempfile
import zipfile
from dataclasses import dataclass
from pathlib import Path
from typing import Iterable

PAGE_SIZE = 16 * 1024
ELF_MAGIC = b"\x7fELF"
MAX_NESTED_ARCHIVE_DEPTH = 4


@dataclass(frozen=True)
class ElfReport:
    artifact: str
    load_alignments: tuple[int, ...]

    @property
    def compliant(self) -> bool:
        return bool(self.load_alignments) and all(
            alignment >= PAGE_SIZE and alignment % PAGE_SIZE == 0
            for alignment in self.load_alignments
        )

    @property
    def invalid_alignments(self) -> tuple[int, ...]:
        return tuple(
            alignment for alignment in self.load_alignments
            if alignment < PAGE_SIZE or alignment % PAGE_SIZE != 0
        )


def parse_load_alignments(readelf_output: str) -> tuple[int, ...]:
    """Read the final Align column from llvm-readelf -lW PT_LOAD rows."""
    alignments: list[int] = []
    for line in readelf_output.splitlines():
        fields = line.split()
        if not fields or fields[0] != "LOAD":
            continue
        if len(fields) < 3:
            raise ValueError(f"Malformed PT_LOAD row: {line!r}")
        try:
            alignments.append(int(fields[-1], 0))
        except ValueError as error:
            raise ValueError(f"Invalid PT_LOAD alignment in row: {line!r}") from error
    if not alignments:
        raise ValueError("ELF has no readable PT_LOAD program headers")
    return tuple(alignments)


def _is_zip(payload: bytes) -> bool:
    return zipfile.is_zipfile(io.BytesIO(payload))


def _inspect_elf(payload: bytes, label: str, readelf: str) -> ElfReport:
    if not payload.startswith(ELF_MAGIC):
        raise ValueError(f"Native .so entry is neither ELF nor a recognized ZIP payload: {label}")

    with tempfile.NamedTemporaryFile(prefix="ubahvo-elf-", suffix=".so") as temporary:
        temporary.write(payload)
        temporary.flush()
        result = subprocess.run(
            [readelf, "-lW", temporary.name],
            check=False,
            capture_output=True,
            text=True,
        )
    if result.returncode != 0:
        detail = (result.stderr or result.stdout).strip()
        raise RuntimeError(f"llvm-readelf failed for {label}: {detail}")
    return ElfReport(label, parse_load_alignments(result.stdout))


def _inspect_archive_entry(
    archive: zipfile.ZipFile,
    entry_name: str,
    label: str,
    readelf: str,
    visited: frozenset[str] = frozenset(),
) -> list[ElfReport]:
    """Inspect one .so entry, resolving ZIP-stored Unix symlinks safely."""
    if entry_name in visited:
        raise ValueError(f"Symlink cycle in native archive: {label}")

    try:
        entry = archive.getinfo(entry_name)
    except KeyError as error:
        raise ValueError(f"Native archive entry is missing: {label}") from error

    mode = (entry.external_attr >> 16) & 0xFFFF
    if stat.S_ISLNK(mode):
        try:
            target = archive.read(entry).decode("utf-8").strip()
        except UnicodeDecodeError as error:
            raise ValueError(f"Native archive symlink target is not UTF-8: {label}") from error
        if not target or posixpath.isabs(target) or "\\" in target:
            raise ValueError(f"Unsafe native archive symlink target: {label}")
        resolved = posixpath.normpath(posixpath.join(posixpath.dirname(entry_name), target))
        if resolved in ("", ".", "..") or resolved.startswith("../") or posixpath.isabs(resolved):
            raise ValueError(f"Native archive symlink escapes its root: {label}")
        next_label = f"{label} -> {resolved}"
        return _inspect_archive_entry(
            archive, resolved, next_label, readelf, visited | {entry_name}
        )

    payload = archive.read(entry)
    if _is_zip(payload):
        return _inspect_nested_zip(payload, label, readelf)
    return [_inspect_elf(payload, label, readelf)]


def _inspect_nested_zip(
    payload: bytes,
    label: str,
    readelf: str,
    depth: int = 0,
) -> list[ElfReport]:
    if depth > MAX_NESTED_ARCHIVE_DEPTH:
        raise ValueError(f"Nested native archive depth exceeded at {label}")

    reports: list[ElfReport] = []
    try:
        with zipfile.ZipFile(io.BytesIO(payload)) as archive:
            for entry in archive.infolist():
                if entry.is_dir() or not entry.filename.lower().endswith(".so"):
                    continue
                child_label = f"{label}!{entry.filename}"
                reports.extend(
                    _inspect_archive_entry(archive, entry.filename, child_label, readelf)
                )
    except zipfile.BadZipFile as error:
        raise ValueError(f"Invalid nested native ZIP archive: {label}") from error
    return reports


def inspect_artifact(path: Path, readelf: str) -> list[ElfReport]:
    """Scan .so entries in an APK/AAB and recurse into ZIP-backed native payloads."""
    if not path.is_file() or path.stat().st_size == 0:
        raise ValueError(f"Artifact is missing or empty: {path}")

    reports: list[ElfReport] = []
    try:
        with zipfile.ZipFile(path) as artifact:
            for entry in artifact.infolist():
                if entry.is_dir() or not entry.filename.lower().endswith(".so"):
                    continue
                label = f"{path.name}!{entry.filename}"
                reports.extend(
                    _inspect_archive_entry(artifact, entry.filename, label, readelf)
                )
    except zipfile.BadZipFile as error:
        raise ValueError(f"Android artifact is not a valid ZIP archive: {path}") from error

    if not reports:
        raise ValueError(f"No ELF shared objects were found in {path}")
    return reports


def main(argv: Iterable[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--readelf", required=True, help="Path to NDK llvm-readelf")
    mode = parser.add_mutually_exclusive_group()
    mode.add_argument(
        "--strict",
        action="store_true",
        help="Fail when any PT_LOAD segment is not aligned to at least 16 KB",
    )
    mode.add_argument(
        "--report-only",
        action="store_true",
        help="Report incompatibilities without failing (for current beta dependencies)",
    )
    parser.add_argument("artifacts", nargs="+", type=Path, help="APK/AAB artifacts to inspect")
    args = parser.parse_args(argv)

    all_reports: list[ElfReport] = []
    try:
        for artifact in args.artifacts:
            reports = inspect_artifact(artifact, args.readelf)
            all_reports.extend(reports)
            incompatible = [report for report in reports if not report.compliant]
            print(
                f"{artifact}: inspected {len(reports)} ELF shared objects; "
                f"16 KB compliant {len(reports) - len(incompatible)}; "
                f"incompatible {len(incompatible)}"
            )
            for report in incompatible:
                values = ", ".join(f"0x{value:x}" for value in report.invalid_alignments)
                print(f"  INCOMPATIBLE {report.artifact}: PT_LOAD p_align={values}")
    except (OSError, RuntimeError, ValueError) as error:
        print(f"16 KB native audit error: {error}", file=sys.stderr)
        return 2

    incompatible = [report for report in all_reports if not report.compliant]
    print(
        f"16 KB ELF audit: {len(all_reports)} ELF object(s), "
        f"{len(incompatible)} incompatible object(s)."
    )
    if incompatible:
        print(
            "Do not claim 16 KB compatibility. Rebuild the affected dependency from source; "
            "changing only an application's JNI bridge cannot repair nested ELF binaries."
        )
        if args.strict:
            return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
