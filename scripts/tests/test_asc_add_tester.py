from __future__ import annotations

from scripts import asc_add_tester as add_tester


class FakeASCClient:
    def __init__(self):
        self.posts = []

    def get(self, path, params=None):
        assert path == "/apps"
        assert params["filter[bundleId]"] == "com.igorganapolsky.answerguard"
        return {"data": [{"id": "app-1"}]}

    def get_all(self, path, params=None):
        if path == "/apps/app-1/betaGroups":
            return [{"id": "group-1", "attributes": {"name": "Internal Testers"}}]
        if path == "/betaTesters":
            return [{"id": "tester-1", "attributes": {"email": "iganapolsky@gmail.com"}}]
        if path == "/builds":
            return [{"id": "build-1", "attributes": {"version": "42", "uploadedDate": "2026-05-05T00:00:00Z"}}]
        raise AssertionError(path)

    def request(self, method, path, payload=None):
        self.posts.append((method, path, payload))
        return {"data": {"id": "created-id"}}


def test_find_app_id_uses_answerguard_bundle_id():
    assert add_tester.find_app_id(FakeASCClient(), "com.igorganapolsky.answerguard") == "app-1"


def test_existing_tester_is_attached_to_group():
    client = FakeASCClient()
    tester_id = add_tester.find_or_create_tester(
        client,
        email="iganapolsky@gmail.com",
        first_name="Igor",
        last_name="Ganapolsky",
        group_id="group-1",
    )
    assert tester_id == "tester-1"
    assert client.posts == [
        (
            "POST",
            "/betaGroups/group-1/relationships/betaTesters",
            {"data": [{"type": "betaTesters", "id": "tester-1"}]},
        )
    ]


def test_latest_build_is_attached_to_group():
    client = FakeASCClient()
    build = add_tester.distribute_latest_build(client, app_id="app-1", group_id="group-1")
    assert build == "42"
    assert client.posts == [
        (
            "POST",
            "/betaGroups/group-1/relationships/builds",
            {"data": [{"type": "builds", "id": "build-1"}]},
        )
    ]
