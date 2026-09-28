"""Verify Host profile file I/O and CLI precedence without injecting input."""

import argparse
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
        assert initial.startswith("version=2\n")
        assert "field-left=0.12" in initial and "resolution=1080p" in initial
        run("--fps", "120", "--field-right", "0.87", "--profile", path, "--save-profile")
        saved = path.read_text()
        assert "field-left=0.12" in saved and "field-right=0.87" in saved and "fps=120" in saved
        assert "resolution=1080p" in saved and "bitrate=12000000" in saved
        assert not list(path.parent.glob("*.tmp")), "Temporary profile file was not cleaned up"
        for fps in (23, 121, 65536):
            run("--profile", path, "--save-profile", "--fps", fps, success=False)
            assert path.read_text() == saved, "Invalid flags changed the existing profile"
        for flag in ("--sensitivity", "--acceleration", "--smoothing", "--max-speed"):
            result = run("--profile", path, "--save-profile", flag, "1", success=False)
            assert "adjust mouse sensitivity in In Falsus" in result.stderr
            assert path.read_text() == saved, "Retired tuning flags changed the existing profile"
        path.write_text("version=1\nfield-left=0.12\nresolution=1080p\nfps=30\n"
                        "sensitivity=2\nacceleration=3\nsmoothing=0.9\nmax-speed=100\n")
        result = run("--profile", path, "--save-profile")
        assert "Legacy relative tuning ignored" in result.stderr
        upgraded = path.read_text()
        assert upgraded.startswith("version=2\n") and "sensitivity=" not in upgraded
        assert "field-left=0.12" in upgraded and "resolution=1080p" in upgraded and "fps=30" in upgraded
        run("--profile", path, "--no-profile", success=False)
        run("--profile", pathlib.Path(directory) / "missing.ini", success=False)
        path.write_text("version=1\nfield-left=nan\n")
        run("--profile", path, "--save-profile", success=False)
        assert path.read_text() == "version=1\nfield-left=nan\n", "Corrupt profile was silently overwritten"
    print("PASS: profile save/load, legacy migration, retired tuning flags, CLI precedence, atomic replacement and invalid profiles")


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--host", type=pathlib.Path, default=HOST)
    HOST = parser.parse_args().host.resolve()
    main()
