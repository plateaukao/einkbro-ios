#!/usr/bin/env python3
"""Mechanical Android->CMP codemod for porting EinkBro Compose files.

Usage: python3 tools/port.py <file.kt> [...]
Applies in place:
  - import info.plateaukao.einkbro.R  -> CMP Res star imports
  - androidx.compose.ui.res.stringResource/painterResource -> org.jetbrains.compose.resources
  - stringResource(id = X) / painterResource(id = X) -> drop `id =`
  - R.string.x -> Res.string.x, R.drawable.x -> Res.drawable.x
  - drop @StringRes/@DrawableRes/@Keep annotations and their imports
Prints WARN lines for constructs that need manual attention.
"""
import re
import sys

RES_IMPORTS = (
    "import info.plateaukao.einkbro.resources.Res\n"
    "import info.plateaukao.einkbro.resources.*\n"
)

def port(path: str) -> None:
    with open(path) as fh:
        s = fh.read()
    orig = s

    # R import -> generated Res accessors
    s = re.sub(r"import info\.plateaukao\.einkbro\.R\n", RES_IMPORTS, s)

    # compose resource helpers move to org.jetbrains.compose.resources
    s = s.replace("import androidx.compose.ui.res.stringResource",
                  "import org.jetbrains.compose.resources.stringResource")
    s = s.replace("import androidx.compose.ui.res.painterResource",
                  "import org.jetbrains.compose.resources.painterResource")
    s = s.replace("import androidx.compose.ui.res.vectorResource",
                  "import org.jetbrains.compose.resources.vectorResource")

    # drop `id =` named arg (CMP takes the typed resource positionally)
    s = re.sub(r"\b(stringResource|painterResource|vectorResource)\(\s*id\s*=\s*", r"\1(", s)
    # ImageVector.vectorResource(X) companion form -> plain vectorResource(X)
    s = s.replace("ImageVector.vectorResource(", "vectorResource(")

    # @Preview: drop annotation + tooling import; preview fns stay as plain composables
    s = re.sub(r"import androidx\.compose\.ui\.tooling\.preview\.Preview\n", "", s)
    s = re.sub(r"@Preview(\([^)]*\))?\n", "", s)

    # resource references
    s = re.sub(r"\bR\.string\.", "Res.string.", s)
    s = re.sub(r"\bR\.drawable\.", "Res.drawable.", s)

    # androidx.annotation usages
    s = re.sub(r"import androidx\.annotation\.[A-Za-z]+\n", "", s)
    s = re.sub(r"@(StringRes|DrawableRes|Keep|ColorInt)\s*", "", s)

    # SuppressLint
    s = re.sub(r"import android\.annotation\.SuppressLint\n", "", s)
    s = re.sub(r"@SuppressLint\([^)]*\)\n?\s*", "", s)

    # LocalContext -> multiplatform stand-in
    s = s.replace("import androidx.compose.ui.platform.LocalContext",
                  "import info.plateaukao.einkbro.util.LocalContext")

    # LocalConfiguration screen metrics -> helper composables
    if "LocalConfiguration" in s:
        s = s.replace("import androidx.compose.ui.platform.LocalConfiguration",
                      "import info.plateaukao.einkbro.util.screenWidthDp\nimport info.plateaukao.einkbro.util.screenHeightDp")
        s = s.replace("LocalConfiguration.current.screenWidthDp", "screenWidthDp()")
        s = s.replace("LocalConfiguration.current.screenHeightDp", "screenHeightDp()")

    # android Bitmap -> compose ImageBitmap
    if "import android.graphics.Bitmap\n" in s:
        s = s.replace("import android.graphics.Bitmap\n",
                      "import androidx.compose.ui.graphics.ImageBitmap\n")
        s = re.sub(r"\bBitmap\b", "ImageBitmap", s)
        s = re.sub(r"(\w+)\.asImageBitmap\(\)", r"\1", s)
        s = s.replace("import androidx.compose.ui.graphics.asImageBitmap\n", "")

    # context.getString(typed res) needs the extension import
    if "context.getString(" in s or ".getString(Res.string" in s or "context.getString(\n" in s:
        if "import info.plateaukao.einkbro.util.getString" not in s:
            s = re.sub(r"(^import [^\n]+\n)", r"import info.plateaukao.einkbro.util.getString\n\1", s, count=1, flags=re.M)

    # System.currentTimeMillis -> util.System shim
    if "System.currentTimeMillis" in s and "import info.plateaukao.einkbro.util.System" not in s:
        s = re.sub(r"(^import [^\n]+\n)", r"import info.plateaukao.einkbro.util.System\n\1", s, count=1, flags=re.M)

    if s != orig:
        with open(path, "w") as fh:
            fh.write(s)
        print(f"ported: {path}")

    # things that need a human/agent decision
    for pat, msg in [
        (r"\bR\.(id|layout|raw|dimen|color|anim|style|menu|array)\.", "non-string/drawable R reference"),
        (r"ResId\w*\s*:\s*Int", "Int resource-id field: change to StringResource/DrawableResource"),
        (r"context\.getString|getString\(R\.|resources\.getString", "context string lookup: use stringResource() in composable"),
        (r"^import android\.(?!content\.(Context|SharedPreferences))", "android import"),
        (r"^import androidx\.(fragment|appcompat|activity|localbroadcastmanager|preference|print)", "android-only androidx import"),
        (r"LocalContext|LocalView|LocalConfiguration", "Local android composition local"),
    ]:
        for i, line in enumerate(s.splitlines(), 1):
            if re.search(pat, line):
                print(f"WARN {path}:{i}: {msg}: {line.strip()[:100]}")

if __name__ == "__main__":
    for p in sys.argv[1:]:
        port(p)
