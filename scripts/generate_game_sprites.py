#!/usr/bin/env python3

import base64
import os
from pathlib import Path

from openai import OpenAI


MODEL = "gpt-image-1"
SIZE = "1024x1024"
OUTPUT_DIR = Path("app/src/main/res/drawable")

SPRITES = [
    (
        "sprite_gen_player.png",
        "Cute stickman fighter character, game sprite style, transparent background.",
    ),
    (
        "sprite_gen_cat.png",
        "Cute round orange cat with big eyes, cartoon game sprite, transparent background.",
    ),
    (
        "sprite_gen_boss.png",
        "Evil wizard cat with purple hat, game boss sprite, transparent background.",
    ),
]


def generate_sprite(client: OpenAI, filename: str, prompt: str) -> None:
    response = client.images.generate(
        model=MODEL,
        prompt=prompt,
        size=SIZE,
        response_format="b64_json",
    )

    image_base64 = response.data[0].b64_json
    image_bytes = base64.b64decode(image_base64)

    output_path = OUTPUT_DIR / filename
    output_path.write_bytes(image_bytes)
    print(f"saved {output_path}")


def main() -> None:
    if not os.environ.get("OPENAI_API_KEY"):
        raise SystemExit("OPENAI_API_KEY is not set")

    OUTPUT_DIR.mkdir(parents=True, exist_ok=True)
    client = OpenAI()

    for filename, prompt in SPRITES:
        generate_sprite(client, filename, prompt)


if __name__ == "__main__":
    main()
