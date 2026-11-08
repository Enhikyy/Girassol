package dev.labforge.app.core.probe;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

/** Runs the production JNI bridge with HotSpot's strict JNI checker. */
public class NativeTrainingProbe {
    static { System.loadLibrary("labforge"); }
    public interface Callback {
        void onLog(String text);
        void onEpoch(int epoch, int step, int total, double loss, double lr);
    }
    static class First implements Callback {
        int logs, epochs;
        public void onLog(String s) { logs++; }
        public void onEpoch(int e, int s, int t, double loss, double lr) { epochs++; }
    }
    // Separate class catches method IDs incorrectly cached from a previous job.
    static class Second implements Callback {
        int logs, epochs;
        public void onLog(String s) { logs++; }
        public void onEpoch(int e, int s, int t, double loss, double lr) { epochs++; }
    }
    static native String nativeLoraTrain(String model, String out, String dataset, int epochs,
        float lr, int ctx, int threads, int rank, float alpha, String targets, Callback cb);
    static native String nativeFinetune(String model, String out, String dataset, int epochs,
        float lr, int ctx, int batch, int ubatch, int threads, String filter, Callback cb);
    static native int nativeCountTokens(String model, String text);

    static void expect(String json, boolean ok, String marker) {
        if (!json.contains("\"ok\":" + ok) || !json.contains(marker)) throw new AssertionError(json);
    }
    public static void main(String[] args) throws Exception {
        Path dir = Path.of(args[0]);
        Path dataset = dir.resolve("dataset.txt");
        Files.writeString(dataset, "The sun is bright. Plants grow toward the light. Study the natural world. ".repeat(60));
        if (args.length > 1 && args[1].equals("callbacks")) {
            // Baseline crash reproduction: one real training call is enough.
            expect(nativeLoraTrain(dir.resolve("tiny-f32.gguf").toString(), dir.resolve("adapter.gguf").toString(),
                dataset.toString(), 1, 0.0001f, 256, 2, 4, 4, "attn,ffn", new First()), true, "\"tensors\":14");
            return;
        }
        for (String type : new String[]{"f32", "f16", "bf16", "q8"}) {
            String model = dir.resolve("tiny-" + type + ".gguf").toString();
            byte[] original = Files.readAllBytes(Path.of(model));
            First a = new First(); Second b = new Second();
            String adapter = dir.resolve("adapter-" + type + ".gguf").toString();
            expect(nativeLoraTrain(model, adapter, dataset.toString(), 1, 0.0001f, 256, 2, 4, 4, "attn,ffn", a), true, "\"tensors\":14");
            if (a.logs == 0 || a.epochs == 0 || Files.size(Path.of(adapter)) == 0) throw new AssertionError("No LoRA progress/output");
            expect(nativeFinetune(model, dir.resolve("full-" + type + ".gguf").toString(), dataset.toString(),
                1, 0.0001f, 256, 256, 256, 2, "", b), !type.equals("q8"), type.equals("q8") ? "use LoRA" : "\"error\":\"\"");
            if (!type.equals("q8") && (b.logs == 0 || b.epochs == 0)) throw new AssertionError("No full training progress");
            if (!Arrays.equals(original, Files.readAllBytes(Path.of(model)))) throw new AssertionError("Base model modified");
            try (var files = Files.list(dir)) {
                if (files.anyMatch(p -> p.toString().contains(".training-"))) throw new AssertionError("Leaked temporary model");
            }
            System.out.println("TRAINING_PASS " + type);
        }
        String model = dir.resolve("tiny-f32.gguf").toString();
        for (int count : new int[]{300, 450}) {
            String text = " the".repeat(count);
            int tokens = nativeCountTokens(model, text);
            if (tokens <= 257 || tokens >= 513) throw new AssertionError("Invalid short-dataset fixture: " + tokens);
            Files.writeString(dataset, text);
            expect(nativeLoraTrain(model, dir.resolve("short-lora.gguf").toString(), dataset.toString(),
                1, 0.0001f, 256, 2, 4, 4, "attn", new First()), false, "dataset curto");
            expect(nativeFinetune(model, dir.resolve("short-full.gguf").toString(), dataset.toString(),
                1, 0.0001f, 256, 256, 256, 2, "", new Second()), false, "dataset curto");
        }
        expect(nativeFinetune(model, dir.resolve("filtered.gguf").toString(), dataset.toString(),
            1, 0.0001f, 256, 256, 256, 2, "missing-tensor", new First()), false, "nenhum peso treinável");
        expect(nativeLoraTrain(dir.resolve("missing.gguf").toString(), dir.resolve("missing-out.gguf").toString(),
            dataset.toString(), 1, 0.0001f, 256, 2, 4, 4, "attn", new Second()), false, "falha ao carregar");
        expect(nativeFinetune(model, dir.resolve("bad-batch.gguf").toString(), dataset.toString(),
            1, 0.0001f, 256, 100, 100, 2, "", new First()), false, "incompatíveis");
        expect(nativeLoraTrain(model, dir.resolve("bad-context.gguf").toString(), dataset.toString(),
            1, 0.0001f, 257, 2, 4, 4, "attn", new Second()), false, "incompatíveis");
        System.out.println("INVALID_INPUTS_PASS");
    }
}
