package io.github.hanshs474.saymaker;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Generate images and video on <a href="https://saymaker.ai">SayMaker</a> from Java.
 *
 * <pre>{@code
 * SayMaker sm = new SayMaker(System.getenv("SAYMAKER_API_KEY"));
 * String url = sm.generate("a paper-cut layered mountain range at dusk");
 * String clip = sm.generateVideo("a fox trotting through snow", SayMaker.video().duration(6));
 * }</pre>
 *
 * <p>One API key runs the whole shelf on your own credits: Veo 3.1, Kling 3.0, Seedance 2.0,
 * MiniMax H3, Nano Banana 2, GPT Image 2.5, Seedream 5.0 and more. Create a key at
 * https://saymaker.ai/settings/apikeys. Java 11+, no dependencies.
 */
public final class SayMaker {
    /** The service this library talks to. */
    public static final String BASE_URL = "https://saymaker.ai";
    /** Where an API key is created. */
    public static final String KEYS_URL = BASE_URL + "/settings/apikeys";
    /** The cheapest text-to-image model. */
    public static final String MODEL_IMAGE = "saymaker-image-v1";
    /** The default edit model; it takes an input image. */
    public static final String MODEL_EDIT = "nano-banana-2-lite";
    /** The video model a free account can run (480p or 768p, 4 to 15 seconds). */
    public static final String MODEL_VIDEO = "minimax-h3-fast";

    /** Model id to the routing field the API expects next to it. Unlisted ids go as "kie". */
    public static final Map<String, String> MODELS;

    static {
        Map<String, String> m = new HashMap<>();
        String[] pairs = {
            "saymaker-image-v1", "kie", "nano-banana-2-lite", "kie", "nano-banana-2", "poyo",
            "nano-banana-pro", "poyo", "gpt-image-2", "poyo", "gpt-image-2-5-flare", "kie",
            "seedream-5-lite", "poyo", "seedream-5-pro", "kie", "qwen-image-3-pro", "kie",
            "grok-imagine-image-2-0", "poyo", "minimax-h3-fast", "vgenv", "minimax-h3", "poyo",
            "seedance-2", "kie", "seedance-2-fast", "kie", "seedance-2-5", "kie", "veo-3-1", "kie",
            "kling-3-0", "kie", "kling-3-0-turbo", "kie", "wan-3-0", "kie", "ltx-2-5-fast", "replicate",
        };
        for (int k = 0; k < pairs.length; k += 2) m.put(pairs[k], pairs[k + 1]);
        MODELS = java.util.Collections.unmodifiableMap(m);
    }

    /** Options for an image. */
    public static final class ImageOptions {
        String model, imageUrl, aspectRatio;
        Duration timeout = Duration.ofMinutes(75), pollEvery = Duration.ofSeconds(5);

        /** A model id, e.g. "gpt-image-2-5-flare". */
        public ImageOptions model(String v) { model = v; return this; }
        /** A public http(s) url; makes the call an edit. */
        public ImageOptions imageUrl(String v) { imageUrl = v; return this; }
        /** "1:1", "16:9", "9:16", "4:3", "3:4". */
        public ImageOptions aspectRatio(String v) { aspectRatio = v; return this; }
        /** Defaults to 75 minutes, which covers the free-account queue. */
        public ImageOptions timeout(Duration v) { timeout = v; return this; }
        /** Defaults to five seconds. */
        public ImageOptions pollEvery(Duration v) { pollEvery = v; return this; }
    }

    /** Options for a clip. */
    public static final class VideoOptions {
        String model = MODEL_VIDEO, imageUrl, resolution, aspectRatio = "16:9";
        Integer duration;
        boolean sound = true;
        Duration timeout = Duration.ofMinutes(90), pollEvery = Duration.ofSeconds(10);

        /** A model id, e.g. "veo-3-1". */
        public VideoOptions model(String v) { model = v; return this; }
        /** Optional first frame; makes the call image-to-video. */
        public VideoOptions imageUrl(String v) { imageUrl = v; return this; }
        /** Seconds, where the model offers a choice. */
        public VideoOptions duration(int v) { duration = v; return this; }
        /** e.g. "480p", "720p", "1080p". */
        public VideoOptions resolution(String v) { resolution = v; return this; }
        /** Defaults to "16:9". */
        public VideoOptions aspectRatio(String v) { aspectRatio = v; return this; }
        /** Ask for sound where the model writes it in the same pass. Default true. */
        public VideoOptions sound(boolean v) { sound = v; return this; }
        /** Defaults to 90 minutes. */
        public VideoOptions timeout(Duration v) { timeout = v; return this; }
        /** Defaults to ten seconds. */
        public VideoOptions pollEvery(Duration v) { pollEvery = v; return this; }
    }

    /** Start image options. */
    public static ImageOptions image() { return new ImageOptions(); }

    /** Start video options. */
    public static VideoOptions video() { return new VideoOptions(); }

    /** Transport seam: POST a JSON body, return the parsed JSON envelope. */
    interface Transport {
        Map<String, Object> post(String url, String json, String key) throws IOException, InterruptedException;
    }

    private final String apiKey;
    private final String base;
    private final Transport transport;

    /** A client for one API key. */
    public SayMaker(String apiKey) {
        this(apiKey, BASE_URL, null);
    }

    SayMaker(String apiKey, String base, Transport transport) {
        this.apiKey = apiKey == null ? "" : apiKey.trim();
        this.base = base;
        this.transport = transport != null ? transport : httpTransport();
    }

    private static Transport httpTransport() {
        HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(30)).build();
        return (url, json, key) -> {
            HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofSeconds(60))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + key)
                    .header("User-Agent", "saymaker-java/0.1.0")
                    .POST(HttpRequest.BodyPublishers.ofString(json))
                    .build();
            HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString());
            return asMap(Json.parse(res.body()));
        };
    }

    /** Generate one image and return its URL. */
    public String generate(String prompt) {
        return generate(prompt, image());
    }

    /** Generate one image, or edit {@code o.imageUrl}, and return its URL. */
    public String generate(String prompt, ImageOptions o) {
        checkUrl(o.imageUrl);
        boolean edit = o.imageUrl != null;
        Map<String, Object> options = new LinkedHashMap<>();
        options.put("size", o.aspectRatio != null ? o.aspectRatio : (edit ? "auto" : "1:1"));
        options.put("resolution", "1K");
        options.put("n", 1);
        if (edit) options.put("image_input", List.of(o.imageUrl));
        String model = o.model != null ? o.model : (edit ? MODEL_EDIT : MODEL_IMAGE);
        return run("image", edit ? "image-to-image" : "text-to-image", model, prompt, options, o.timeout, o.pollEvery);
    }

    /** Generate one clip and return its URL. */
    public String generateVideo(String prompt) {
        return generateVideo(prompt, video());
    }

    /** Generate one clip, or animate {@code o.imageUrl} as the first frame, and return its URL. */
    public String generateVideo(String prompt, VideoOptions o) {
        checkUrl(o.imageUrl);
        Map<String, Object> options = new LinkedHashMap<>();
        options.put("aspect_ratio", o.aspectRatio);
        options.put("sound", o.sound);
        if (o.imageUrl != null) options.put("image_input", List.of(o.imageUrl));
        if (o.duration != null) options.put("duration", o.duration);
        if (o.resolution != null) options.put("resolution", o.resolution);
        return run("video", o.imageUrl != null ? "image-to-video" : "text-to-video", o.model, prompt, options, o.timeout, o.pollEvery);
    }

    private static void checkUrl(String u) {
        if (u != null && !(u.startsWith("http://") || u.startsWith("https://"))) {
            throw new IllegalArgumentException("imageUrl must be a public http(s) url");
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> asMap(Object o) {
        return o instanceof Map ? (Map<String, Object>) o : new HashMap<>();
    }

    private Map<String, Object> post(String path, Map<String, Object> body) {
        try {
            return transport.post(base + path, Json.write(body), apiKey);
        } catch (IOException e) {
            throw new SayMakerException(SayMakerException.Kind.OTHER, e.toString());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new SayMakerException(SayMakerException.Kind.OTHER, "interrupted");
        }
    }

    private String run(String media, String scene, String model, String prompt, Map<String, Object> options,
                       Duration timeout, Duration pollEvery) {
        if (prompt == null || prompt.trim().isEmpty()) throw new IllegalArgumentException("prompt is required");
        if (apiKey.isEmpty()) {
            throw new SayMakerException(SayMakerException.Kind.AUTH, "an API key is required; create one at " + KEYS_URL);
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("provider", MODELS.getOrDefault(model, "kie"));
        body.put("mediaType", media);
        body.put("model", model);
        body.put("scene", scene);
        body.put("prompt", prompt);
        body.put("options", options);
        Map<String, Object> env = post("/api/ai/generate", body);
        Map<String, Object> data = asMap(env.get("data"));
        // Refusals answer HTTP 200 with code -1; the status code alone reads as success.
        if (!isZero(env.get("code"))) {
            Object msg = env.get("message");
            throw SayMakerException.classify(msg == null ? "request refused" : msg.toString(), data.get("busy") != null);
        }
        // A signed-out request is also a 200 with code 0; only `wall` tells it apart.
        if (Boolean.TRUE.equals(data.get("wall"))) {
            throw new SayMakerException(SayMakerException.Kind.AUTH, "an API key is required; create one at " + KEYS_URL);
        }
        Object id = data.get("id");
        if (id == null) throw new SayMakerException(SayMakerException.Kind.OTHER, "the service returned no task id");

        // Free-account runs only reach the model on the poll that crosses the end of the
        // queue wait, so polling is what starts the work.
        Instant deadline = Instant.now().plus(timeout);
        Map<String, Object> poll = new LinkedHashMap<>();
        poll.put("taskId", id.toString());
        while (Instant.now().isBefore(deadline)) {
            try {
                Thread.sleep(pollEvery.toMillis());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new SayMakerException(SayMakerException.Kind.OTHER, "interrupted");
            }
            Map<String, Object> q;
            try {
                q = post("/api/ai/query", poll);
            } catch (SayMakerException e) {
                continue; // a dropped poll is not a failed generation
            }
            if (!isZero(q.get("code"))) continue;
            Map<String, Object> d = asMap(q.get("data"));
            for (String field : new String[] {"cleanImages", "images", "videos"}) {
                Object list = d.get(field);
                if (list instanceof List && !((List<?>) list).isEmpty()) return String.valueOf(((List<?>) list).get(0));
            }
            String status = String.valueOf(d.get("status")).toLowerCase();
            if (status.equals("failed") || status.equals("error")) {
                throw new SayMakerException(SayMakerException.Kind.REJECTED, "the run failed; reword the prompt rather than retrying");
            }
        }
        throw new SayMakerException(SayMakerException.Kind.TIMEOUT, "deadline passed; the run may still finish in your SayMaker history");
    }

    private static boolean isZero(Object code) {
        return code instanceof Number && ((Number) code).intValue() == 0;
    }
}
