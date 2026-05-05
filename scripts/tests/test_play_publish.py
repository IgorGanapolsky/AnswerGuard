import struct
import tempfile
import unittest
from pathlib import Path

from scripts.play_publish import (
    _build_app_details,
    _is_draft_app_status_error,
    _is_edit_expired,
    _is_failed_precondition,
    _release_payload,
    _validate_play_image_dimensions,
)


PNG_SIG = b"\x89PNG\r\n\x1a\n"


def _write_png_header(path: Path, width: int, height: int) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_bytes(
        PNG_SIG
        + b"\x00\x00\x00\r"
        + b"IHDR"
        + struct.pack(">II", width, height)
        + b"\x08\x06\x00\x00\x00"
    )


class PlayPublishTests(unittest.TestCase):
    def test_detects_failed_precondition_marker(self):
        self.assertTrue(
            _is_failed_precondition(
                "HttpError 400",
                '{"error":{"status":"FAILED_PRECONDITION","message":"Precondition check failed."}}',
                400,
            )
        )

    def test_detects_precondition_phrase_for_400(self):
        self.assertTrue(
            _is_failed_precondition(
                "Bad request",
                "Precondition check failed for production publishing.",
                400,
            )
        )

    def test_does_not_false_positive_without_precondition(self):
        self.assertFalse(
            _is_failed_precondition(
                "HttpError 403",
                '{"error":{"status":"PERMISSION_DENIED","message":"No permission"}}',
                403,
            )
        )

    def test_detects_draft_app_release_status_error(self):
        self.assertTrue(
            _is_draft_app_status_error(
                "HttpError 400",
                '{"error":{"message":"Only releases with status draft may be created on draft app."}}',
                400,
            )
        )

    def test_draft_app_release_status_error_requires_bad_request(self):
        self.assertFalse(
            _is_draft_app_status_error(
                "HttpError 403",
                "Only releases with status draft may be created on draft app.",
                403,
            )
        )

    def test_detects_expired_edit_error(self):
        self.assertTrue(
            _is_edit_expired(
                "HttpError 400",
                '{"error":{"message":"This edit has expired, please create a new Edit."}}',
                400,
            )
        )

    def test_build_app_details_includes_required_contact_email(self):
        details = _build_app_details(
            "en-US",
            "https://example.com/support",
            "support@example.com",
        )

        self.assertEqual(details["defaultLanguage"], "en-US")
        self.assertEqual(details["contactWebsite"], "https://example.com/support")
        self.assertEqual(details["contactEmail"], "support@example.com")

    def test_release_payload_in_progress_clamps_invalid_fraction(self):
        payload = _release_payload(
            version_code="123",
            release_status="inProgress",
            release_notes="",
            user_fraction_raw="1.0",
        )
        self.assertEqual(payload["userFraction"], 0.1)
        self.assertEqual(payload["status"], "inProgress")

    def test_release_payload_completed_skips_user_fraction(self):
        payload = _release_payload(
            version_code="123",
            release_status="completed",
            release_notes="notes",
            user_fraction_raw="0.5",
        )
        self.assertNotIn("userFraction", payload)
        self.assertEqual(payload["releaseNotes"][0]["text"], "notes")

    def test_validate_play_image_dimensions_accepts_required_sizes(self):
        with tempfile.TemporaryDirectory() as tmp:
            metadata_dir = Path(tmp)
            _write_png_header(metadata_dir / "images" / "icon.png", 512, 512)
            _write_png_header(
                metadata_dir / "images" / "featureGraphic" / "feature.png",
                1024,
                500,
            )

            self.assertEqual(_validate_play_image_dimensions(metadata_dir), [])

    def test_validate_play_image_dimensions_rejects_bad_icon_size(self):
        with tempfile.TemporaryDirectory() as tmp:
            metadata_dir = Path(tmp)
            _write_png_header(metadata_dir / "images" / "icon.png", 1024, 1024)

            errors = _validate_play_image_dimensions(metadata_dir)

        self.assertEqual(len(errors), 1)
        self.assertIn("expected 512x512, got 1024x1024", errors[0])


if __name__ == "__main__":
    unittest.main()
