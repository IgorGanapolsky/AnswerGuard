import unittest

from scripts.tests.router_client import RouterClient


class AscSubmitForReviewVerifyAgeRatingTests(unittest.TestCase):
    def test_verify_age_rating_reads_age_rating_declaration_from_version(self):
        from scripts.asc_submit_for_review import verify_age_rating

        client = RouterClient(
            {
                ("GET", "/appStoreVersions/ver1/ageRatingDeclaration"): {
                    "data": {"id": "decl1", "type": "ageRatingDeclarations", "attributes": {}}
                }
            }
        )
        verify_age_rating(client, "app1", "ver1")

    def test_verify_age_rating_dies_when_missing(self):
        from scripts.asc_submit_for_review import verify_age_rating

        client = RouterClient(
            {
                ("GET", "/appStoreVersions/ver1/ageRatingDeclaration"): RuntimeError("404"),
            }
        )
        with self.assertRaises(SystemExit):
            verify_age_rating(client, "app1", "ver1")

    def test_verify_age_rating_skips_when_apple_relationship_endpoint_is_unavailable(self):
        from scripts.asc_submit_for_review import verify_age_rating

        client = RouterClient(
            {
                ("GET", "/appStoreVersions/ver1/ageRatingDeclaration"): RuntimeError(
                    "HTTP 404 {'errors': [{'code': 'PATH_ERROR', "
                    "'detail': \"The relationship 'ageRatingDeclaration' does not exist\"}]}"
                ),
            }
        )

        verify_age_rating(client, "app1", "ver1")


if __name__ == "__main__":
    unittest.main()
