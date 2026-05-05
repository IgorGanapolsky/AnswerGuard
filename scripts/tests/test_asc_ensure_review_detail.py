import os
import unittest
from unittest.mock import patch

from scripts.asc_ensure_review_detail import (
    contact_attributes_from_env,
    ensure_review_detail,
)


class FakeClient:
    def __init__(self, detail=None):
        self.detail = detail
        self.calls = []

    def get_all(self, path, params=None):
        self.calls.append(("GET_ALL", path, params, None))
        if path == "/apps":
            return [{"id": "app1"}]
        if path == "/apps/app1/appStoreVersions":
            return [{"id": "ver1", "attributes": {"versionString": "1.2.6"}}]
        return []

    def get(self, path, params=None):
        self.calls.append(("GET", path, params, None))
        if path == "/appStoreVersions/ver1/appStoreReviewDetail":
            if self.detail is None:
                from scripts.asc_client import AscClientError

                raise AscClientError("missing")
            return {"data": self.detail}
        return {}

    def request(self, method, path, *, params=None, payload=None):
        self.calls.append((method, path, params, payload))
        if method == "POST":
            return {"data": {"id": "detail-created"}}
        if method == "PATCH":
            return {"data": {"id": "detail1"}}
        return {}


ATTRS = {
    "contactFirstName": "Igor",
    "contactLastName": "Ganapolsky",
    "contactEmail": "igor@example.com",
    "contactPhone": "+15551231234",
    "demoAccountRequired": False,
}


class AscEnsureReviewDetailTests(unittest.TestCase):
    def test_contact_attributes_from_env_requires_core_fields(self):
        env = {
            "APP_REVIEW_CONTACT_FIRST_NAME": "Igor",
            "APP_REVIEW_CONTACT_LAST_NAME": "Ganapolsky",
            "APP_REVIEW_CONTACT_EMAIL": "igor@example.com",
            "APP_REVIEW_CONTACT_PHONE": "+15551231234",
            "APP_REVIEW_NOTES": "No account required.",
        }
        with patch.dict(os.environ, env, clear=True):
            attrs = contact_attributes_from_env()

        self.assertEqual(attrs["contactFirstName"], "Igor")
        self.assertEqual(attrs["contactPhone"], "+15551231234")
        self.assertFalse(attrs["demoAccountRequired"])
        self.assertEqual(attrs["notes"], "No account required.")

    def test_creates_missing_review_detail(self):
        client = FakeClient(detail=None)

        result = ensure_review_detail(
            client,
            bundle_id="com.example.app",
            version="1.2.6",
            attributes=ATTRS,
        )

        self.assertEqual(result["status"], "created")
        post_call = [call for call in client.calls if call[0] == "POST"][0]
        self.assertEqual(post_call[1], "/appStoreReviewDetails")
        self.assertEqual(
            post_call[3]["data"]["relationships"]["appStoreVersion"]["data"]["id"],
            "ver1",
        )

    def test_patches_existing_review_detail_when_fields_change(self):
        client = FakeClient(
            detail={
                "id": "detail1",
                "attributes": {**ATTRS, "contactPhone": "+10000000000"},
            }
        )

        result = ensure_review_detail(
            client,
            bundle_id="com.example.app",
            version="1.2.6",
            attributes=ATTRS,
        )

        self.assertEqual(result["status"], "updated")
        patch_call = [call for call in client.calls if call[0] == "PATCH"][0]
        self.assertEqual(patch_call[1], "/appStoreReviewDetails/detail1")
        self.assertEqual(patch_call[3]["data"]["attributes"], {"contactPhone": "+15551231234"})

    def test_leaves_matching_review_detail_unchanged(self):
        client = FakeClient(detail={"id": "detail1", "attributes": ATTRS})

        result = ensure_review_detail(
            client,
            bundle_id="com.example.app",
            version="1.2.6",
            attributes=ATTRS,
        )

        self.assertEqual(result["status"], "unchanged")
        self.assertFalse([call for call in client.calls if call[0] in {"POST", "PATCH"}])


if __name__ == "__main__":
    unittest.main()
