package dev.restone0616.ymp.comment;

import lombok.Getter;
import org.kohsuke.github.GHMyself;

@Getter
public class VerificationResult {
    private final boolean valid;
    private final GHMyself user;
    private final String errorMessage;

    public VerificationResult(boolean valid, GHMyself user, String errorMessage) {
        this.valid = valid;
        this.user = user;
        this.errorMessage = errorMessage;
    }

}
