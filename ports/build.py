#!/usr/bin/env python3
"""Builds the jar of this mod for another Minecraft version (the main build is for 1.19.4).

    python3 ports/build.py 1.19.2        (1.18, 1.18.1, 1.18.2, 1.19, 1.19.1, 1.19.2, 1.19.3)

It makes a work folder (default /tmp/materialshare-ports/<version>), downloads the Litematica, malilib, Syncmatica and
Mod Menu jars of that version from Modrinth, copies the shared source, builds, and puts the jar into dist/.
The game API differences live in src/compat/<version>/ (see build.gradle, "mcCompat").
"""
import json
import os
import shutil
import subprocess
import sys
import urllib.parse
import urllib.request
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
    work = Path(os.environ.get("PORT_WORK", "/tmp/materialshare-ports")) / mc
    shutil.rmtree(work / "src", ignore_errors=True)
    shutil.rmtree(work / "build", ignore_errors=True)  # fabric.mod.json is filled in from the properties; never reuse an old result
    work.mkdir(parents=True, exist_ok=True)
    shutil.copytree(ROOT / "src", work / "src")
    shutil.copy(ROOT / "settings.gradle", work / "settings.gradle")
    lit = f"libs/litematica-{mc}-{cfg['litematica']}.jar"
    mali = f"libs/malilib-{mc}-{cfg['malilib']}.jar"
    gradle = (ROOT / "build.gradle").read_text()
    gradle = gradle.replace("libs/litematica-1.19.4-0.14.7.jar", lit).replace("libs/malilib-1.19.4-0.15.4.jar", mali)
    (work / "build.gradle").write_text(gradle)
    props = (ROOT / "gradle.properties").read_text().splitlines()
    base = dict(line.split("=", 1) for line in props if "=" in line)
    base.update(minecraft_version=mc, yarn_mappings=cfg["yarn"], fabric_version=cfg["fabric_api"],
                mod_version=f"{base['mod_version']}+{mc}", mcCompat=mc, minecraft_dependency=mc,
                # the lowest Fabric API the docs promise (the versions in VERSIONS are the ones tested)
                fabric_api_min="0.44.0" if mc in ("1.18", "1.18.1") else "0.58.0" if mc in ("1.19", "1.19.1") else "0.76.0",
                # Fabric API before 1.19.2 had the mod id "fabric"
                fabric_api_id="fabric" if mc in ("1.18", "1.18.1", "1.19", "1.19.1") else "fabric-api")
    (work / "gradle.properties").write_text("".join(f"{k}={v}\n" for k, v in base.items()))
    for slug, version, target in (("litematica", cfg["litematica"], lit), ("malilib", cfg["malilib"], mali),
                                  ("syncmatica", "1.18.2-0.3.11", "libs/syncmatica.jar"),
                                  ("modmenu", cfg["modmenu"], "libs/compat/masa/modmenu.jar")):
        if not (work / target).exists():
            download(slug, mc, version, work / target)
    subprocess.run(["gradle", "build", "-x", "test", "--no-daemon", "-q"], cwd=work, check=True)
    jar = next((work / "build" / "libs").glob("*.jar"))
    shutil.copy(jar, ROOT / "dist" / jar.name)
    print("built", ROOT / "dist" / jar.name)


if __name__ == "__main__":
    main()
