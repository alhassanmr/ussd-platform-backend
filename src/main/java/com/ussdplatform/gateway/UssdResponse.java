package com.ussdplatform.gateway;

public class UssdResponse {
    private String message;
    private boolean shouldContinue;

    public UssdResponse() {}
    public UssdResponse(String message, boolean shouldContinue) {
        this.message = message; this.shouldContinue = shouldContinue;
    }
    public String getMessage() { return message; }
    public boolean isShouldContinue() { return shouldContinue; }
    public void setMessage(String v) { this.message = v; }
    public void setShouldContinue(boolean v) { this.shouldContinue = v; }

    public static Builder builder() { return new Builder(); }
    public static class Builder {
        private String message;
        private boolean shouldContinue;
        public Builder message(String v) { this.message = v; return this; }
        public Builder shouldContinue(boolean v) { this.shouldContinue = v; return this; }
        public UssdResponse build() { return new UssdResponse(message, shouldContinue); }
    }
}
