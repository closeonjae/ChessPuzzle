"""Cuts the 12 piece bitmaps used by the app out of the user-provided LS/DS
reference PNGs (DESIGN.md 2절 "구현 중 수정 2"). Each LS/DS PNG is a piece
already flattened onto its square's solid background color, with no
transparency — this script keys the piece out into its own alpha-masked
PNG so it can be drawn (and later animated) independently of the square
underneath it.

Run from the repo root:  python tools/extract_pieces.py
Writes into app/src/main/res/drawable-nodpi/piece_{w|b}{p|n|b|r|q|k}.png,
overwriting the checked-in files there — re-run only if LS/DS change.
"""
import math
import os
from collections import deque
from PIL import Image, ImageFilter

REPO_ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OUT_DIR = os.path.join(REPO_ROOT, "app", "src", "main", "res", "drawable-nodpi")

# Which square-shade folder to key each piece color out of: whichever
# background contrasts more with that piece's own fill gives the cleanest
# alpha edges (pale LS bg vs. dark black-piece fills; blue DS bg vs.
# light/white-piece fills).
SOURCES = {"w": "DS", "b": "LS"}
PIECES = ["P", "N", "B", "R", "Q", "K"]
LOW, EXT_HIGH = 16.0, 70.0  # exterior distance-from-background alpha ramp, in RGB units
WALL_LUM_RATIO = 0.6  # a pixel darker than this fraction of the bg's own luminance is "wall" (outline)
ALPHA_FLOOR = 0.15  # below this, don't trust a resize-introduced alpha — see resize_straight_alpha()
PAD = 3  # px kept around the trimmed bounding box
OUTLINE_THICKEN = 0.3  # 0=original stroke weight, 1=full 1px dilation (MinFilter(3))
# Final downscale applied to every sprite (same factor, so relative piece
# sizes are preserved) — bakes proper filtered anti-aliasing into the
# asset itself, close to the ~30-40px the pieces actually render at on a
# watch face. Left at native ~90-100px, the outline was visibly staircased
# on-device: GPU bilinear minification only samples a 2×2 texel window, so
# a >2x runtime downscale aliases fine strokes no matter what FilterQuality
# is requested at draw time (confirmed empirically — PieceIcons.kt still
# asks for FilterQuality.High as a cheap defensive extra, but the fix that
# actually mattered is here).
OUTPUT_SCALE = 0.5


def resize_straight_alpha(img, scale):
    """Resize an RGBA image with straight (non-premultiplied) alpha using a
    high-quality filter, without the dark fringing a naive resize causes:
    premultiply first so fully-transparent neighbors (which we set to
    (0,0,0,0)) don't bleed black into semi-transparent edge pixels."""
    w, h = img.size
    new_size = (max(1, round(w * scale)), max(1, round(h * scale)))
    px = img.load()
    pre = Image.new("RGBA", img.size)
    pre_px = pre.load()
    for y in range(h):
        for x in range(w):
            pr, pg, pb, pa = px[x, y]
            pre_px[x, y] = (pr * pa // 255, pg * pa // 255, pb * pa // 255, pa)
    small = pre.resize(new_size, Image.LANCZOS)
    small_px = small.load()
    out = Image.new("RGBA", new_size)
    out_px = out.load()
    alpha_floor_255 = round(ALPHA_FLOOR * 255)
    for y in range(new_size[1]):
        for x in range(new_size[0]):
            pr, pg, pb, pa = small_px[x, y]
            # Defensive backstop: LANCZOS has negative side lobes, so a
            # hard alpha edge can in principle ring a stray low-but-nonzero
            # alpha into existence right past the boundary. Now that
            # extract() fixes every partial-alpha source pixel's color at
            # black (premultiplied color is always exactly 0 there, so
            # un-premultiplying can't manufacture a stray bright color the
            # way it used to), this floor mostly just guards against
            # rounding noise rather than doing the real work.
            if pa < alpha_floor_255:
                out_px[x, y] = (0, 0, 0, 0)
            else:
                out_px[x, y] = (min(255, pr * 255 // pa), min(255, pg * 255 // pa), min(255, pb * 255 // pa), pa)
    return out


def bg_color(im):
    w, h = im.size
    corners = [im.getpixel((0, 0)), im.getpixel((w - 1, 0)), im.getpixel((0, h - 1)), im.getpixel((w - 1, h - 1))]
    return tuple(sum(c[i] for c in corners) / 4 for i in range(3))


def flood_fill_exterior(w, h, is_wall):
    """BFS from the canvas border through every non-wall pixel. A pixel the
    flood fill never reaches is topologically *enclosed* by the wall (the
    piece's own outline, which runs unbroken around its whole silhouette)
    — i.e. real interior content. Everything reached is "outside": either
    true background or an anti-aliased blend pixel sitting between the
    background and the wall, however light or dark that blend happens to
    land — this is what a pure color-distance threshold couldn't tell
    apart from genuine (e.g. white-piece) fill color."""
    reached = [[False] * w for _ in range(h)]
    q = deque()

    def seed(x, y):
        if not is_wall[y][x] and not reached[y][x]:
            reached[y][x] = True
            q.append((x, y))

    for x in range(w):
        seed(x, 0)
        seed(x, h - 1)
    for y in range(h):
        seed(0, y)
        seed(w - 1, y)
    while q:
        x, y = q.popleft()
        for nx, ny in ((x + 1, y), (x - 1, y), (x, y + 1), (x, y - 1)):
            if 0 <= nx < w and 0 <= ny < h and not is_wall[ny][nx] and not reached[ny][nx]:
                reached[ny][nx] = True
                q.append((nx, ny))
    return reached


def extract(path, out_path):
    im = Image.open(path).convert("RGB")
    # Grow the piece's own dark outline/detail lines by a fraction of a
    # pixel (partial blend with a 1px min-filter dilation) — a request to
    # make the stroke "just slightly" bolder, not visibly heavier.
    im = Image.blend(im, im.filter(ImageFilter.MinFilter(3)), OUTLINE_THICKEN)
    w, h = im.size
    bg = bg_color(im)
    bg_lum = 0.299 * bg[0] + 0.587 * bg[1] + 0.114 * bg[2]
    src = im.load()

    is_wall = [[False] * w for _ in range(h)]
    for y in range(h):
        for x in range(w):
            r, g, b = src[x, y]
            lum = 0.299 * r + 0.587 * g + 0.114 * b
            is_wall[y][x] = lum < bg_lum * WALL_LUM_RATIO
    exterior = flood_fill_exterior(w, h, is_wall)

    out = Image.new("RGBA", (w, h))
    dst = out.load()
    for y in range(h):
        for x in range(w):
            r, g, b = src[x, y]
            if not exterior[y][x]:
                # Enclosed by the outline — real interior content (the
                # outline itself, or fill), never touches the background.
                dst[x, y] = (r, g, b, 255)
                continue
            d = math.sqrt((r - bg[0]) ** 2 + (g - bg[1]) ** 2 + (b - bg[2]) ** 2)
            if d <= LOW:
                dst[x, y] = (0, 0, 0, 0)
            else:
                # Exterior anti-aliased rim: whatever color this blend
                # pixel happens to land on (it can drift far from a simple
                # background/outline mix — hence needing the flood fill
                # above rather than a second color-distance threshold), it
                # is a blend *of the background with the outline*, so the
                # true color is ~black. Recovering the "real" blended
                # color by dividing by a near-zero alpha was numerically
                # unstable and left a white/gray halo outside the piece —
                # fix the color at black and vary only alpha instead.
                alpha = min(1.0, (d - LOW) / (EXT_HIGH - LOW))
                dst[x, y] = (0, 0, 0, round(alpha * 255))

    bbox = out.getbbox()
    if bbox is None:
        raise RuntimeError(f"empty extraction for {path}")
    l, t, r2, b2 = bbox
    l = max(0, l - PAD)
    t = max(0, t - PAD)
    r2 = min(w, r2 + PAD)
    b2 = min(h, b2 + PAD)
    cropped = out.crop((l, t, r2, b2))
    final = resize_straight_alpha(cropped, OUTPUT_SCALE)
    final.save(out_path)
    print(out_path, "size", final.size)


if __name__ == "__main__":
    os.makedirs(OUT_DIR, exist_ok=True)
    for color, folder in SOURCES.items():
        for piece in PIECES:
            src_path = os.path.join(REPO_ROOT, folder, f"{color.upper()}{piece}.png")
            out_path = os.path.join(OUT_DIR, f"piece_{color}{piece.lower()}.png")
            extract(src_path, out_path)
