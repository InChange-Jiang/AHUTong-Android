from __future__ import annotations

import importlib.util
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path
from unittest import mock


SCRIPT_PATH = Path(__file__).resolve().parents[1] / "scripts" / "release_publish.py"
SPEC = importlib.util.spec_from_file_location("release_publish_under_test", SCRIPT_PATH)
if SPEC is None or SPEC.loader is None:
    raise RuntimeError(f"Could not load release helper: {SCRIPT_PATH}")
release_publish = importlib.util.module_from_spec(SPEC)
sys.modules[SPEC.name] = release_publish
SPEC.loader.exec_module(release_publish)


class ChangelogTests(unittest.TestCase):
    def test_mixed_diff_keeps_only_visible_subjects(self) -> None:
        subjects = "feat: add visible timetable shortcut\nchore: enable debug rollout"
        files = ".agents/skills/yukon-auto-update-release/SKILL.md\napp/src/main/java/Visible.kt"
        with mock.patch.object(release_publish, "ref_available", return_value=True), mock.patch.object(
            release_publish,
            "git_capture",
            side_effect=[subjects, files],
        ):
            changelog = release_publish.derive_changelog_from_release_diff(
                Path("."), "release/1.0.0", "release/1.0.1"
            )

        self.assertEqual("add visible timetable shortcut\n", changelog)
        self.assertNotIn("debug", changelog.lower())

    def test_internal_only_diff_uses_generic_notes(self) -> None:
        subjects = "chore: update debug rollout"
        files = ".agents/skills/yukon-auto-update-release/SKILL.md"
        with mock.patch.object(release_publish, "ref_available", return_value=True), mock.patch.object(
            release_publish,
            "git_capture",
            side_effect=[subjects, files],
        ):
            changelog = release_publish.derive_changelog_from_release_diff(
                Path("."), "release/1.0.0", "release/1.0.1"
            )

        self.assertEqual(release_publish.generic_changelog(), changelog)

    def test_explicit_hidden_changelog_is_rejected(self) -> None:
        with self.assertRaisesRegex(release_publish.ReleaseError, "hidden, debug"):
            release_publish.collect_changelog(
                Path("."),
                ["enable debug rollout"],
                "release/1.0.0",
                "release/1.0.1",
            )


class RollbackVersionTests(unittest.TestCase):
    def test_version_file_is_restored_after_failure(self) -> None:
        original = (
            b"android {\r\n"
            b"    defaultConfig {\r\n"
            b"        versionCode = 42\r\n"
            b'        versionName = "3.2.0"\r\n'
            b"    }\r\n"
            b"}\r\n"
        )
        with tempfile.TemporaryDirectory() as temp_dir:
            repo = Path(temp_dir)
            gradle_file = repo / "app" / "build.gradle.kts"
            gradle_file.parent.mkdir(parents=True)
            gradle_file.write_bytes(original)

            with self.assertRaisesRegex(RuntimeError, "publish failed"):
                with release_publish.temporary_local_versions(repo, 43, "3.1.9"):
                    self.assertEqual((43, "3.1.9"), release_publish.parse_local_versions(repo))
                    raise RuntimeError("publish failed")

            self.assertEqual(original, gradle_file.read_bytes())


class SigningTests(unittest.TestCase):
    def test_signing_passwords_are_not_command_line_arguments(self) -> None:
        config = {
            "keystore": {
                "path": "release.jks",
                "store_password": "store-secret",
                "key_alias": "release",
                "key_password": "key-secret",
            }
        }
        with tempfile.TemporaryDirectory() as temp_dir:
            repo = Path(temp_dir)
            (repo / "app").mkdir()
            run = mock.Mock()
            with mock.patch.object(release_publish, "run_cmd", run), mock.patch.object(
                release_publish,
                "find_build_tool",
                side_effect=[Path("zipalign"), Path("apksigner")],
            ), mock.patch.object(
                release_publish,
                "release_apk_input",
                return_value=Path("unsigned.apk"),
            ), mock.patch.object(
                release_publish,
                "WORK_DIR",
                repo / "work",
            ):
                release_publish.build_and_sign(repo, config, "3.2.1")

        sign_call = run.call_args_list[2]
        sign_command = sign_call.args[0]
        sign_environment = sign_call.kwargs["env"]
        self.assertIn("env:AHUTONG_RELEASE_KS_PASSWORD", sign_command)
        self.assertIn("env:AHUTONG_RELEASE_KEY_PASSWORD", sign_command)
        self.assertNotIn("store-secret", " ".join(sign_command))
        self.assertNotIn("key-secret", " ".join(sign_command))
        self.assertEqual("store-secret", sign_environment["AHUTONG_RELEASE_KS_PASSWORD"])
        self.assertEqual("key-secret", sign_environment["AHUTONG_RELEASE_KEY_PASSWORD"])

    def test_run_cmd_forwards_explicit_environment(self) -> None:
        environment = {"RELEASE_SECRET": "not-on-command-line"}
        completed = subprocess.CompletedProcess(["tool"], 0)
        with mock.patch.object(release_publish.subprocess, "run", return_value=completed) as run:
            release_publish.run_cmd(["tool"], Path("."), env=environment)

        run.assert_called_once_with(["tool"], cwd=".", env=environment)


class ConfigTests(unittest.TestCase):
    def test_missing_known_hosts_file_is_rejected(self) -> None:
        with tempfile.TemporaryDirectory() as temp_dir:
            temp = Path(temp_dir)
            keystore = temp / "release.jks"
            keystore.touch()
            config = {
                "keystore": {
                    "path": str(keystore),
                    "store_password": "store-secret",
                    "key_alias": "release",
                    "key_password": "key-secret",
                },
                "server": {
                    "host": "release.example.test",
                    "port": 22,
                    "username": "publisher",
                    "auth_method": "password",
                    "known_hosts_path": str(temp / "missing-known-hosts"),
                    "password": "server-secret",
                },
            }

            with self.assertRaisesRegex(release_publish.ReleaseError, "known_hosts"):
                release_publish.validate_config(config)

    def test_ssh_rejects_connection_error_and_closes_client(self) -> None:
        class FakeSSHException(Exception):
            pass

        client = mock.Mock()
        client.connect.side_effect = FakeSSHException("unknown host key")
        reject_policy = object()
        fake_paramiko = mock.Mock()
        fake_paramiko.SSHException = FakeSSHException
        fake_paramiko.SSHClient.return_value = client
        fake_paramiko.RejectPolicy.return_value = reject_policy
        config = {
            "server": {
                "host": "release.example.test",
                "port": 22,
                "username": "publisher",
                "auth_method": "password",
                "password": "server-secret",
            }
        }

        with mock.patch.object(release_publish, "import_paramiko", return_value=fake_paramiko):
            with self.assertRaisesRegex(release_publish.ReleaseError, "host-key verification"):
                release_publish.ssh_connect(config)

        client.load_system_host_keys.assert_called_once_with()
        client.set_missing_host_key_policy.assert_called_once_with(reject_policy)
        client.close.assert_called_once_with()


if __name__ == "__main__":
    unittest.main()
