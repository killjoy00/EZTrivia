#!/usr/bin/env python3
import os
import struct
from pathlib import Path

import requests

PACKAGE = "com.rsm.eztrivia"
LANGUAGE = "en-US"
IMAGE_TYPE = "phoneScreenshots"
BASE = "https://androidpublisher.googleapis.com/androidpublisher/v3"
UPLOAD_BASE = "https://androidpublisher.googleapis.com/upload/androidpublisher/v3"
EXPECTED = [
    "01-play.png",
    "02-question-explanation.png",
    "03-daily.png",
    "04-friend-challenge.png",
    "05-scores.png",
]


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


def main() -> None:
    token = os.environ.get("GOOGLE_PLAY_ACCESS_TOKEN", "").strip()
    if not token:
        raise SystemExit("GOOGLE_PLAY_ACCESS_TOKEN is not available")

    directory = Path("play-store-screenshots")
    screenshots = [directory / name for name in EXPECTED]
    missing = [path.name for path in screenshots if not path.is_file()]
    if missing:
        raise SystemExit(f"Missing expected screenshots: {', '.join(missing)}")

    for path in screenshots:
        width, height = png_size(path)
        if width < 1080 or height < 1920 or width >= height:
            raise SystemExit(
                f"{path.name} has unexpected dimensions {width}x{height}; "
                "expected a portrait Play screenshot at least 1080x1920"
            )
        print(f"Validated {path.name}: {width}x{height}")

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

        require(
            session.delete(
                f"{app_base}/edits/{edit_id}/listings/{LANGUAGE}/{IMAGE_TYPE}",
                timeout=60,
            ),
            "Could not clear existing phone screenshots",
            allowed=(200, 404),
        )

        uploaded_ids = []
        for path in screenshots:
            response = require(
                session.post(
                    f"{UPLOAD_BASE}/applications/{PACKAGE}/edits/{edit_id}/listings/{LANGUAGE}/{IMAGE_TYPE}",
                    params={"uploadType": "media"},
                    headers={"Content-Type": "image/png"},
                    data=path.read_bytes(),
                    timeout=120,
                ),
                f"Could not upload {path.name}",
            )
            image = response.json().get("image", {})
            image_id = image.get("id")
            if not image_id:
                raise SystemExit(f"Play did not return an image ID for {path.name}")
            uploaded_ids.append(image_id)
            print(f"Uploaded {path.name}: {image_id}")

        require(
            session.post(
                f"{app_base}/edits/{edit_id}:commit",
                json={},
                timeout=60,
            ),
            "Could not commit screenshot edit",
        )
        committed = True
        edit_id = None
        print(f"Committed {len(uploaded_ids)} phone screenshots to the {LANGUAGE} Play listing.")

        verify_edit = require(
            session.post(f"{app_base}/edits", json={}, timeout=60),
            "Could not create verification edit",
        ).json()["id"]
        try:
            listed = require(
                session.get(
                    f"{app_base}/edits/{verify_edit}/listings/{LANGUAGE}/{IMAGE_TYPE}",
                    timeout=60,
                ),
                "Could not verify committed phone screenshots",
            ).json().get("images", [])
            listed_ids = {image.get("id") for image in listed}
            missing_ids = [image_id for image_id in uploaded_ids if image_id not in listed_ids]
            if missing_ids:
                raise SystemExit(
                    "Play commit completed, but verification could not find uploaded image IDs: "
                    + ", ".join(missing_ids)
                )
            print(f"Verified {len(uploaded_ids)} uploaded screenshots are present in Play.")
        finally:
            session.delete(f"{app_base}/edits/{verify_edit}", timeout=60)
    finally:
        if edit_id is not None and not committed:
            session.delete(f"{app_base}/edits/{edit_id}", timeout=60)


if __name__ == "__main__":
    main()
