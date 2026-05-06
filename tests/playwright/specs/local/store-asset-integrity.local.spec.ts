import path from "node:path";
import { fileURLToPath } from "node:url";
import { expect, test } from "@playwright/test";
import {
  countUniqueFileHashes,
  listPngFiles,
  readPngSize,
} from "../../src/storeVerification";

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);
const repoRoot = path.resolve(__dirname, "../../../..");

test.describe("Local Store Asset Integrity", () => {
  test("Google Play icon and feature graphic use required dimensions", async () => {
    const icon = path.join(
      repoRoot,
      "native-android/fastlane/metadata/android/en-US/images/icon.png",
    );
    const featureGraphic = path.join(
      repoRoot,
      "native-android/fastlane/metadata/android/en-US/images/featureGraphic/feature.png",
    );

    expect(readPngSize(icon)).toEqual({ width: 512, height: 512 });
    expect(readPngSize(featureGraphic)).toEqual({ width: 1024, height: 500 });
  });

  test("Android and iOS screenshots are not duplicate placeholders", async () => {
    const androidShots = listPngFiles(
      path.join(
        repoRoot,
        "native-android/fastlane/metadata/android/en-US/images/phoneScreenshots",
      ),
    );
    const iosShots = listPngFiles(
      path.join(repoRoot, "native-ios/fastlane/screenshots/en-US"),
    );

    expect(countUniqueFileHashes(androidShots)).toBeGreaterThanOrEqual(3);
    expect(countUniqueFileHashes(iosShots)).toBeGreaterThanOrEqual(5);
  });
});
