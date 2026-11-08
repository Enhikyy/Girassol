# Native Studio and 3D roadmap

## Delivered in this branch

- New Native Studio destination in the app navigation.
- Private, on-device source workspace with edit/save, SAF import/export, and ZIP project export.
- One-tap Android project skeleton generation (Kotlin Activity, manifest, Gradle Kotlin DSL).
- OBJ mesh parser with polygon fan triangulation, positive/negative indices, normalization and OBJ export.
- Binary and ASCII STL import, binary STL export, and a lightweight wireframe preview with rotation.
- Unit tests for OBJ triangulation/round-trip, negative indices and binary STL round-trip.

## APK build boundary

The editor and project generator work locally. They do **not** yet compile an APK inside the Android app. A normal Android application does not inherit Android Studio's JDK, Gradle daemon, Android SDK platforms/build-tools, AAPT2, D8/R8 or NDK. Girassol currently does not bundle this full toolchain, and the Galaxy A22 5G's 4 GB RAM makes embedding and running the entire Android build stack a poor default. The UI therefore reports toolchain availability and never claims a build succeeded when no SDK is present.

A genuine offline APK builder requires a separately packaged, versioned toolchain (JDK, Gradle distribution, SDK platform/build-tools, AAPT2, D8/R8, and optionally NDK/CMake), license review, ABI/disk budgeting, and device validation. The current branch intentionally does not fake this by shelling out to a nonexistent SDK or silently uploading source to a remote builder.

## 3D format boundary

OBJ and STL are implemented natively in Kotlin and have unit tests. GLTF/GLB scene graphs, materials, textures, animation, mesh decimation, Boolean modeling, and a production GPU renderer are not included yet. The current viewport is a simple wireframe preview, not a full CAD/DCC engine.

## Next engineering stages

1. Add glTF 2.0/GLB import/export with a maintained parser and license audit.
2. Add a native GPU renderer (Filament or equivalent) and camera/orbit/pan controls.
3. Add source syntax highlighting, multi-file tree, diff/patch review, diagnostics and project indexing to Studio.
4. Package and validate an offline Android build toolchain, then add real assemble/test/install flows with resource quotas and cancellation.
5. Add an integration test that creates a starter project, exports it, builds it with the packaged toolchain, and validates APK metadata/signature.
