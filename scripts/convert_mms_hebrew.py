#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Convert Meta's MMS Hebrew VITS voice to a sherpa-onnx model for KoSpeaker.

This script downloads ``facebook/mms-tts`` Hebrew (``heb``) weights and converts
them into the two files sherpa-onnx (v1.13.0, as bundled in KoSpeaker) needs:

    model.onnx    - the exported VITS acoustic+vocoder graph
    tokens.txt    - the character->id table (MMS uses its OWN characters,
                    NO espeak, NO niqqud, NO lexicon)

It follows the OFFICIAL sherpa-onnx MMS procedure documented at:

    https://k2-fsa.github.io/sherpa/onnx/tts/mms.html
    (script: sherpa-onnx `scripts/mms/vits-mms.py`)

The only adaptation is `lang=heb` (instead of `eng`) and packaging the manual
shell steps (download / clone / build monotonic_align / export) into one script.

Why MMS and not Piper for Hebrew:
    MMS-heb is a self-contained VITS that tokenises raw Hebrew characters. It
    needs no espeak phonemizer and no lexicon, so it runs fully offline with the
    sherpa "character" frontend. In KoSpeaker install it as model type "MMS"
    (stored as "vits-mms") so the engine loads it with an EMPTY dataDir and
    EMPTY lexicon -- that is what selects the character frontend. If dataDir is
    left pointing at espeak-ng-data, sherpa picks the espeak frontend and MMS
    output becomes garbage.

Metadata written into model.onnx (matches the official export exactly):
    model_type = "vits", frontend = "characters", comment = "mms",
    language, add_blank, n_speakers, sample_rate (16000 for MMS).

--------------------------------------------------------------------------------
REQUIREMENTS
--------------------------------------------------------------------------------
Python 3.8-3.10 recommended (the upstream `vits` package predates newer numpy).
CPU is fine; no GPU needed.

    pip install "torch==1.13.1" onnx scipy Cython "numpy<2" requests
    #                ^ any recent CPU torch works; 1.13.x matches upstream docs.
    # `git` must be on PATH (used to clone the upstream VITS package).

--------------------------------------------------------------------------------
USAGE
--------------------------------------------------------------------------------
    python3 scripts/convert_mms_hebrew.py                 # -> ./mms-heb/
    python3 scripts/convert_mms_hebrew.py --out ~/mms-heb # custom output dir
    python3 scripts/convert_mms_hebrew.py --lang heb      # other MMS langs too

Outputs (in the chosen --out directory, default ./mms-heb):
    model.onnx     copy to the device, select as "model.onnx" during sideload
    tokens.txt     copy to the device, select as "tokens.txt" during sideload

Then in KoSpeaker: Manage Languages -> Install from SD -> code "heb",
name "Hebrew (MMS)", model type "MMS". See docs/HEBREW.md for the full guide.

--------------------------------------------------------------------------------
LICENSE NOTE
--------------------------------------------------------------------------------
The MMS voices are released by Meta under CC-BY-NC 4.0 (non-commercial). Fine for
personal offline reading; do NOT redistribute the generated model commercially.
"""

import argparse
import collections
import os
import subprocess
import sys
from pathlib import Path
from typing import Any, Dict, List

# Upstream VITS package (mms-meta/MMS HF Space) - cloned on demand. It provides
# the `vits` python package (models.SynthesizerTrn, utils, commons) that the
# checkpoint was trained with; the HF `transformers` VitsModel is a *different*
# re-implementation and is intentionally not used, to stay byte-faithful to the
# official sherpa export.
MMS_REPO = "https://huggingface.co/spaces/mms-meta/MMS"

# Per-language weight files live under facebook/mms-tts/models/<lang>/.
HF_BASE = "https://huggingface.co/facebook/mms-tts/resolve/main/models"
WEIGHT_FILES = ("G_100000.pth", "config.json", "vocab.txt")


def run(cmd: List[str], cwd: Path = None) -> None:
    print("+", " ".join(str(c) for c in cmd))
    subprocess.check_call(cmd, cwd=str(cwd) if cwd else None)


def download(url: str, dest: Path) -> None:
    if dest.exists() and dest.stat().st_size > 0:
        print(f"  cached {dest.name}")
        return
    import requests

    print(f"  downloading {url}")
    with requests.get(url, stream=True, timeout=120) as r:
        r.raise_for_status()
        with open(dest, "wb") as f:
            for chunk in r.iter_content(chunk_size=1 << 16):
                f.write(chunk)


def fetch_weights(lang: str, work: Path) -> None:
    for name in WEIGHT_FILES:
        download(f"{HF_BASE}/{lang}/{name}", work / name)


def prepare_vits_package(work: Path) -> Path:
    """Clone the upstream VITS package and build its monotonic_align extension."""
    mms_dir = work / "MMS"
    if not mms_dir.exists():
        run(["git", "clone", "--depth", "1", MMS_REPO, str(mms_dir)])

    # Build the Cython monotonic_align core (required to import vits.models).
    ma = mms_dir / "vits" / "monotonic_align"
    if not list(ma.glob("core*.so")):
        run([sys.executable, "setup.py", "build"], cwd=ma)
        built = list((ma / "build").glob("lib*/vits/monotonic_align/core*.so"))
        if not built:
            built = list((ma / "build").glob("lib*/**/core*.so"))
        if not built:
            raise RuntimeError("Failed to build monotonic_align core*.so")
        (ma / built[0].name).write_bytes(built[0].read_bytes())
        # Upstream __init__ imports `.monotonic_align.core`; the copied .so is
        # at `.core`. Rewrite the import to match (mirrors the doc's sed step).
        init = ma / "__init__.py"
        init.write_text(
            init.read_text().replace(".monotonic_align.core", ".core"),
            encoding="utf-8",
        )

    # Make `import vits` resolve to the cloned package.
    sys.path.insert(0, str(mms_dir))
    sys.path.insert(0, str(mms_dir / "vits"))
    return mms_dir


# --- Export logic (verbatim behaviour from official scripts/mms/vits-mms.py) ---


def load_vocab(vocab_path: Path) -> List[str]:
    return [x.replace("\n", "") for x in open(vocab_path, encoding="utf-8").readlines()]


def generate_tokens(symbols: List[str], out_path: Path) -> None:
    """Write tokens.txt: `<token> <id>` per line.

    An extra line maps the UPPER-CASE form to the same id when it is a distinct
    single character and not ambiguous -- identical to the official script.
    """
    all_upper = [s.upper() for s in symbols]
    duplicate = {
        item
        for item, count in collections.Counter(all_upper).items()
        if count > 1
    }
    with open(out_path, "w", encoding="utf-8") as f:
        for idx, token in enumerate(symbols):
            f.write(f"{token} {idx}\n")
            if (
                token.lower() != token.upper()
                and len(token.upper()) == 1
                and token.upper() not in duplicate
            ):
                f.write(f"{token.upper()} {idx}\n")
    print(f"  wrote {out_path} ({len(symbols)} symbols)")


def add_meta_data(filename: Path, meta_data: Dict[str, Any]) -> None:
    import onnx

    model = onnx.load(str(filename))
    for key, value in meta_data.items():
        meta = model.metadata_props.add()
        meta.key = key
        meta.value = str(value)
    onnx.save(model, str(filename))


def export(lang: str, work: Path, out_dir: Path) -> None:
    import torch
    from vits import utils
    from vits.models import SynthesizerTrn

    class OnnxModel(torch.nn.Module):
        def __init__(self, model: "SynthesizerTrn"):
            super().__init__()
            self.model = model

        def forward(self, x, x_lengths, noise_scale=0.667, length_scale=1.0,
                    noise_scale_w=0.8):
            return self.model.infer(
                x=x,
                x_lengths=x_lengths,
                noise_scale=noise_scale,
                length_scale=length_scale,
                noise_scale_w=noise_scale_w,
            )[0]

    hps = utils.get_hparams_from_file(str(work / "config.json"))
    if hps.data.training_files.split(".")[-1] == "uroman":
        raise ValueError(
            "This MMS language requires uroman romanization, which the character "
            "frontend does not support. Hebrew (heb) does NOT use uroman."
        )

    symbols = load_vocab(work / "vocab.txt")
    generate_tokens(symbols, out_dir / "tokens.txt")

    net_g = SynthesizerTrn(
        len(symbols),
        hps.data.filter_length // 2 + 1,
        hps.train.segment_size // hps.data.hop_length,
        **hps.model,
    )
    net_g.cpu()
    net_g.eval()
    utils.load_checkpoint(str(work / "G_100000.pth"), net_g, None)

    model = OnnxModel(net_g)

    # Dummy inputs only trace the graph shapes; sherpa feeds real values at run
    # time (noiseScale=0.667, noiseScaleW=0.8, lengthScale=1.0 by default).
    x = torch.randint(low=1, high=10, size=(50,), dtype=torch.int64).unsqueeze(0)
    x_length = torch.tensor([x.shape[1]], dtype=torch.int64)
    noise_scale = torch.tensor([1], dtype=torch.float32)
    length_scale = torch.tensor([1], dtype=torch.float32)
    noise_scale_w = torch.tensor([1], dtype=torch.float32)

    model_path = out_dir / "model.onnx"
    torch.onnx.export(
        model,
        (x, x_length, noise_scale, length_scale, noise_scale_w),
        str(model_path),
        opset_version=13,
        input_names=["x", "x_length", "noise_scale", "length_scale", "noise_scale_w"],
        output_names=["y"],
        dynamic_axes={
            "x": {0: "N", 1: "L"},
            "x_length": {0: "N"},
            "y": {0: "N", 2: "L"},
        },
    )

    meta_data = {
        "model_type": "vits",
        "comment": "mms",
        "url": "https://huggingface.co/facebook/mms-tts/tree/main",
        "add_blank": int(hps.data.add_blank),
        "language": lang,
        "frontend": "characters",
        "n_speakers": int(hps.data.n_speakers),
        "sample_rate": hps.data.sampling_rate,
    }
    print("  meta_data:", meta_data)
    add_meta_data(model_path, meta_data)
    print(f"  wrote {model_path}")


def main() -> None:
    ap = argparse.ArgumentParser(description=__doc__,
                                 formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--lang", default="heb", help="MMS language code (default: heb)")
    ap.add_argument("--out", default="./mms-heb", help="output dir (default: ./mms-heb)")
    ap.add_argument("--work", default=None,
                    help="scratch dir for downloads/clone (default: <out>/_work)")
    args = ap.parse_args()

    out_dir = Path(args.out).expanduser().resolve()
    out_dir.mkdir(parents=True, exist_ok=True)
    work = Path(args.work).expanduser().resolve() if args.work else out_dir / "_work"
    work.mkdir(parents=True, exist_ok=True)

    # The upstream export reads `language` from the environment in some versions;
    # we pass it explicitly instead, but set it too for safety.
    os.environ["language"] = args.lang

    print(f"[1/3] downloading MMS '{args.lang}' weights -> {work}")
    fetch_weights(args.lang, work)

    print("[2/3] preparing upstream VITS package")
    prepare_vits_package(work)

    print("[3/3] exporting model.onnx + tokens.txt")
    export(args.lang, work, out_dir)

    print("\nDone. Copy these to the device:")
    print(f"  {out_dir / 'model.onnx'}")
    print(f"  {out_dir / 'tokens.txt'}")
    print("Then: KoSpeaker -> Manage Languages -> Install from SD "
          "(code 'heb', model type 'MMS'). See docs/HEBREW.md.")


if __name__ == "__main__":
    main()
