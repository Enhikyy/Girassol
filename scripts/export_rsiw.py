#!/usr/bin/env python3
"""Export trained adapter tensors to Girassol's hash-verified RSIW bundle.

Requires torch. The input checkpoint must be a state_dict or contain a state_dict
under 'state_dict'/'model'. --mapping is JSON: {adapter_tensor_name: checkpoint_key}.
"""
import argparse
import hashlib
import json
import zipfile
from pathlib import Path

import torch

EXPECTED = {
    "rdt": ("input_weights", "recurrent_weights", "bias", "gate"),
    "lunaris_moc": ("router_weights", "router_bias", "expert_weights", "expert_bias",
                    "mediator_weights", "mediator_bias", "fusion_weights", "fusion_bias"),
    "modusx": (),
}


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--checkpoint", type=Path)
    parser.add_argument("--mapping", type=Path, help="JSON mapping from RSIW tensor names to state_dict keys")
    parser.add_argument("--architecture", choices=EXPECTED, required=True)
    parser.add_argument("--hidden-size", type=int, required=True)
    parser.add_argument("--experts", type=int, default=0)
    parser.add_argument("--top-k", type=int, default=1)
    parser.add_argument("--reasoning-steps", type=int, default=1)
    parser.add_argument("--decay", type=float, default=0.95)
    parser.add_argument("--learning-rate", type=float, default=0.001)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()

    if args.hidden_size <= 0 or args.hidden_size > (2048 if args.architecture == "rdt" else 512):
        raise SystemExit("hidden-size fora do limite nativo")
    if not 1 <= args.reasoning_steps <= (16 if args.architecture == "rdt" else 8):
        raise SystemExit("reasoning-steps fora do limite")
    if args.architecture == "lunaris_moc" and not (2 <= args.experts <= 16 and 1 <= args.top_k <= args.experts):
        raise SystemExit("experts/top-k inválidos")

    required = EXPECTED[args.architecture]
    if required and (args.checkpoint is None or args.mapping is None):
        raise SystemExit("RDT/Lunaris MoC exigem --checkpoint e --mapping")
    state = {}
    if args.checkpoint is not None:
        checkpoint = torch.load(args.checkpoint, map_location="cpu", weights_only=True)
        if isinstance(checkpoint, dict):
            state = checkpoint.get("state_dict", checkpoint.get("model", checkpoint))
        else:
            raise SystemExit("checkpoint deve conter um state_dict")
        if not isinstance(state, dict):
            raise SystemExit("state_dict inválido")

    mapping = json.loads(args.mapping.read_text()) if args.mapping else {}
    if required and set(mapping) != set(required):
        raise SystemExit("mapping deve conter exatamente: " + ", ".join(required))

    shapes = {
        "input_weights": (args.hidden_size, args.hidden_size),
        "recurrent_weights": (args.hidden_size, args.hidden_size),
        "bias": (args.hidden_size,),
        "gate": (args.hidden_size,),
        "router_weights": (args.experts, args.hidden_size),
        "router_bias": (args.experts,),
        "expert_weights": (args.experts, args.hidden_size, args.hidden_size),
        "expert_bias": (args.experts, args.hidden_size),
        "mediator_weights": (args.hidden_size, args.hidden_size),
        "mediator_bias": (args.hidden_size,),
        "fusion_weights": (args.hidden_size, args.hidden_size),
        "fusion_bias": (args.hidden_size,),
    }
    payload = bytearray()
    descriptors = {}
    for name in required:
        key = mapping[name]
        if key not in state:
            raise SystemExit(f"tensor ausente no checkpoint: {key}")
        tensor = state[key].detach().to(device="cpu", dtype=torch.float32).contiguous()
        if tuple(tensor.shape) != shapes[name]:
            raise SystemExit(f"{name}: shape {tuple(tensor.shape)} != {shapes[name]}")
        if not torch.isfinite(tensor).all().item():
            raise SystemExit(f"{name}: tensor contém NaN/Inf")
        raw = tensor.numpy().astype("<f4", copy=False).tobytes(order="C")
        offset = len(payload) // 4
        payload.extend(raw)
        descriptors[name] = {"offset": offset, "count": tensor.numel()}

    digest = hashlib.sha256(payload).hexdigest()
    manifest = {
        "format": "girassol-rsiw",
        "version": 1,
        "architecture": args.architecture,
        "hidden_size": args.hidden_size,
        "experts": args.experts,
        "top_k": args.top_k,
        "reasoning_steps": args.reasoning_steps,
        "decay": args.decay,
        "learning_rate": args.learning_rate,
        "tensor_sha256": digest,
        "tensors": descriptors,
    }
    args.output.parent.mkdir(parents=True, exist_ok=True)
    with zipfile.ZipFile(args.output, "w", compression=zipfile.ZIP_DEFLATED) as bundle:
        bundle.writestr("manifest.json", json.dumps(manifest, separators=(",", ":")))
        bundle.writestr("tensors.f32", payload)
    print(f"RSIW exportado: {args.output} · {len(payload)} bytes · sha256={digest}")


if __name__ == "__main__":
    main()
