package com.ussdplatform.gateway;

public class UssdRequest {
    private String sessionId;
    private String msisdn;
    private String shortCode;
    private String input;
    private boolean isNew;

    public UssdRequest() {}
    public UssdRequest(String sessionId, String msisdn, String shortCode, String input, boolean isNew) {
        this.sessionId = sessionId; this.msisdn = msisdn; this.shortCode = shortCode;
        this.input = input; this.isNew = isNew;
    }
    public String getSessionId() { return sessionId; }
    public String getMsisdn() { return msisdn; }
    public String getShortCode() { return shortCode; }
    public String getInput() { return input; }
    public boolean isNew() { return isNew; }
    public void setSessionId(String v) { this.sessionId = v; }
    public void setMsisdn(String v) { this.msisdn = v; }
    public void setShortCode(String v) { this.shortCode = v; }
    public void setInput(String v) { this.input = v; }
    public void setNew(boolean v) { this.isNew = v; }

    public static Builder builder() { return new Builder(); }
    public static class Builder {
        private String sessionId, msisdn, shortCode, input;
        private boolean isNew;
        public Builder sessionId(String v) { this.sessionId = v; return this; }
        public Builder msisdn(String v) { this.msisdn = v; return this; }
        public Builder shortCode(String v) { this.shortCode = v; return this; }
        public Builder input(String v) { this.input = v; return this; }
        public Builder isNew(boolean v) { this.isNew = v; return this; }
        public UssdRequest build() { return new UssdRequest(sessionId, msisdn, shortCode, input, isNew); }
    }
}
