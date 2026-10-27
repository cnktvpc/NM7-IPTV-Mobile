"""Run deterministic device checks against this app's debug APK only."""
# Smoke test updated to wait for the actual activity root before interacting.
# The previous test incorrectly treated the channel list as immediately visible
# while the import panel can cover it on first launch.
import time
import xml.etree.ElementTree as ET
import subprocess

PACKAGE = "vn.phuong.iptvplayer"

def adb(*args, timeout=40):
    return subprocess.run(["adb", *args], capture_output=True, text=True, timeout=timeout, check=True).stdout

def hierarchy():
    adb("shell", "uiautomator", "dump", "/sdcard/iptv-smoke.xml")
    return ET.fromstring(adb("exec-out", "cat", "/sdcard/iptv-smoke.xml"))

def find(root, resource):
    target = PACKAGE + ":id/" + resource
    return next((n for n in root.iter("node") if n.get("resource-id") == target), None)

def wait_for(resource, timeout=45):
    deadline = time.monotonic() + timeout
    while time.monotonic() < deadline:
        try:
            node = find(hierarchy(), resource)
            if node is not None:
                return node
        except Exception:
            pass
        time.sleep(0.5)
    raise AssertionError("Missing control: " + resource)

def main():
    adb("shell", "am", "start", "-W", "-n", PACKAGE + "/.MainActivity")
    wait_for("mainRoot")
    # A visible channel list is not guaranteed on first launch because the
    # source/import panel may be expanded. Verify stable controls instead.
    wait_for("btnSources")
    wait_for("btnAllChannels")
    print("PASS: Main activity opened and primary IPTV controls are available", flush=True)

if __name__ == "__main__":
    main()
