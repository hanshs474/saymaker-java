package io.github.hanshs474.saymaker;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Plain-main tests so the build needs no test framework. Run: java -cp build/classes:build/test ...SayMakerTest */
public final class SayMakerTest {
    static void check(boolean ok, String what) {
        if (!ok) throw new AssertionError(what);
    }

    @SuppressWarnings("unchecked")
    public static void main(String[] args) {
        List<Map<String, Object>> calls = new ArrayList<>();
        SayMaker.Transport t = (url, json, key) -> {
            calls.add((Map<String, Object>) Json.parse(json));
            String body = url.endsWith("/api/ai/generate")
                    ? "{\"code\":0,\"data\":{\"id\":\"row1\"}}"
                    : "{\"code\":0,\"data\":{\"status\":\"success\",\"images\":[\"m\"],\"cleanImages\":[\"c\"],\"videos\":[]}}";
            return (Map<String, Object>) Json.parse(body);
        };
        SayMaker sm = new SayMaker("sk", "https://t", t);
        check(sm.generate("x", SayMaker.image().pollEvery(Duration.ZERO)).equals("c"), "clean first");
        check(calls.get(0).get("model").equals("saymaker-image-v1"), "default model");
        check(calls.get(1).get("taskId").equals("row1"), "poll body");
        calls.clear();
        sm.generateVideo("x", SayMaker.video().duration(6).pollEvery(Duration.ZERO));
        check(calls.get(0).get("provider").equals("vgenv"), "video provider");
        check(((Map<String, Object>) calls.get(0).get("options")).get("duration").equals(6.0), "duration");
        check(SayMakerException.classify("insufficient credits", false).kind() == SayMakerException.Kind.QUOTA, "quota");
        check(SayMakerException.classify("Your current run is rendering. Plans on the pricing page", false).kind() == SayMakerException.Kind.BUSY, "busy");
        try {
            new SayMaker("").generate("x");
            check(false, "needs key");
        } catch (SayMakerException e) {
            check(e.kind() == SayMakerException.Kind.AUTH, "auth");
        }
        System.out.println("all tests passed");
    }
}
