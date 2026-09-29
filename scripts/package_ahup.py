#!/usr/bin/env python3
"""package_ahup.py —— 把 feature 模块打成可运行期装载的 .ahup 插件包。

用法（Gradle packageAhup 任务调用）：
  python package_ahup.py <moduleDir> <moduleAar> <outFile> \
      --id campus_circle --title 校园圈子 --summary 简介 --tint 0xFF5C6BC0 \
      --version 0.1.0 --author AHUTong --entry com.xxx.CirclePlugin \
      --icon-src <可选 PNG> --keystore <可选> --storepass x --alias ahup --keypass x \
      --extra-aar <可选：如 opencv.aar，其 Java 类并入 dex、jni so 进包> \
      --abi arm64-v8a <可选：so 的 ABI 过滤，默认 arm64-v8a，多 ABI 逗号分隔>

流程：AAR classes.jar（+ 可选 extra AAR 类）→ D8 → plugin.dex →（可选）v2 原生库
→（可选）签名 → manifest.json + icon.png 打包 zip。
未提供 keystore 时产未签名包（宿主安装时会有强警告）。
签名载荷：v1 = sha256(dex+icon)；带 so 时 v2 = sha256(dex+icon+so 按相对路径排序拼接)。
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

# ABI 策略：2026 年校园设备事实标准是 arm64-v8a；armeabi-v7a 做成开关默认关闭
DEFAULT_ABI = "arm64-v8a"


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
    ap.add_argument("--extra-aar", default="",
                    help="可选第三方 AAR（如 opencv）：Java 类并入 dex，jni so 拷入包内 lib/")
    ap.add_argument("--abi", default=DEFAULT_ABI,
                    help=f"so 的 ABI 过滤，默认 {DEFAULT_ABI}；多 ABI 逗号分隔")
    a = ap.parse_args()

    work = Path(a.module_dir) / "build" / "ahup-work"
    work.mkdir(parents=True, exist_ok=True)
    import time
    import shutil

    # 1. 主 AAR 的 classes.jar 解出 class 文件（每次开新目录，不批量删旧文件）
    classes_dir = work / f"classes_{int(time.time())}"
    classes_dir.mkdir()
    with zipfile.ZipFile(a.aar) as zf:
        zf.extract("classes.jar", work)
    with zipfile.ZipFile(work / "classes.jar") as zf:
        zf.extractall(classes_dir)

    # 1b. 额外 AAR（如 OpenCV）：其 classes.jar 解出后并入 D8 输入（全量，不收缩）
    extra_dir = None
    if a.extra_aar:
        extra_dir = work / f"extra_{int(time.time())}"
        extra_dir.mkdir()
        extra_jar = work / f"extra_classes_{int(time.time())}.jar"
        with zipfile.ZipFile(a.extra_aar) as zf:
            with open(extra_jar, "wb") as f:
                f.write(zf.read("classes.jar"))
        with zipfile.ZipFile(extra_jar) as zf:
            zf.extractall(extra_dir)
        print(f"已并入 {a.extra_aar} 的 Java 类")

    # 2. D8 → plugin.dex（宿主侧类不进包，运行时由父 ClassLoader 提供）
    dex_out = work / "dex"
    dex_out.mkdir(exist_ok=True)
    class_files = [str(p) for p in classes_dir.rglob("*.class")]
    if extra_dir is not None:
        class_files += [str(p) for p in extra_dir.rglob("*.class")]
    subprocess.run(
        [str(D8), "--release", "--min-api", "26", "--lib", str(ANDROID_JAR),
         "--output", str(dex_out)] + class_files,
        check=True, capture_output=True
    )
    dex = dex_out / "classes.dex"
    plugin_dex = work / "plugin.dex"
    shutil.copy(dex, plugin_dex)  # 复制而非改名：Windows rename 不能覆盖已存在文件

    # 3. 图标
    icon = work / "icon.png"
    if a.icon_src:
        shutil.copy(a.icon_src, icon)
    else:
        make_default_icon(icon, int(a.tint, 16))

    # 3b. 原生库：从 extra AAR 的 jni/<abi>/*.so 拷入包内 lib/<abi>/（v2）
    so_files = []  # (zip 相对路径, 磁盘绝对路径)
    if a.extra_aar:
        abis = [x.strip() for x in a.abi.split(",") if x.strip()]
        lib_root = work / "lib"
        with zipfile.ZipFile(a.extra_aar) as zf:
            for name in zf.namelist():
                if not name.startswith("jni/"):
                    continue
                parts = name.split("/")
                if len(parts) != 3 or not name.endswith(".so"):
                    continue
                so_abi, so_name = parts[1], parts[2]
                if so_abi not in abis:
                    print(f"丢弃非目标 ABI 的 so：{name}（不在 --abi {a.abi}）")
                    continue
                out_path = lib_root / so_abi / so_name
                out_path.parent.mkdir(parents=True, exist_ok=True)
                with open(out_path, "wb") as f:
                    f.write(zf.read(name))
                so_files.append((f"lib/{so_abi}/{so_name}", out_path))
        so_files.sort(key=lambda x: x[0])
        if so_files:
            print(f"打包 {len(so_files)} 个原生库：{[p for p, _ in so_files]}")

    # 4. 签名（有 keystore 才签；载荷与宿主校验两端一致：v1=dex+icon，v2 再拼 so）
    signature = ""
    if a.keystore:
        cmd = [JAVA, str(SIGNER), a.keystore, a.storepass, a.alias, a.keypass,
               str(plugin_dex), str(icon)] + [str(p) for _, p in so_files]
        sig = subprocess.run(cmd, check=True, capture_output=True, text=True
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
        for rel, disk in so_files:
            zf.write(disk, rel)
    pkg_kind = "v2（含原生库）" if so_files else "v1"
    print(f"打包完成: {out} ({pkg_kind}，{'已签名' if signature else '未签名'})")


if __name__ == "__main__":
    main()
