"""Slate material must stay dark and survive APK packaging."""
from pathlib import Path
from io import BytesIO
import zipfile
from PIL import Image, ImageStat

ROOT = Path(__file__).resolve().parents[1]
ASSETS = ROOT / "app/assets/experience-board"


def test_slate_is_dark_textured_material():
    with Image.open(ASSETS / "slate-texture.png") as image:
        luminance = image.convert("L")
        stats = ImageStat.Stat(luminance)
        assert 8 < stats.mean[0] < 50
        assert stats.stddev[0] > 1


def test_apk_contains_original_chalk_textures():
    with zipfile.ZipFile(ROOT / "FreeMyTV.apk") as apk:
        for name in ("slate-texture.png",):
            data = apk.read("assets/experience-board/" + name)
            assert data == (ASSETS / name).read_bytes()
            with Image.open(BytesIO(data)) as image:
                image.verify()
