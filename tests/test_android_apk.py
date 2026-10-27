"""Packaging regression: host JVM tests see the original Paho JAR, Android sees only the APK."""
from pathlib import Path
import zipfile

import pytest

REPO = Path(__file__).resolve().parents[1]
APK = REPO / "FreeMyTV.apk"
pytestmark = pytest.mark.skipif(not APK.exists(), reason="Build the Android APK before packaging checks")


def test_paho_android_resources_are_present_and_unchanged():
    with zipfile.ZipFile(REPO / "app/libs/org.eclipse.paho.client.mqttv3-1.2.5.jar") as dependency, zipfile.ZipFile(APK) as apk:
        names = [name for name in dependency.namelist() if
                 (name.startswith("org/eclipse/paho/client/mqttv3/internal/nls/") and name.endswith(".properties")) or
                 (name.startswith("META-INF/services/") and not name.endswith("/"))]
        assert "org/eclipse/paho/client/mqttv3/internal/nls/logcat.properties" in names
        assert "META-INF/services/org.eclipse.paho.client.mqttv3.spi.NetworkModuleFactory" in names
        for name in names:
            assert apk.read(name) == dependency.read(name), name


def test_adding_java_resources_preserves_android_asset_paths_and_font_storage():
    with zipfile.ZipFile(APK) as apk:
        assert all("\\" not in name for name in apk.namelist())
        fonts = [entry for entry in apk.infolist() if entry.filename.endswith(".ttf")]
        assert fonts
        assert all(entry.compress_type == zipfile.ZIP_STORED for entry in fonts)


def test_local_event_qr_encoder_and_license_are_packaged():
    with zipfile.ZipFile(APK) as apk:
        assert b"QRCodeWriter" in apk.read("classes.dex")
        assert b"CulturalQr" in apk.read("classes.dex")
        assert b"Apache License" in apk.read("assets/licenses/ZXing-Apache-2.0.txt")
