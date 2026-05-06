from __future__ import annotations

import os
import struct
import subprocess
import sys
from pathlib import Path

import pytest

from scripts import normalize_pem, play_artifacts, png_dimensions, repo_dotenv, source_versions


PNG_SIG = b"\x89PNG\r\n\x1a\n"


def _write_png(path: Path, width: int, height: int) -> None:
    path.write_bytes(
        PNG_SIG
        + b"\x00\x00\x00\r"
        + b"IHDR"
        + struct.pack(">II", width, height)
        + b"\x08\x06\x00\x00\x00"
    )


def test_png_dimensions_reads_size_and_rejects_non_png(tmp_path: Path):
    image = tmp_path / "image.png"
    _write_png(image, 320, 640)

    assert png_dimensions.png_dimensions(image) == (320, 640)

    bad = tmp_path / "bad.png"
    bad.write_text("not a png", encoding="utf-8")
    with pytest.raises(ValueError, match="not a PNG"):
        png_dimensions.png_dimensions(bad)


def test_png_dimensions_cli_success_and_usage(tmp_path: Path):
    image = tmp_path / "image.png"
    _write_png(image, 11, 22)

    ok = subprocess.run(
        [sys.executable, "scripts/png_dimensions.py", str(image)],
        cwd=Path(__file__).resolve().parents[2],
        text=True,
        capture_output=True,
        check=False,
    )
    assert ok.returncode == 0
    assert ok.stdout.strip() == "11x22"

    usage = subprocess.run(
        [sys.executable, "scripts/png_dimensions.py"],
        cwd=Path(__file__).resolve().parents[2],
        text=True,
        capture_output=True,
        check=False,
    )
    assert usage.returncode == 2
    assert "usage:" in usage.stderr


def test_normalize_pem_accepts_secret_format_and_rejects_bad_files(tmp_path: Path, monkeypatch):
    key = tmp_path / "AuthKey.p8"
    pem_type = f"{'PRIVATE'} {'KEY'}"
    begin = f"-----BEGIN {pem_type}-----"
    end = f"-----END {pem_type}-----"
    key.write_text(f"\ufeff  {begin}\\nabc\\n{end}\r\n", encoding="utf-8")

    monkeypatch.setattr(sys, "argv", ["normalize_pem.py", str(key)])
    assert normalize_pem.main() == 0
    assert key.read_text(encoding="utf-8") == f"{begin}\nabc\n{end}"

    missing = tmp_path / "missing.p8"
    monkeypatch.setattr(sys, "argv", ["normalize_pem.py", str(missing)])
    assert normalize_pem.main() == 1

    invalid = tmp_path / "invalid.p8"
    invalid.write_text("not a key", encoding="utf-8")
    monkeypatch.setattr(sys, "argv", ["normalize_pem.py", str(invalid)])
    assert normalize_pem.main() == 1

    monkeypatch.setattr(sys, "argv", ["normalize_pem.py"])
    assert normalize_pem.main() == 2


def test_repo_dotenv_loads_multiline_values_without_clobbering_existing_env(
    tmp_path: Path,
    monkeypatch,
):
    (tmp_path / ".env").write_text(
        "\n".join(
            [
                "EMPTY_EXPORT=from_file",
                "EXISTING=from_file",
                "PLAIN='value'",
                'MULTI="line1',
                "line2\"",
                "# ignored",
                "MALFORMED",
            ]
        ),
        encoding="utf-8",
    )
    monkeypatch.setenv("EXISTING", "from_env")
    monkeypatch.setenv("EMPTY_EXPORT", "")
    monkeypatch.delenv("PLAIN", raising=False)
    monkeypatch.delenv("MULTI", raising=False)

    repo_dotenv.load_repo_dotenv(tmp_path)

    assert os.environ["EMPTY_EXPORT"] == "from_file"
    assert os.environ["EXISTING"] == "from_env"
    assert os.environ["PLAIN"] == "value"
    assert os.environ["MULTI"] == "line1\nline2"


def test_source_versions_extractors_and_cli(tmp_path: Path, monkeypatch, capsys):
    assert source_versions.extract_android_version_name('versionName = "1.2.3"') == "1.2.3"
    assert source_versions.extract_android_version_code("versionCode = ciVersionCode ?: 42") == 42
    assert source_versions.extract_ios_version_name("MARKETING_VERSION = 1.2.3;") == "1.2.3"
    assert source_versions.extract_ios_build_number("CURRENT_PROJECT_VERSION = 77;") == 77

    repo = tmp_path
    android_file = repo / "native-android" / "app" / "build.gradle.kts"
    ios_file = repo / "native-ios" / "AnswerGuard.xcodeproj" / "project.pbxproj"
    android_file.parent.mkdir(parents=True)
    ios_file.parent.mkdir(parents=True)
    android_file.write_text('versionCode = 123\nversionName = "1.2.3"\n', encoding="utf-8")
    ios_file.write_text(
        "MARKETING_VERSION = 1.2.3;\nCURRENT_PROJECT_VERSION = 456;\n",
        encoding="utf-8",
    )

    payload = source_versions.read_source_versions(repo)
    assert payload["android"]["version_code"] == 123
    assert payload["ios"]["build_number"] == 456

    monkeypatch.setattr(
        sys,
        "argv",
        ["source_versions.py", "--repo-root", str(repo), "--format", "value", "--key", "ANDROID_VERSION_NAME"],
    )
    assert source_versions.main() == 0
    assert capsys.readouterr().out.strip() == "1.2.3"

    monkeypatch.setattr(
        sys,
        "argv",
        ["source_versions.py", "--repo-root", str(repo), "--format", "value", "--key", "NOPE"],
    )
    assert source_versions.main() == 2


def test_source_versions_errors_are_actionable(tmp_path: Path):
    with pytest.raises(source_versions.VersionParseError, match="Missing required file"):
        source_versions.read_source_versions(tmp_path)
    with pytest.raises(source_versions.VersionParseError, match="Could not parse versionName"):
        source_versions.extract_android_version_name("versionCode = 1")


def test_play_artifacts_places_screenshots_under_artifact_directory():
    result = Path(play_artifacts.screenshot_path("proof.png"))

    assert result.name == "proof.png"
    assert result.parent.name == "play_console"
    assert result.parent.is_dir()
