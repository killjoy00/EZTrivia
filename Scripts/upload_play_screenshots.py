#!/usr/bin/env python3
import os
import struct
from pathlib import Path

import requests

PACKAGE = "com.rsm.eztrivia"
LANGUAGE = "en-US"
SCREENSHOT_TYPE = "phoneScreenshots"
FEATURE_GRAPHIC_TYPE = "featureGraphic"
BASE = "https://androidpublisher.googleapis.com/androidpublisher/v3"
UPLOAD_BASE = "https://androidpublisher.googleapis.com/upload/androidpublisher/v3"
EXPECTED_SCREENSHOTS = [
    "01-play.png",
    "02-question-explanation.png",
    "03-daily.png",
    "04-friend-challenge.png",
    "05-scores.png",
]
FEATURE_GRAPHIC = Path("play-store-assets/feature-graphic.png")


def png_size(path: Path) -> tuple[int, int]:
    with path.open("rb") as stream:
        header = stream.read(24)
    if len(header) < 24 or header[:8] != b"\x89PNG\r\n\x1a\n":
        raise SystemExit(f"{path} is not a valid PNG")
    return struct.unpack(">II", header[16:24])


def require(response: requests.Response, context: str, allowed=(200,)) -> requests.Response:
    if response.status_code not in allowed:
        print(f"::error::{context}: HTTP {response.status_code}: {response.text[:4000]}")
        raise SystemExit(1)
    return response


def upload_image(
    session: requests.Session,
    edit_id: str,
    image_type: str,
    path: Path,
) -> str:
    response = require(
        session.post(
            f"{UPLOAD_BASE}/applications/{PACKAGE}/edits/{edit_id}/listings/{LANGUAGE}/{image_type}",
            params={"uploadType": "media"},
            headers={"Content-Type": "image/png"},
            data=path.read_bytes(),
            timeout=120,
        ),
        f"Could not upload {path.name} as {image_type}",
    )
    image = response.json().get("image", {})
    image_id = image.get("id")
    if not image_id:
        raise SystemExit(f"Play did not return an image ID for {path.name}")
    print(f"Uploaded {path.name} as {image_type}: {image_id}")
    return image_id


def verify_images(
    session: requests.Session,
    app_base: str,
    edit_id: str,
    image_type: str,
    expected_ids: list[str],
) -> None:
    listed = require(
        session.get(
            f"{app_base}/edits/{edit_id}/listings/{LANGUAGE}/{image_type}",
            timeout=60,
        ),
        f"Could not verify committed {image_type}",
    ).json().get("images", [])
    listed_ids = {image.get("id") for image in listed}
    missing_ids = [image_id for image_id in expected_ids if image_id not in listed_ids]
    if missing_ids:
        raise SystemExit(
            f"Play commit completed, but verification could not find {image_type} image IDs: "
            + ", ".join(missing_ids)
        )
    print(f"Verified {len(expected_ids)} {image_type} image(s) are present in Play.")


def main() -> None:
    token = os.environ.get("GOOGLE_PLAY_ACCESS_TOKEN", "").strip()
    if not token:
        raise SystemExit("GOOGLE_PLAY_ACCESS_TOKEN is not available")

    screenshot_directory = Path("play-store-screenshots")
    screenshots = [screenshot_directory / name for name in EXPECTED_SCREENSHOTS]
    missing = [path.name for path in screenshots if not path.is_file()]
    if missing:
        raise SystemExit(f"Missing expected screenshots: {', '.join(missing)}")
    if not FEATURE_GRAPHIC.is_file():
        raise SystemExit(f"Missing feature graphic: {FEATURE_GRAPHIC}")

    for path in screenshots:
        width, height = png_size(path)
        if width < 1080 or height < 1920 or width >= height:
            raise SystemExit(
                f"{path.name} has unexpected dimensions {width}x{height}; "
                "expected a portrait Play screenshot at least 1080x1920"
            )
        print(f"Validated {path.name}: {width}x{height}")

    feature_width, feature_height = png_size(FEATURE_GRAPHIC)
    if (feature_width, feature_height) != (1024, 500):
        raise SystemExit(
            f"{FEATURE_GRAPHIC.name} has unexpected dimensions {feature_width}x{feature_height}; "
            "expected exactly 1024x500"
        )
    print(f"Validated {FEATURE_GRAPHIC.name}: {feature_width}x{feature_height}")

    session = requests.Session()
    session.headers.update({"Authorization": f"Bearer {token}"})
    app_base = f"{BASE}/applications/{PACKAGE}"
    edit_id = None
    committed = False

    try:
        edit = require(
            session.post(f"{app_base}/edits", json={}, timeout=60),
            "Could not create Play edit",
        ).json()
        edit_id = edit["id"]

        for image_type, label in (
            (SCREENSHOT_TYPE, "phone screenshots"),
            (FEATURE_GRAPHIC_TYPE, "feature graphic"),
        ):
            require(
                session.delete(
                    f"{app_base}/edits/{edit_id}/listings/{LANGUAGE}/{image_type}",
                    timeout=60,
                ),
                f"Could not clear existing {label}",
                allowed=(200, 404),
            )

        screenshot_ids = [
            upload_image(session, edit_id, SCREENSHOT_TYPE, path)
            for path in screenshots
        ]
        feature_graphic_id = upload_image(
            session,
            edit_id,
            FEATURE_GRAPHIC_TYPE,
            FEATURE_GRAPHIC,
        )

        require(
            session.post(
                f"{app_base}/edits/{edit_id}:commit",
                params={"changesInReviewBehavior": "ERROR_IF_IN_REVIEW"},
                json={},
                timeout=60,
            ),
            "Could not commit Play listing image edit without disturbing an existing review",
        )
        committed = True
        edit_id = None
        print(
            f"Committed {len(screenshot_ids)} phone screenshots and 1 feature graphic "
            f"to the {LANGUAGE} Play listing."
        )

        verify_edit = require(
            session.post(f"{app_base}/edits", json={}, timeout=60),
            "Could not create verification edit",
        ).json()["id"]
        try:
            verify_images(
                session,
                app_base,
                verify_edit,
                SCREENSHOT_TYPE,
                screenshot_ids,
            )
            verify_images(
                session,
                app_base,
                verify_edit,
                FEATURE_GRAPHIC_TYPE,
                [feature_graphic_id],
            )
        finally:
            session.delete(f"{app_base}/edits/{verify_edit}", timeout=60)
    finally:
        if edit_id is not None and not committed:
            session.delete(f"{app_base}/edits/{edit_id}", timeout=60)


if __name__ == "__main__":
    main()
