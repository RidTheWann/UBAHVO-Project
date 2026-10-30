import io
import stat
import tempfile
import unittest
import zipfile
from pathlib import Path
from subprocess import CompletedProcess
from unittest.mock import patch

from verify_native_16k import inspect_artifact, parse_load_alignments


class Native16kAuditTests(unittest.TestCase):
    def test_parses_all_load_segment_alignments(self):
        output = """
Program Headers:
  Type Offset VirtAddr PhysAddr FileSiz MemSiz Flg Align
  LOAD 0x000000 0x000000 0x000000 0x001000 0x001000 R E 0x4000
  LOAD 0x002000 0x002000 0x002000 0x001000 0x001000 RW 0x8000
"""
        self.assertEqual(parse_load_alignments(output), (0x4000, 0x8000))

    def test_rejects_malformed_or_missing_load_segments(self):
        with self.assertRaises(ValueError):
            parse_load_alignments("There are no program headers")
        with self.assertRaises(ValueError):
            parse_load_alignments("LOAD 0x000000 not-an-alignment")

    def test_scans_nested_ffmpeg_zip_payloads(self):
        fake_elf = b"\x7fELF" + (b"\x00" * 60)
        nested = io.BytesIO()
        with zipfile.ZipFile(nested, "w", zipfile.ZIP_DEFLATED) as archive:
            archive.writestr("usr/lib/libwebp.so", fake_elf)
            archive.writestr("usr/lib/libavcodec.so", fake_elf)

        with tempfile.TemporaryDirectory() as directory:
            artifact_path = Path(directory) / "test.apk"
            with zipfile.ZipFile(artifact_path, "w", zipfile.ZIP_DEFLATED) as artifact:
                artifact.writestr("lib/arm64-v8a/libubahvo_native.so", fake_elf)
                artifact.writestr("lib/arm64-v8a/libffmpeg.zip.so", nested.getvalue())

            aligned = CompletedProcess(
                args=[], returncode=0,
                stdout="LOAD 0x000000 0x000000 0x000000 0x001000 0x001000 R E 0x4000\n",
                stderr="",
            )
            with patch("verify_native_16k.subprocess.run", return_value=aligned) as run:
                reports = inspect_artifact(artifact_path, "llvm-readelf")

            self.assertEqual(len(reports), 3)
            self.assertTrue(all(report.compliant for report in reports))
            self.assertTrue(any("libffmpeg.zip.so!usr/lib/libwebp.so" in report.artifact for report in reports))
            self.assertEqual(run.call_count, 3)

    def test_nested_misaligned_library_is_reported(self):
        fake_elf = b"\x7fELF" + (b"\x00" * 60)
        nested = io.BytesIO()
        with zipfile.ZipFile(nested, "w", zipfile.ZIP_DEFLATED) as archive:
            archive.writestr("usr/lib/libwebp.so", fake_elf)

        with tempfile.TemporaryDirectory() as directory:
            artifact_path = Path(directory) / "test.apk"
            with zipfile.ZipFile(artifact_path, "w", zipfile.ZIP_DEFLATED) as artifact:
                artifact.writestr("lib/arm64-v8a/libffmpeg.zip.so", nested.getvalue())

            misaligned = CompletedProcess(
                args=[], returncode=0,
                stdout="LOAD 0x000000 0x000000 0x000000 0x001000 0x001000 R E 0x1000\n",
                stderr="",
            )
            with patch("verify_native_16k.subprocess.run", return_value=misaligned):
                reports = inspect_artifact(artifact_path, "llvm-readelf")

            self.assertEqual(len(reports), 1)
            self.assertFalse(reports[0].compliant)
            self.assertEqual(reports[0].invalid_alignments, (0x1000,))


    def test_resolves_nested_unix_symlink_to_elf_target(self):
        fake_elf = b"\\x7fELF" + (b"\\x00" * 60)
        nested = io.BytesIO()
        with zipfile.ZipFile(nested, "w", zipfile.ZIP_DEFLATED) as archive:
            archive.writestr("usr/lib/libswscale.so.8.1.100", fake_elf)
            link = zipfile.ZipInfo("usr/lib/libswscale.so")
            link.create_system = 3
            link.external_attr = (stat.S_IFLNK | 0o777) << 16
            archive.writestr(link, "libswscale.so.8.1.100")

        with tempfile.TemporaryDirectory() as directory:
            artifact_path = Path(directory) / "test.apk"
            with zipfile.ZipFile(artifact_path, "w", zipfile.ZIP_DEFLATED) as artifact:
                artifact.writestr("lib/arm64-v8a/libffmpeg.zip.so", nested.getvalue())

            aligned = CompletedProcess(
                args=[], returncode=0,
                stdout="LOAD 0x000000 0x000000 0x000000 0x001000 0x001000 R E 0x4000\\n",
                stderr="",
            )
            with patch("verify_native_16k.subprocess.run", return_value=aligned) as run:
                reports = inspect_artifact(artifact_path, "llvm-readelf")

            self.assertEqual(len(reports), 1)
            self.assertTrue(reports[0].compliant)
            self.assertIn("libswscale.so -> usr/lib/libswscale.so.8.1.100", reports[0].artifact)
            self.assertEqual(run.call_count, 1)

    def test_rejects_symlink_escape_from_nested_archive(self):
        nested = io.BytesIO()
        with zipfile.ZipFile(nested, "w", zipfile.ZIP_DEFLATED) as archive:
            link = zipfile.ZipInfo("usr/lib/libunsafe.so")
            link.create_system = 3
            link.external_attr = (stat.S_IFLNK | 0o777) << 16
            archive.writestr(link, "../../outside.so")

        with tempfile.TemporaryDirectory() as directory:
            artifact_path = Path(directory) / "test.apk"
            with zipfile.ZipFile(artifact_path, "w", zipfile.ZIP_DEFLATED) as artifact:
                artifact.writestr("lib/arm64-v8a/libffmpeg.zip.so", nested.getvalue())

            with self.assertRaisesRegex(ValueError, "escapes its root"):
                inspect_artifact(artifact_path, "llvm-readelf")


if __name__ == "__main__":
    unittest.main()
