import math
import os
from PIL import Image, ImageDraw, ImageFilter

def create_brand_icon(size=512, is_round=False, foreground_only=False):
    scale = 4
    canvas_size = size * scale
    img = Image.new("RGBA", (canvas_size, canvas_size), (0, 0, 0, 0))
    draw = ImageDraw.Draw(img)

    cx, cy = canvas_size / 2, canvas_size / 2

    if not foreground_only:
        # Background
        bg_radius = int(canvas_size * 0.22) if not is_round else int(canvas_size * 0.48)
        padding = int(canvas_size * 0.04)
        
        # Subtle glow underneath
        glow = Image.new("RGBA", (canvas_size, canvas_size), (0, 0, 0, 0))
        glow_draw = ImageDraw.Draw(glow)
        glow_draw.ellipse(
            [cx - canvas_size * 0.42, cy - canvas_size * 0.42, cx + canvas_size * 0.42, cy + canvas_size * 0.42],
            fill=(0, 229, 255, 60)
        )
        glow = glow.filter(ImageFilter.GaussianBlur(radius=int(canvas_size * 0.08)))
        img = Image.alpha_composite(img, glow)
        draw = ImageDraw.Draw(img)

        # Draw rounded rectangle / circle background with gradient
        # Deep space gradient from top-left (14, 23, 42) to bottom-right (8, 12, 22)
        bg_layer = Image.new("RGBA", (canvas_size, canvas_size), (0, 0, 0, 0))
        bg_draw = ImageDraw.Draw(bg_layer)

        if is_round:
            bg_draw.ellipse(
                [padding, padding, canvas_size - padding, canvas_size - padding],
                fill=(13, 17, 28, 255)
            )
            # Gradient overlay
            for i in range(int(canvas_size * 0.9)):
                alpha = int(180 * (1 - i / (canvas_size * 0.9)))
                color = (20, 32, 60, alpha)
                bg_draw.ellipse(
                    [padding + i * 0.4, padding + i * 0.4, canvas_size - padding - i * 0.4, canvas_size - padding - i * 0.4],
                    outline=color, width=2
                )
            # Border ring
            bg_draw.ellipse(
                [padding, padding, canvas_size - padding, canvas_size - padding],
                outline=(0, 210, 255, 200), width=int(canvas_size * 0.02)
            )
        else:
            bg_box = [padding, padding, canvas_size - padding, canvas_size - padding]
            bg_draw.rounded_rectangle(bg_box, radius=bg_radius, fill=(13, 17, 28, 255))
            # Subtle gradient lines
            for i in range(int(canvas_size * 0.4)):
                alpha = int(120 * (1 - i / (canvas_size * 0.4)))
                color = (25, 45, 80, alpha)
                box = [padding + i, padding + i, canvas_size - padding - i, canvas_size - padding - i]
                bg_draw.rounded_rectangle(box, radius=max(2, bg_radius - i), outline=color, width=2)
            # Border
            bg_draw.rounded_rectangle(bg_box, radius=bg_radius, outline=(0, 210, 255, 200), width=int(canvas_size * 0.02))

        img = Image.alpha_composite(img, bg_layer)
        draw = ImageDraw.Draw(img)

    # Mouse & Wireless Signal Graphic
    # Mouse body dimensions
    mouse_w = canvas_size * 0.38
    mouse_h = canvas_size * 0.52
    mouse_x0 = cx - mouse_w / 2
    mouse_y0 = cy - mouse_h / 2 + canvas_size * 0.05
    mouse_x1 = mouse_x0 + mouse_w
    mouse_y1 = mouse_y0 + mouse_h
    corner_r = mouse_w * 0.45

    # Mouse Body with sleek electric gradient
    body_layer = Image.new("RGBA", (canvas_size, canvas_size), (0, 0, 0, 0))
    b_draw = ImageDraw.Draw(body_layer)

    # Outer mouse shadow / glow
    b_draw.rounded_rectangle(
        [mouse_x0, mouse_y0, mouse_x1, mouse_y1],
        radius=int(corner_r),
        fill=(0, 220, 255, 40)
    )
    body_layer = body_layer.filter(ImageFilter.GaussianBlur(radius=int(canvas_size * 0.03)))
    img = Image.alpha_composite(img, body_layer)
    draw = ImageDraw.Draw(img)

    # Mouse Main Body
    draw.rounded_rectangle(
        [mouse_x0, mouse_y0, mouse_x1, mouse_y1],
        radius=int(corner_r),
        fill=(22, 33, 54, 255),
        outline=(0, 220, 255, 240),
        width=int(canvas_size * 0.022)
    )

    # Mouse Top Split (Left/Right button divider)
    split_y_end = mouse_y0 + mouse_h * 0.42
    draw.line(
        [(cx, mouse_y0 + canvas_size * 0.015), (cx, split_y_end)],
        fill=(0, 220, 255, 200),
        width=int(canvas_size * 0.018)
    )

    # Mouse Scroll Wheel (illuminated cyan pill)
    wheel_w = mouse_w * 0.16
    wheel_h = mouse_h * 0.18
    wheel_x0 = cx - wheel_w / 2
    wheel_y0 = mouse_y0 + mouse_h * 0.12
    wheel_x1 = wheel_x0 + wheel_w
    wheel_y1 = wheel_y0 + wheel_h

    draw.rounded_rectangle(
        [wheel_x0, wheel_y0, wheel_x1, wheel_y1],
        radius=int(wheel_w * 0.45),
        fill=(0, 242, 254, 255),
        outline=(255, 255, 255, 255),
        width=int(canvas_size * 0.008)
    )

    # Wireless signal waves above mouse
    wave_center_x = cx
    wave_center_y = mouse_y0 - canvas_size * 0.02
    wave_color = (0, 229, 255, 255)

    # Wave 1 (inner)
    r1 = canvas_size * 0.12
    draw.arc(
        [wave_center_x - r1, wave_center_y - r1, wave_center_x + r1, wave_center_y + r1],
        start=220, end=320,
        fill=wave_color,
        width=int(canvas_size * 0.022)
    )

    # Wave 2 (middle)
    r2 = canvas_size * 0.20
    draw.arc(
        [wave_center_x - r2, wave_center_y - r2, wave_center_x + r2, wave_center_y + r2],
        start=225, end=315,
        fill=(0, 200, 255, 220),
        width=int(canvas_size * 0.024)
    )

    # Wave 3 (outer)
    r3 = canvas_size * 0.28
    draw.arc(
        [wave_center_x - r3, wave_center_y - r3, wave_center_x + r3, wave_center_y + r3],
        start=230, end=310,
        fill=(0, 180, 255, 170),
        width=int(canvas_size * 0.026)
    )

    # Small accent dot / LED on palm area
    dot_r = canvas_size * 0.015
    dot_y = mouse_y1 - mouse_h * 0.22
    draw.ellipse(
        [cx - dot_r, dot_y - dot_r, cx + dot_r, dot_y + dot_r],
        fill=(0, 242, 254, 230)
    )

    # Downsample with Lanczos for super-smooth anti-aliasing
    final_img = img.resize((size, size), Image.Resampling.LANCZOS)
    return final_img

def save_xpm(image, filepath, varname="icon"):
    img = image.convert("RGBA").resize((32, 32), Image.Resampling.LANCZOS)
    pixels = list(img.getdata())
    chars = " .XoO+#$%&*=-;:>,<1234567890abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ"
    
    color_map = {}
    counter = 0
    for p in pixels:
        r, g, b, a = p
        hex_c = "None" if a < 128 else f"#{r:02X}{g:02X}{b:02X}"
        if hex_c not in color_map:
            c1 = chars[(counter // len(chars)) % len(chars)]
            c2 = chars[counter % len(chars)]
            code = f"{c1}{c2}"
            counter += 1
            color_map[hex_c] = code

    w, h = img.size
    with open(filepath, "w", encoding="utf-8") as f:
        f.write(f"static const char *{varname}[] = {{\n")
        f.write(f'"{w} {h} {len(color_map)} 2 ",\n')
        for c, code in color_map.items():
            f.write(f'"{code} c {c}",\n')
        for y in range(h):
            row = ""
            for x in range(w):
                p = img.getpixel((x, y))
                r, g, b, a = p
                hex_c = "None" if a < 128 else f"#{r:02X}{g:02X}{b:02X}"
                row += color_map[hex_c]
            comma = "," if y < h - 1 else ""
            f.write(f'"{row}"{comma}\n')
        f.write("};\n")

def main():
    root = "j:/Mousedroid"
    print("Generating branding icons...")

    # 1. Master 512x512 logo
    logo_512 = create_brand_icon(512, is_round=False)
    logo_512.save(os.path.join(root, "imgs/logo.png"))
    logo_512.save(os.path.join(root, "client/app/src/main/res/drawable/logo.png"))
    logo_512.save(os.path.join(root, "server/icon.png"))

    # 2. Android mipmaps
    mipmaps = {
        "mipmap-mdpi": 48,
        "mipmap-hdpi": 72,
        "mipmap-xhdpi": 96,
        "mipmap-xxhdpi": 144,
        "mipmap-xxxhdpi": 192
    }

    res_dir = os.path.join(root, "client/app/src/main/res")
    for folder, px in mipmaps.items():
        dest = os.path.join(res_dir, folder)
        os.makedirs(dest, exist_ok=True)
        # Square icon
        sq = create_brand_icon(px, is_round=False)
        sq.save(os.path.join(dest, "ic_launcher.png"))

        # Round icon
        rd = create_brand_icon(px, is_round=True)
        rd.save(os.path.join(dest, "ic_launcher_round.png"))

        # Foreground icon (adaptive)
        fg = create_brand_icon(px, is_round=False, foreground_only=True)
        fg.save(os.path.join(dest, "ic_launcher_foreground.png"))

    # 3. Windows ICO for PC Server
    ico_sizes = [(16, 16), (24, 24), (32, 32), (48, 48), (64, 64), (128, 128), (256, 256)]
    ico_images = [create_brand_icon(s[0], is_round=False) for s in ico_sizes]
    ico_path = os.path.join(root, "server/app.ico")
    ico_images[0].save(ico_path, format="ICO", sizes=ico_sizes, append_images=ico_images[1:])

    # 4. Generate XPM for wxWidgets
    icon_32 = create_brand_icon(32, is_round=False)
    xpm_path = os.path.join(root, "server/res/icon.xpm")
    save_xpm(icon_32, xpm_path, "icon")

    print("Brand assets successfully generated!")

if __name__ == "__main__":
    main()
