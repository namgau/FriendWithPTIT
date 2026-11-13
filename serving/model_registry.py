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

from serving.schemas_mobile import ModelVersion

MODULES = ("m1", "m2", "m3")


@dataclass(frozen=True)
class ModelArtifact:
    module: str
    version: str
    directory: Path
    metadata: dict


def _safe_load_artifact(path: Path):
    try:
        import joblib
        try:
            import __main__
            try:
                import src.m3_health.train_c2_transformer as c2_mod
                cls = getattr(c2_mod, "EmbeddingClassifierFallback", None)
            except Exception:
                class EmbeddingClassifierFallback:
                    pass
                cls = EmbeddingClassifierFallback
            if cls is not None:
                setattr(__main__, "EmbeddingClassifierFallback", cls)
        except Exception:
            pass
        return joblib.load(path)
    except Exception:
        if path.suffix == ".pt":
            try:
                import torch
                return torch.load(path, map_location="cpu")
            except Exception:
                pass
        return None


class ModelRegistry:
    def __init__(self, root: Path | str | None = None) -> None:
        default_dir = Path(__file__).resolve().parent.parent / "models"
        if root is None:
            self.root = default_dir
        else:
            p = Path(root)
            self.root = p if p.is_dir() else default_dir

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

    def _resolve_version_dir(self, module: str, version: str | None = None) -> Path | None:
        module_dir = self._module_dir(module)
        if version:
            cand = module_dir / version
            if cand.is_dir():
                return cand
            direct = Path(version)
            if direct.is_dir():
                return direct
            if module_dir.is_dir():
                for sub in module_dir.iterdir():
                    if sub.is_dir() and sub.name.lower() == version.lower():
                        return sub
            return None

        active = self._active_version(module)
        if active:
            cand = module_dir / active
            if cand.is_dir():
                return cand

        if module == "m3" and module_dir.is_dir():
            for v_name in ("v1", "c2", "c4"):
                cand = module_dir / v_name
                if cand.is_dir():
                    return cand

        available = self.versions(module)
        if available:
            return Path(available[0].path)
        return None

    def read_metadata(self, module: str = "m3", version: str | None = None) -> dict:
        """Đọc metadata.json của module/version tương ứng."""
        directory = self._resolve_version_dir(module, version)
        if directory is None or not directory.is_dir():
            return {}
        return self._read_metadata(directory)

    def get_thresholds(self, version: str | None = None) -> dict[str, float]:
        """Đọc metadata.json của module m3 lấy tau1, tau2, tau_nudge."""
        meta = self.read_metadata("m3", version)
        head_a = meta.get("head_a", {}) if isinstance(meta.get("head_a"), dict) else {}
        head_b = meta.get("head_b", {}) if isinstance(meta.get("head_b"), dict) else {}
        thresholds = meta.get("thresholds", {}) if isinstance(meta.get("thresholds"), dict) else {}

        tau1 = float(meta.get("tau1", head_a.get("tau1", thresholds.get("tau1", 0.3))))
        tau2 = float(meta.get("tau2", head_a.get("tau2", thresholds.get("tau2", 0.6))))
        tau_nudge = float(meta.get("tau_nudge", head_b.get("tau_nudge", thresholds.get("tau_nudge", 0.5))))
        return {"tau1": tau1, "tau2": tau2, "tau_nudge": tau_nudge}

    def load_head_a(self, version: str | None = None):
        """Load artifact cho Head A (Conversation Health) từ models/m3/<version>/."""
        directory = self._resolve_version_dir("m3", version)
        if directory is None or not directory.is_dir():
            return None
        meta = self._read_metadata(directory)
        head_a_cfg = meta.get("head_a", {}) if isinstance(meta.get("head_a"), dict) else {}
        explicit_file = head_a_cfg.get("model_file") or meta.get("head_a_model")
        candidates = [explicit_file] if explicit_file else ["head_a.pkl", "head_a.joblib", "model_head_a.pkl", "model.pkl"]
        for fname in candidates:
            if not fname:
                continue
            for p in (directory / fname, directory / "fold_0" / fname):
                if p.is_file():
                    model = _safe_load_artifact(p)
                    if model is not None:
                        try:
                            setattr(model, "metadata", meta)
                            setattr(model, "version", directory.name)
                        except Exception:
                            pass
                        return model
        return None

    def load_head_b(self, version: str | None = None):
        """Load artifact cho Head B (Conversation Safety) từ models/m3/<version>/."""
        directory = self._resolve_version_dir("m3", version)
        if directory is None or not directory.is_dir():
            return None
        meta = self._read_metadata(directory)
        head_b_cfg = meta.get("head_b", {}) if isinstance(meta.get("head_b"), dict) else {}
        explicit_file = head_b_cfg.get("model_file") or meta.get("head_b_model")
        candidates = [explicit_file] if explicit_file else [
            "head_b.pkl", "head_b.joblib", "model_head_b.pkl", "model.pkl",
            "table.joblib", "craft_gru.pt",
        ]
        for fname in candidates:
            if not fname:
                continue
            for p in (directory / fname, directory / "fold_0" / fname):
                if p.is_file():
                    model = _safe_load_artifact(p)
                    if model is not None:
                        try:
                            setattr(model, "metadata", meta)
                            setattr(model, "version", directory.name)
                        except Exception:
                            pass
                        return model
        return None

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
