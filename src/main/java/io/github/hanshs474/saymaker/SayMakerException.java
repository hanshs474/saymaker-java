package io.github.hanshs474.saymaker;

/** The only exception this library throws on purpose. Check {@link #kind()} to decide what to do next. */
public final class SayMakerException extends RuntimeException {
    /** Why a call failed, split by what to do next. */
    public enum Kind {
        /** No API key, or the key is invalid or deleted. */
        AUTH,
        /** The account is out of credits. */
        QUOTA,
        /** The model needs a paid plan or a credit pack. */
        PLAN,
        /** Another run on this account is still rendering. Free accounts run one at a time. */
        BUSY,
        /** The content filter or the model refused the prompt. Reword it. */
        REJECTED,
        /** The deadline passed; the run may still finish in your SayMaker history. */
        TIMEOUT,
        /** Anything else the service answered, or a transport failure. */
        OTHER
    }

    private static final long serialVersionUID = 1L;

    private final Kind kind;

    SayMakerException(Kind kind, String message) {
        super(message);
        this.kind = kind;
    }

    /** What went wrong. */
    public Kind kind() {
        return kind;
    }

    /** Map a refusal message to the error that says what to do next. */
    static SayMakerException classify(String message, boolean busy) {
        String low = message.toLowerCase();
        if (busy || low.contains("is rendering") || low.contains("at once")) return new SayMakerException(Kind.BUSY, message);
        if (low.contains("api key") || low.contains("no auth")) return new SayMakerException(Kind.AUTH, message);
        if (low.contains("insufficient credits")) return new SayMakerException(Kind.QUOTA, message);
        if (low.contains("subscription") || low.contains("plan") || low.contains("sign in")) return new SayMakerException(Kind.PLAN, message);
        return new SayMakerException(Kind.OTHER, message);
    }
}
