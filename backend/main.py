"""
SafePath AI — FastAPI Server Entry Point Shim.
Enables running the backend directly from the backend directory using:
    uvicorn main:app --host 0.0.0.0 --port 8000
or:
    python main.py
"""
import os
import sys
from importlib.abc import MetaPathFinder

# ── Windows Defender Application Control / SmartAppControl Bypass ───────────────
class PurePyFinder(MetaPathFinder):
    def find_spec(self, fullname, path, target=None):
        if fullname.startswith("sqlalchemy.engine._") and fullname.endswith("_cy"):
            for p in (path or []):
                name = fullname.split(".")[-1]
                py_file = os.path.join(p, name + ".py")
                if os.path.exists(py_file):
                    from importlib.util import spec_from_file_location
                    return spec_from_file_location(fullname, py_file)
        return None

sys.meta_path.insert(0, PurePyFinder())

# Import FastAPI application
from app.main import app, health_check

if __name__ == "__main__":
    import uvicorn
    uvicorn.run("main:app", host="0.0.0.0", port=8000, reload=True)
