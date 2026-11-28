import build123d as bd
from cad import model

FONT = "/usr/share/fonts/truetype/wqy/wqy-zenhei.ttc"


def keychain_parts(text, length=66, width=30, base_t=3.0, text_t=1.0, hole_d=5.0):
    base = bd.Box(length, width, base_t, align=(bd.Align.CENTER, bd.Align.CENTER, bd.Align.MIN))
    base = bd.fillet(base.edges().filter_by(bd.Axis.Z), radius=5)
    hole = bd.Pos(-length / 2 + 6, 0, 0) * bd.Cylinder(
        hole_d / 2, base_t, align=(bd.Align.CENTER, bd.Align.CENTER, bd.Align.MIN))
    base = base - hole
    base.label = "base"
    base.color = "#1f5f4a"

    sk = bd.Text(text, font_size=22, font_path=FONT,
                 align=(bd.Align.CENTER, bd.Align.CENTER))
    letters = bd.Pos(5, 0, base_t) * bd.extrude(sk, amount=text_t)
    letters.label = "letters"
    letters.color = "#f2f2f2"
    return bd.Compound(children=[base, letters], label="keychain")


@model
def tuzhi_keychain():
    return keychain_parts("圖知")


if __name__ == "__main__":
    tuzhi_keychain()
