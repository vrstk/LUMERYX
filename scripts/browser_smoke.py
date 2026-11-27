"""Check browser navigation, HTTPS loading, bookmarks and session restoration."""

import subprocess

from saf_copy_smoke import PACKAGE, nodes, names, shell, tap, wait_for


def offer(url):
    shell(
        f"am start -W -a android.intent.action.SEND "
        f"-t text/plain --es android.intent.extra.TEXT {url} -n {PACKAGE}/.MainActivity"
    )
    wait_for("Открыть полученную ссылку?")


def main():
    tap("Браузер")
    tap("Вкладки (1)")
    tap("Новая вкладка")
    wait_for("Вкладки (2)")
    print("Browser opened two tabs", flush=True)

    offer("file:///data/local/tmp/browser-smoke")
    wait_for("Укажите действительный адрес HTTPS.")
    tap("Готово")
    print("Browser rejected a local-file share", flush=True)

    offer("https://example.org/")
    tap("Открыть")
    tap("Вкладки (2)")
    wait_for("Example Domain")
    tap("Example Domain")
    tap("Ссылка")
    tap("Добавить закладку")
    print("Browser loaded HTTPS and saved a bookmark", flush=True)

    shell("am", "force-stop", PACKAGE)
    shell("am", "start", "-W", "-n", f"{PACKAGE}/.MainActivity")
    tap("Браузер")
    wait_for("Вкладки (2)")
    tap("Закладки")
    wait_for("Example Domain")
    print("Browser tabs and bookmark survived process restart", flush=True)


if __name__ == "__main__":
    try:
        main()
    except (AssertionError, subprocess.SubprocessError):
        print(f"Last Android UI labels: {names(nodes())!r}", flush=True)
        raise
