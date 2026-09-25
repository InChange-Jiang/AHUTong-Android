#!/usr/bin/env python3
"""package_ahup.py —— 把 feature 模块打成可运行期装载的 .ahup 插件包。

用法（Gradle packageAhup 任务调用）：
  python package_ahup.py <moduleDir> <moduleAar> <outFile> \
      --id campus_circle --title 校园圈子 --summary 简介 --tint 0xFF5C6BC0 \
      --version 0.1.0 --author AHUTong --entry com.xxx.CirclePlugin \
      --icon-src <可选 PNG> --keystore <可选> --storepass x --alias ahup --keypass x

流程：AAR classes.jar → D8 → plugin.dex →（可选）签名 → manifest.json + icon.png 打包 zip。
未提供 keystore 时产未签名包（宿主安装时会有强警告）。
"""
import argparse
import json
import subprocess
import sys
import zipfile
from pathlib import Path

SDK = Path(r"C:\Users\InChange_Jiang\AppData\Local\Android\Sdk")
D8 = SDK / "build-tools" / "36.0.0" / "d8.bat"
ANDROID_JAR = SDK / "platforms" / "android-36" / "android.jar"
SIGNER = Path(__file__).parent / "AhupSign.java"
JAVA = r"C:\Program Files\Microsoft\jdk-21.0.8.9-hotspot\bin\java.exe"


def make_default_icon(path: Path, tint: int) -> None:
    """无图标素材时画一个气泡占位 PNG（tint 描边）。"""
    from PIL import Image, ImageDraw

    r, g, b = (tint >> 16) & 0xFF, (tint >> 8) & 0xFF, tint & 0xFF
    img = Image.new("RGBA", (96, 96), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    d.rounded_rectangle([10, 14, 86, 66], radius=12, outline=(r, g, b, 255), width=5)
    d.polygon([(26, 64), (26, 84), (46, 64)], fill=(r, g, b, 255))
    d.line([26, 34, 70, 34], fill=(r, g, b, 255), width=5)
    d.line([26, 48, 56, 48], fill=(r, g, b, 255), width=5)
    img.save(path)


def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("module_dir")
    ap.add_argument("aar")
    ap.add_argument("out")
    ap.add_argument("--id", required=True)
    ap.add_argument("--title", required=True)
    ap.add_argument("--summary", default="")
    ap.add_argument("--tint", default="0xFF607D8B")
    ap.add_argument("--version", default="0.1.0")
    ap.add_argument("--author", default="未知作者")
    ap.add_argument("--entry", required=True)
    ap.add_argument("--capabilities", default="NETWORK,PLUGIN_STORAGE")
    ap.add_argument("--icon-src", default="")
    ap.add_argument("--keystore", default="")
    ap.add_argument("--storepass", default="")
    ap.add_argument("--alias", default="ahup")
    ap.add_argument("--keypass", default="")
    a = ap.parse_args()

    work = Path(a.module_dir) / "build" / "ahup-work"
    work.mkdir(parents=True, exist_ok=True)

    # 1. AAR 里取 classes.jar 解出 class 文件（每次开新目录，不批量删旧文件）
    import time
    classes_dir = work / f"classes_{int(time.time())}"
    classes_dir.mkdir()
    with zipfile.ZipFile(a.aar) as zf:
        zf.extract("classes.jar", work)
    with zipfile.ZipFile(work / "classes.jar") as zf:
        zf.extractall(classes_dir)

    # 2. D8 → plugin.dex（宿主侧类不进包，运行时由父 ClassLoader 提供）
    dex_out = work / "dex"
    dex_out.mkdir(exist_ok=True)
    subprocess.run(
        [str(D8), "--release", "--min-api", "26", "--lib", str(ANDROID_JAR),
         "--output", str(dex_out)] +
        [str(p) for p in classes_dir.rglob("*.class")],
        check=True, capture_output=True
    )
    dex = dex_out / "classes.dex"
    plugin_dex = work / "plugin.dex"
    import shutil
    shutil.copy(dex, plugin_dex)  # 复制而非改名：Windows rename 不能覆盖已存在文件

    # 3. 图标
    icon = work / "icon.png"
    if a.icon_src:
        import shutil
        shutil.copy(a.icon_src, icon)
    else:
        make_default_icon(icon, int(a.tint, 16))

    # 4. 签名（有 keystore 才签；签的是 dex+icon，与宿主校验一致）
    signature = ""
    if a.keystore:
        sig = subprocess.run(
            [JAVA, str(SIGNER), a.keystore, a.storepass, a.alias, a.keypass,
             str(plugin_dex), str(icon)],
            check=True, capture_output=True, text=True
        ).stdout.strip().splitlines()[-1]
        signature = sig

    # 5. manifest + 打包
    manifest = {
        "id": a.id, "title": a.title, "summary": a.summary, "tint": a.tint,
        "version": a.version, "author": a.author, "entryClass": a.entry,
        "icon": "icon.png",
        "capabilities": [c for c in a.capabilities.split(",") if c],
        "signature": signature,
    }
    (work / "manifest.json").write_text(json.dumps(manifest, ensure_ascii=False), encoding="utf-8")
    out = Path(a.out)
    out.parent.mkdir(parents=True, exist_ok=True)
    with zipfile.ZipFile(out, "w", zipfile.ZIP_DEFLATED) as zf:
        zf.write(work / "manifest.json", "manifest.json")
        zf.write(plugin_dex, "plugin.dex")
        zf.write(icon, "icon.png")
    print(f"打包完成: {out} ({'已签名' if signature else '未签名'})")


if __name__ == "__main__":
    main()
