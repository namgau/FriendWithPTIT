"""Small filesystem model registry.

Layout::

    models/<module>/<version>/metadata.json
    models/<module>/<version>/model.pkl   (optional)
    models/<module>/.active               (optional, contains a version name)

The service currently ships deterministic fallback rules.  A future trained
artifact can be installed in this layout without changing the API contract;
``active_metadata`` and ``load_artifact`` are the hooks for that integration.
"""

from __future__ import annotations

import json
from dataclasses import dataclass
from pathlib import Path

from serving.schemas import ModelVersion

MODULES = ("m1", "m2", "m3")


@dataclass(frozen=True)
class ModelArtifact:
    module: str
    version: str
    directory: Path
    metadata: dict


class ModelRegistry:
    def __init__(self, root: Path | str = "models") -> None:
        self.root = Path(root)

    def _module_dir(self, module: str) -> Path:
        return self.root / module

    def versions(self, module: str) -> list[ModelVersion]:
        module_dir = self._module_dir(module)
        if not module_dir.is_dir():
            return []
        active = self._active_version(module)
        result: list[ModelVersion] = []
        for directory in sorted(module_dir.iterdir(), key=lambda item: item.name):
            if not directory.is_dir() or directory.name.startswith("."):
                continue
            metadata = self._read_metadata(directory)
            result.append(
                ModelVersion(
                    module=module,
                    version=directory.name,
                    path=str(directory),
                    active=directory.name == active,
                    metadata=metadata,
                )
            )
        return sorted(result, key=lambda item: _version_key(item.version), reverse=True)

    def list_all(self) -> list[ModelVersion]:
        result: list[ModelVersion] = []
        for module in MODULES:
            result.extend(self.versions(module))
        return result

    def active(self, module: str) -> ModelArtifact | None:
        version = self._active_version(module)
        if version is None:
            available = [item.version for item in self.versions(module)]
            if not available:
                return None
            version = available[0]
        directory = self._module_dir(module) / version
        if not directory.is_dir():
            return None
        return ModelArtifact(
            module=module,
            version=version,
            directory=directory,
            metadata=self._read_metadata(directory),
        )

    def activate(self, module: str, version: str) -> ModelArtifact:
        if module not in MODULES:
            raise ValueError(f"Unknown module: {module}")
        directory = self._module_dir(module) / version
        if not directory.is_dir():
            raise FileNotFoundError(f"Model version not found: {module}/{version}")
        module_dir = self._module_dir(module)
        module_dir.mkdir(parents=True, exist_ok=True)
        (module_dir / ".active").write_text(version, encoding="utf-8")
        return ModelArtifact(
            module=module,
            version=version,
            directory=directory,
            metadata=self._read_metadata(directory),
        )

    def load_artifact(self, module: str):
        """Load ``model.pkl`` for the active version, if one exists.

        joblib is imported lazily so the API can boot on a minimal install.
        """

        artifact = self.active(module)
        if artifact is None:
            return None
        model_path = artifact.directory / "model.pkl"
        if not model_path.is_file():
            return None
        try:
            import joblib  # type: ignore[import-not-found]
        except ImportError:
            return None
        return joblib.load(model_path)

    def _active_version(self, module: str) -> str | None:
        pointer = self._module_dir(module) / ".active"
        if pointer.is_file():
            value = pointer.read_text(encoding="utf-8").strip()
            return value or None
        return None

    @staticmethod
    def _read_metadata(directory: Path) -> dict:
        metadata_path = directory / "metadata.json"
        if not metadata_path.is_file():
            return {}
        try:
            value = json.loads(metadata_path.read_text(encoding="utf-8"))
        except (OSError, ValueError):
            return {}
        return value if isinstance(value, dict) else {}


def _version_key(value: str) -> tuple[int, ...]:
    parts: list[int] = []
    for chunk in value.replace("-", ".").replace("_", ".").split("."):
        try:
            parts.append(int(chunk))
        except ValueError:
            parts.append(0)
    return tuple(parts)
