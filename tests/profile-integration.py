"""Verify Host profile file I/O and CLI precedence without injecting input."""

import pathlib
import subprocess
import tempfile


ROOT = pathlib.Path(__file__).resolve().parent.parent
HOST = ROOT / "dist" / "InFalsusTouchHost.exe"


def run(*args, success=True):
    result = subprocess.run([str(HOST), *map(str, args)], capture_output=True, text=True,
                            creationflags=subprocess.CREATE_NO_WINDOW, timeout=5)
    assert (result.returncode == 0) == success, (result.stdout, result.stderr)
    return result


def main():
    with tempfile.TemporaryDirectory(prefix="ift-profile-") as directory:
        path = pathlib.Path(directory) / "nested" / "host.ini"
        run("--profile", path, "--save-profile", "--field-left", "0.12", "--field-y", "0.63",
            "--resolution", "1080p", "--fps", "30", "--bitrate", "12000000")
        initial = path.read_text()
        assert "field-left=0.12" in initial and "resolution=1080p" in initial
        run("--fps", "60", "--field-right", "0.87", "--profile", path, "--save-profile")
        saved = path.read_text()
        assert "field-left=0.12" in saved and "field-right=0.87" in saved and "fps=60" in saved
        assert "resolution=1080p" in saved and "bitrate=12000000" in saved
        assert not list(path.parent.glob("*.tmp")), "Temporary profile file was not cleaned up"
        run("--profile", path, "--save-profile", "--fps", "200", success=False)
        assert path.read_text() == saved, "Invalid flags changed the existing profile"
        run("--profile", path, "--no-profile", success=False)
        run("--profile", pathlib.Path(directory) / "missing.ini", success=False)
        path.write_text("version=1\nfield-left=nan\n")
        run("--profile", path, "--save-profile", success=False)
        assert path.read_text() == "version=1\nfield-left=nan\n", "Corrupt profile was silently overwritten"
    print("PASS: profile save/load, CLI precedence, atomic replacement, invalid and missing profile handling")


if __name__ == "__main__":
    main()
