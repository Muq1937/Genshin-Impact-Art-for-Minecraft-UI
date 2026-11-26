"""
Wallpaper Engine scene.pkg / .tex extractor.

Pulls the image layers out of a Wallpaper Engine "scene" wallpaper so they can be
used as source art for Genshin UI themes.

Usage:
    python tools/we_extract.py <workshop_item_dir_or_scene.pkg> <out_dir> [--min-size 512]

Writes every texture as PNG (largest mip only). Needs Pillow; LZ4 is decoded in pure Python.
Only for personal use of wallpapers you own / subscribed to.
"""
import io
import os
import struct
import sys

from PIL import Image


# ---------------------------------------------------------------- LZ4 (block format)
def lz4_block_decompress(src: bytes, out_size: int) -> bytes:
    dst = bytearray()
    i = 0
    n = len(src)
    while i < n:
        token = src[i]
        i += 1
        lit = token >> 4
        if lit == 15:
            while True:
                b = src[i]
                i += 1
                lit += b
                if b != 255:
                    break
        dst += src[i:i + lit]
        i += lit
        if i >= n:
            break
        offset = src[i] | (src[i + 1] << 8)
        i += 2
        mlen = token & 15
        if mlen == 15:
            while True:
                b = src[i]
                i += 1
                mlen += b
                if b != 255:
                    break
        mlen += 4
        start = len(dst) - offset
        if offset >= mlen:
            dst += dst[start:start + mlen]
        else:  # overlapping copy
            for k in range(mlen):
                dst.append(dst[start + k])
    if len(dst) != out_size:
        raise ValueError(f"lz4 size mismatch {len(dst)} != {out_size}")
    return bytes(dst)


# ---------------------------------------------------------------- PKG
def read_pkg(path):
    with open(path, "rb") as f:
        data = f.read()
    p = 0

    def u32():
        nonlocal p
        v = struct.unpack_from("<I", data, p)[0]
        p += 4
        return v

    def string():
        nonlocal p
        ln = u32()
        s = data[p:p + ln].decode("utf-8", "replace")
        p += ln
        return s

    magic = string()
    if not magic.startswith("PKGV"):
        raise ValueError("not a PKG file: " + magic)
    count = u32()
    entries = []
    for _ in range(count):
        name = string()
        off = u32()
        size = u32()
        entries.append((name, off, size))
    base = p
    for name, off, size in entries:
        yield name, data[base + off: base + off + size]


# ---------------------------------------------------------------- TEX
FMT_RGBA8888, FMT_DXT5, FMT_DXT3, FMT_DXT1, FMT_RG88, FMT_R8 = 0, 4, 6, 7, 8, 9


def read_cstr(data, p):
    e = data.index(b"\0", p)
    return data[p:e].decode("ascii", "replace"), e + 1


def decode_tex(data: bytes):
    p = 0
    m1, p = read_cstr(data, p)
    m2, p = read_cstr(data, p)
    if not m1.startswith("TEXV") or not m2.startswith("TEXI"):
        raise ValueError("not a TEX file")
    fmt, flags, tex_w, tex_h, img_w, img_h, _unk = struct.unpack_from("<iiiiiiI", data, p)
    p += 28
    cont, p = read_cstr(data, p)
    image_count = struct.unpack_from("<i", data, p)[0]
    p += 4
    cver = int(cont[4:])
    free_fmt = -1
    if cver >= 3:
        free_fmt = struct.unpack_from("<i", data, p)[0]
        p += 4
    if cver >= 4:
        is_mp4 = struct.unpack_from("<i", data, p)[0]
        p += 4
        if is_mp4:
            raise ValueError("video texture")

    images = []
    for _ in range(image_count):
        mip_count = struct.unpack_from("<i", data, p)[0]
        p += 4
        mips = []
        for _m in range(mip_count):
            mw, mh = struct.unpack_from("<ii", data, p)
            p += 8
            compressed, dsize = 0, 0
            if cver >= 2:
                compressed, dsize = struct.unpack_from("<ii", data, p)
                p += 8
            blen = struct.unpack_from("<i", data, p)[0]
            p += 4
            raw = data[p:p + blen]
            p += blen
            mips.append((mw, mh, compressed, dsize, raw))
        images.append(mips)

    out = []
    for mips in images:
        mw, mh, compressed, dsize, raw = mips[0]  # largest mip
        if compressed:
            raw = lz4_block_decompress(raw, dsize)
        if free_fmt != -1:
            im = Image.open(io.BytesIO(raw))
            im.load()
        elif fmt == FMT_RGBA8888:
            im = Image.frombytes("RGBA", (mw, mh), raw)
        elif fmt in (FMT_DXT1, FMT_DXT3, FMT_DXT5):
            n = {FMT_DXT1: 1, FMT_DXT3: 2, FMT_DXT5: 3}[fmt]
            im = Image.frombytes("RGBA", (mw, mh), raw, "bcn", n)
        elif fmt == FMT_RG88:
            im = Image.frombytes("LA", (mw, mh), raw)
        elif fmt == FMT_R8:
            im = Image.frombytes("L", (mw, mh), raw)
        else:
            raise ValueError(f"unsupported format {fmt}")
        # crop away power-of-two padding
        if img_w and img_h and (img_w <= im.width and img_h <= im.height):
            im = im.crop((0, 0, img_w, img_h))
        out.append(im)
    return out


def main():
    if len(sys.argv) < 3:
        print(__doc__)
        sys.exit(1)
    src, out_dir = sys.argv[1], sys.argv[2]
    min_size = 512
    if "--min-size" in sys.argv:
        min_size = int(sys.argv[sys.argv.index("--min-size") + 1])
    if os.path.isdir(src):
        src = os.path.join(src, "scene.pkg")
    os.makedirs(out_dir, exist_ok=True)
    for name, blob in read_pkg(src):
        if not name.endswith(".tex"):
            continue
        try:
            ims = decode_tex(blob)
        except Exception as e:  # noqa: BLE001
            print(f"skip {name}: {e}")
            continue
        for idx, im in enumerate(ims):
            if max(im.size) < min_size:
                continue
            safe = name.replace("/", "__").replace("\\", "__")[:-4]
            fn = os.path.join(out_dir, f"{safe}{'_' + str(idx) if idx else ''}.png")
            im.save(fn)
            print(f"{im.size[0]}x{im.size[1]} {fn}")


if __name__ == "__main__":
    main()
