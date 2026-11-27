"""Exercise document-tree grants and copy operations on a fresh Android emulator."""

import argparse
import re
import subprocess
import sys
import time
from pathlib import Path
from xml.etree import ElementTree

PACKAGE = "com.lumeryx.app"
DOWNLOAD = "/data/media/0/Download"
SOURCE = "LUMERYX-QA-source"
TARGET = "LUMERYX-QA-target"
FIXTURE = "fixture.txt"
PAYLOAD = b"LUMERYX SAF smoke test\n"
EXISTING = b"Pre-existing test file\n"
SUCCESS = "Копирование завершено. Содержимое проверено."
DUMP = "/data/local/tmp/lumeryx-saf-smoke.xml"


def adb(*args, timeout=45):
    return subprocess.run(
        ["adb", *args], check=True, capture_output=True, timeout=timeout
    ).stdout


def shell(*args, timeout=45):
    return adb("shell", *args, timeout=timeout).decode(errors="replace").strip()


def nodes():
    message = shell("uiautomator", "dump", DUMP, timeout=20)
    if "dumped to" not in message:
        return []
    return list(ElementTree.fromstring(shell("cat", DUMP)).iter("node"))


def names(elements):
    return [
        element.get("text") or element.get("content-desc")
        for element in elements
        if element.get("text") or element.get("content-desc")
    ]


def matching(elements, labels):
    wanted = {label.casefold() for label in labels}
    return [
        element for element in elements
        if element.get("text", "").casefold() in wanted
        or element.get("content-desc", "").casefold() in wanted
    ]


def wait_for(*labels):
    deadline = time.monotonic() + 45
    latest = []
    while time.monotonic() < deadline:
        try:
            latest = nodes()
        except (subprocess.SubprocessError, ElementTree.ParseError):
            time.sleep(1)
            continue
        matches = matching(latest, labels)
        if matches:
            return matches
        time.sleep(1)
    raise AssertionError(f"Missing {labels!r}. Visible labels: {names(latest)!r}")


def tap(*labels):
    matches = wait_for(*labels)
    node = next(
        (item for item in matches if item.get("clickable") == "true" and item.get("enabled") == "true"),
        matches[-1],
    )
    left, top, right, bottom = map(int, re.findall(r"\d+", node.get("bounds", "")))
    shell("input", "tap", str((left + right) // 2), str((top + bottom) // 2))


def choose_folder(button, folder, parent=None):
    tap(button)
    first = parent or folder
    visible = wait_for(first, "Download", "Downloads", "Show roots", "Open from")
    if not matching(visible, (first, "Download", "Downloads")):
        # The target can be visible already while a navigation button is also present.
        all_nodes = nodes()
        if not matching(all_nodes, (first, "Download", "Downloads")):
            tap("Show roots", "Open from")
    all_nodes = nodes()
    if not matching(all_nodes, (first,)):
        tap("Download", "Downloads")
    tap(first)
    if parent is not None:
        tap(folder)
    tap("Use this folder")
    tap("Allow")


def verify(path, expected=PAYLOAD):
    actual = adb("exec-out", "cat", path)
    if actual != expected:
        raise AssertionError(f"Unexpected content in {path}: {actual!r}")


def setup(apk):
    adb("root")
    adb("wait-for-device")
    for _ in range(8):
        try:
            if shell("id", "-u") == "0":
                break
        except subprocess.SubprocessError:
            pass
        time.sleep(1)
    else:
        raise AssertionError("This scenario requires the AOSP emulator test image")
    source = f"{DOWNLOAD}/{SOURCE}"
    target = f"{DOWNLOAD}/{TARGET}"
    shell("mkdir", "-p", source + "/Album", target)
    shell(f"printf 'LUMERYX SAF smoke test\\n' > {source}/{FIXTURE}")
    shell(f"printf 'LUMERYX SAF smoke test\\n' > {source}/Album/nested.txt")
    shell("chown", "-R", "media_rw:media_rw", source, target)
    shell("restorecon", "-R", source, target)
    adb("install", "-r", str(apk), timeout=90)
    shell("wm", "dismiss-keyguard")
    shell("am", "start", "-W", "-n", f"{PACKAGE}/.MainActivity")


def run(apk):
    setup(apk)
    tap("Облако")
    choose_folder("Откуда", SOURCE)
    choose_folder("Куда", TARGET)

    print("Copying a file between Android document trees", flush=True)
    tap(FIXTURE)
    tap("Копировать")
    wait_for(SUCCESS)
    verify(f"{DOWNLOAD}/{TARGET}/{FIXTURE}")

    shell(f"printf 'Pre-existing test file\\n' > {DOWNLOAD}/{TARGET}/{FIXTURE}")
    print("Checking skip and keep-both for a name collision", flush=True)
    tap(FIXTURE)
    wait_for("Имя уже занято")
    tap("Пропустить")
    wait_for("Пропущено.")
    verify(f"{DOWNLOAD}/{TARGET}/{FIXTURE}", EXISTING)
    assert shell("ls", "-1", f"{DOWNLOAD}/{TARGET}").splitlines() == [FIXTURE]
    tap(FIXTURE)
    wait_for("Имя уже занято")
    tap("Сохранить оба")
    wait_for(SUCCESS)
    duplicate = "fixture (2).txt"
    verify(f"'{DOWNLOAD}/{TARGET}/{duplicate}'")
    verify(f"{DOWNLOAD}/{TARGET}/{FIXTURE}", EXISTING)

    print("Copying a directory and reversing direction", flush=True)
    tap("Album")
    tap("Копировать")
    wait_for(SUCCESS)
    verify(f"{DOWNLOAD}/{TARGET}/Album/nested.txt")
    tap("Поменять местами")
    tap(duplicate)
    tap("Копировать")
    wait_for(SUCCESS)
    # The filename contains spaces and parentheses, so pass a quoted shell path.
    verify(f"'{DOWNLOAD}/{SOURCE}/{duplicate}'")
    verify(f"{DOWNLOAD}/{SOURCE}/{FIXTURE}")
    assert not any(
        name.startswith("LUMERYX-part-")
        for name in shell("ls", "-1", f"{DOWNLOAD}/{TARGET}").splitlines()
    )
    print("Rejecting a folder copied onto itself", flush=True)
    tap("Поменять местами")
    choose_folder("Куда", "Album", SOURCE)
    tap("Album")
    tap("Копировать")
    wait_for("Нельзя копировать папку в неё саму или в её подпапку.")
    assert shell("ls", "-1", f"{DOWNLOAD}/{SOURCE}/Album").splitlines() == ["nested.txt"]
    print("Android SAF smoke test passed", flush=True)


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("apk", type=Path)
    args = parser.parse_args()
    try:
        run(args.apk)
    except Exception:
        try:
            print(f"Last Android UI labels: {names(nodes())!r}", file=sys.stderr)
        except Exception:
            pass
        raise


if __name__ == "__main__":
    main()
