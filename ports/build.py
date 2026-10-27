#!/usr/bin/env python3
"""Builds the jar of this mod for another Minecraft version (the main build is for 1.19.4).

    python3 ports/build.py 1.19.2        (1.18, 1.18.1, 1.18.2, 1.19, 1.19.1, 1.19.2, 1.19.3)

On Windows: py ports\build.py 1.19.2. Optional environment variables: PORT_WORK (work folder), GRADLE (gradle for the
builds up to 1.20.4), GRADLE9 (gradle 9.2 for 1.20.5 and later), JAVA21_HOME / JAVA17_HOME (the JDK that runs Gradle, set as JAVA_HOME).

It makes a work folder (default <temp folder>/materialshare-ports/<version>), downloads the Litematica, malilib, Syncmatica and
Mod Menu jars of that version from Modrinth, copies the shared source, builds, and puts the jar into dist/.
The game API differences live in src/compat/<version>/ (see build.gradle, "mcCompat").
"""
import json
import os
import shutil
import subprocess
import sys
import tempfile
import urllib.parse
import urllib.request
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from memory_guard import run_guarded  # noqa: E402
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
VERSIONS = {
    "1.18": dict(yarn="1.18+build.1", fabric_api="0.44.0+1.18", litematica="0.9.1", malilib="0.10.0-dev.26", modmenu="3.0.1"),
    "1.18.1": dict(yarn="1.18.1+build.22", fabric_api="0.46.6+1.18", litematica="0.10.4", malilib="0.11.8", modmenu="3.0.1"),
    "1.18.2": dict(yarn="1.18.2+build.4", fabric_api="0.77.0+1.18.2", litematica="0.11.7", malilib="0.12.1", modmenu="3.2.5"),
    "1.19": dict(yarn="1.19+build.4", fabric_api="0.58.0+1.19", litematica="0.12.2", malilib="0.13.0", modmenu="4.0.4"),
    "1.19.1": dict(yarn="1.19.1+build.6", fabric_api="0.58.5+1.19.1", litematica="0.12.3", malilib="0.13.0", modmenu="4.0.5"),
    "1.19.2": dict(yarn="1.19.2+build.28", fabric_api="0.77.0+1.19.2", litematica="0.12.7", malilib="0.13.0", modmenu="4.1.2"),
    "1.19.3": dict(yarn="1.19.3+build.5", fabric_api="0.76.1+1.19.3", litematica="0.13.2", malilib="0.14.0", modmenu="5.1.0"),
    # the main build; here only to make a work folder for the two-client test (the jar of the main build comes from `gradle build`)
    "1.19.4": dict(yarn="1.19.4+build.2", fabric_api="0.87.2+1.19.4", litematica="0.14.7", malilib="0.15.4", modmenu="6.3.1"),
    "1.20.1": dict(yarn="1.20.1+build.10", fabric_api="0.92.2+1.20.1", litematica="0.15.4", malilib="0.16.3", modmenu="7.2.2",
                   syncmatica=("1.20.1", "1.20.1-0.3.11"), loader="0.15.6", api_min="0.92.2"),
    "1.20.2": dict(yarn="1.20.2+build.4", fabric_api="0.91.6+1.20.2", litematica="0.16.1", malilib="0.17.0", modmenu="8.0.1",
                   syncmatica=("1.20.2", "1.20.2-0.3.11"), loader="0.15.6", api_min="0.91.6"),
    "1.20.3": dict(yarn="1.20.3+build.1", fabric_api="0.91.1+1.20.3", litematica="0.17.4", malilib="0.18.3", modmenu="9.0.0",
                   syncmatica=("1.20.3", "1.20.4-0.3.11"), loader="0.15.6", api_min="0.91.1"),
    "1.20.5": dict(yarn="1.20.5+build.1", fabric_api="0.97.8+1.20.5", litematica="0.18.3", malilib="0.19.2", modmenu="10.0.0",
                   syncmatica=("1.20.5", "1.20.6-0.3.12-sakura.10"), loader="0.15.10", api_min="0.97.8", java=21, loom="1.14.10"),
    "1.20.6": dict(yarn="1.20.6+build.3", fabric_api="0.100.8+1.20.6", litematica="0.18.3", malilib="0.19.2", modmenu="10.0.0",
                   syncmatica=("1.20.6", "1.20.6-0.3.12-sakura.10"), loader="0.15.10", api_min="0.100.8", java=21, loom="1.14.10"),
    "1.20.4": dict(yarn="1.20.4+build.3", fabric_api="0.97.2+1.20.4", litematica="0.17.4", malilib="0.18.3", modmenu="9.2.0",
                   syncmatica=("1.20.4", "1.20.4-0.3.11"), loader="0.15.6", api_min="0.97.2"),
}


def get(url):
    req = urllib.request.Request(url, headers={"User-Agent": "materialshare-ports/1.0"})
    return json.load(urllib.request.urlopen(req, timeout=60))


def download(slug, mc, version, target):
    query = urllib.parse.urlencode({"game_versions": json.dumps([mc]), "loaders": json.dumps(["fabric"])})
    for v in get(f"https://api.modrinth.com/v2/project/{slug}/version?{query}"):
        if v["version_number"] == version:
            url = [f for f in v["files"] if f["primary"]][0]["url"]
            target.parent.mkdir(parents=True, exist_ok=True)
            urllib.request.urlretrieve(url, target)
            return
    sys.exit(f"{slug} {version} for {mc} not found on Modrinth")


def main():
    if len(sys.argv) < 2 or sys.argv[1] not in VERSIONS:
        sys.exit("usage: build.py <%s>" % "|".join(VERSIONS))
    mc = sys.argv[1]
    cfg = VERSIONS[mc]
    work = Path(os.environ.get("PORT_WORK", Path(tempfile.gettempdir()) / "materialshare-ports")) / mc
    shutil.rmtree(work / "src", ignore_errors=True)
    shutil.rmtree(work / "build", ignore_errors=True)  # fabric.mod.json is filled in from the properties; never reuse an old result
    work.mkdir(parents=True, exist_ok=True)
    shutil.copytree(ROOT / "src", work / "src")
    shutil.copy(ROOT / "settings.gradle", work / "settings.gradle")
    lit = f"libs/litematica-{mc}-{cfg['litematica']}.jar"
    mali = f"libs/malilib-{mc}-{cfg['malilib']}.jar"
    gradle = (ROOT / "build.gradle").read_text(encoding="utf-8")
    gradle = gradle.replace("libs/litematica-1.19.4-0.14.7.jar", lit).replace("libs/malilib-1.19.4-0.15.4.jar", mali)
    if cfg.get("loom"):
        gradle = gradle.replace("version '1.11.8'", f"version '{cfg['loom']}'")
    if cfg.get("java"):
        gradle = gradle.replace("options.release = 17", f"options.release = {cfg['java']}").replace("VERSION_17", f"VERSION_{cfg['java']}")
    (work / "build.gradle").write_text(gradle, encoding="utf-8")
    if mc.startswith("1.20"):
        # from 1.20 on the 2D drawing goes through a DrawContext instead of a MatrixStack (McClientCompat does the drawing)
        for f in (work / "src" / "main" / "java").rglob("*.java"):
            text = f.read_text(encoding="utf-8")
            if "MatrixStack matrices" in text:
                text = text.replace("MatrixStack matrices", "DrawContext matrices")
                text = text.replace("\nimport ", "\nimport net.minecraft.client.gui.DrawContext;\nimport ", 1)
                f.write_text(text, encoding="utf-8")
    if mc in ("1.20.2", "1.20.3", "1.20.4", "1.20.5", "1.20.6"):
        # malilib's onMouseScrolled got the horizontal amount as well
        import re
        for f in (work / "src" / "main" / "java").rglob("*.java"):
            text = f.read_text(encoding="utf-8")
            new = re.sub(r"onMouseScrolled\(int mouseX, int mouseY, double (\w+)\)", r"onMouseScrolled(int mouseX, int mouseY, double horizontalAmount, double \1)", text)
            new = re.sub(r"super\.onMouseScrolled\(mouseX, mouseY, (\w+)\)", r"super.onMouseScrolled(mouseX, mouseY, horizontalAmount, \1)", new)
            if mc in ("1.20.5", "1.20.6") and f.name == "MaterialShareState.java":
                # persistent state gets the registry lookup from 1.20.5 on
                new = new.replace("public NbtCompound writeNbt(NbtCompound nbt) {", "public NbtCompound writeNbt(NbtCompound nbt, net.minecraft.registry.RegistryWrapper.WrapperLookup lookup) {")
            new = new.replace("int mouseX, int mouseY, double amount, CallbackInfoReturnable", "int mouseX, int mouseY, double horizontalAmount, double amount, CallbackInfoReturnable")
            if new != text:
                f.write_text(new, encoding="utf-8")
    props = (ROOT / "gradle.properties").read_text(encoding="utf-8").splitlines()
    base = dict(line.split("=", 1) for line in props if "=" in line)
    base.update(minecraft_version=mc, yarn_mappings=cfg["yarn"], fabric_version=cfg["fabric_api"],
                mod_version=f"{base['mod_version']}+{mc}", loader_version=cfg.get("loader", base["loader_version"]), mcCompat=mc, minecraft_dependency=mc,
                # the lowest Fabric API the docs promise (the versions in VERSIONS are the ones tested)
                fabric_api_min=cfg.get("api_min") or "0.44.0" if mc in ("1.18", "1.18.1") else "0.58.0" if mc in ("1.19", "1.19.1") else "0.83.0" if mc.startswith("1.20") else "0.76.0",
                # Fabric API before 1.19.2 had the mod id "fabric"
                fabric_api_id="fabric" if mc in ("1.18", "1.18.1", "1.19", "1.19.1") else "fabric-api")
    (work / "gradle.properties").write_text("".join(f"{k}={v}\n" for k, v in base.items()), encoding="utf-8")
    for slug, version, target in (("litematica", cfg["litematica"], lit), ("malilib", cfg["malilib"], mali),
                                  ("syncmatica", cfg.get("syncmatica", ("1.18.2", "1.18.2-0.3.11"))[1], "libs/syncmatica.jar"),
                                  ("modmenu", cfg["modmenu"], "libs/compat/masa/modmenu.jar")):
        if not (work / target).exists():
            download(slug, cfg["syncmatica"][0] if slug == "syncmatica" and "syncmatica" in cfg else "1.18.2" if slug == "syncmatica" else mc,
                     version, work / target)
    # Loom needs a Java 21 JVM to run Gradle (the bytecode target is set by options.release), so JAVA21_HOME wins
    if cfg.get("loom"):
        gradle_cmd = os.environ.get("GRADLE9", "/opt/gradle9/gradle-9.2.0/bin/gradle")
    else:
        gradle_cmd = os.environ.get("GRADLE", "gradle")
    env = dict(os.environ)
    jdk = os.environ.get("JAVA21_HOME") or os.environ.get("JAVA17_HOME")
    if jdk:
        env["JAVA_HOME"] = jdk
    if os.name == "nt" and not gradle_cmd.lower().endswith((".bat", ".cmd")):
        gradle_cmd += ".bat"
    run_guarded([gradle_cmd, "build", "-x", "test", "--no-daemon", "-q"], cwd=work, env=env)  # stops when less than 3 GB of RAM is free
    jar = next((work / "build" / "libs").glob("*.jar"))
    (ROOT / "dist").mkdir(exist_ok=True)
    shutil.copy(jar, ROOT / "dist" / jar.name)
    print("built", ROOT / "dist" / jar.name)


if __name__ == "__main__":
    main()
