"""把狂猎棺材的原图做成 64x64 物品贴图（抠黑底 + 提亮 + 等比缩放）。"""
from PIL import Image
import numpy as np

SRC = r"C:\Users\Administrator\.dsh\attachments\v1\objects\16\16d8b600be8968b445bcf9f936e34c01d707c0ef7ca636bb8004a1ee9c1dd16d"
DST = r"C:\Users\Administrator\WorkBuddy\2026-10-18-00-19-23\caduceus-mod\src\main\resources\assets\tenfold\textures\item\wild_hunt_coffin.png"

img = Image.open(SRC).convert("RGB")
arr = np.asarray(img).astype(np.float32)
print("源尺寸:", img.size)

lum = arr.max(axis=2)
# 背景几乎纯黑（0~10），棺材本体 25~60，锁链 60~140。
# 用亮度映射出不透明度：纯黑全透明，本体基本实心，锁链最亮。
alpha = np.clip((lum - 6.0) * 9.0, 0.0, 255.0)
# 提升颜色亮度，避免物品栏里一团黑
rgb = np.clip(arr * 2.1 + 18.0, 0.0, 255.0)

rgba = np.zeros((arr.shape[0], arr.shape[1], 4), dtype=np.uint8)
rgba[:, :, :3] = rgb.astype(np.uint8)
rgba[:, :, 3] = alpha.astype(np.uint8)

out = Image.fromarray(rgba, mode="RGBA")

# 裁掉四周全透明的空白（保留 1px 边距），再等比缩放到 64x64
bbox = out.getbbox()
print("内容包围盒:", bbox)
out = out.crop(bbox)

w, h = out.size
scale = 64.0 / max(w, h)
new_size = (max(1, int(round(w * scale))), max(1, int(round(h * scale))))
out = out.resize(new_size, Image.LANCZOS)

canvas = Image.new("RGBA", (64, 64), (0, 0, 0, 0))
canvas.paste(out, ((64 - new_size[0]) // 2, (64 - new_size[1]) // 2))
canvas.save(DST)

chk = np.asarray(canvas)
print("输出尺寸:", canvas.size, " 不透明像素:", int((chk[:, :, 3] > 20).sum()))
print("alpha 分布: >200 =", int((chk[:, :, 3] > 200).sum()),
      " 100~200 =", int(((chk[:, :, 3] > 100) & (chk[:, :, 3] <= 200)).sum()),
      " 20~100 =", int(((chk[:, :, 3] > 20) & (chk[:, :, 3] <= 100)).sum()))
