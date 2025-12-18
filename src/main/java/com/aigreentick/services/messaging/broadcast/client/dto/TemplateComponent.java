package com.aigreentick.services.messaging.broadcast.client.dto;

public  class TemplateComponent {
    private String type; // HEADER, BODY, FOOTER, BUTTON
    private String format; // IMAGE, VIDEO, DOCUMENT, TEXT
    private String text; // Component text content

    // Default constructor
    public TemplateComponent() {
    }

    public TemplateComponent(String type, String format, String text) {
        this.type = type;
        this.format = format;
        this.text = text;
    }

    // Getters and setters
    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getFormat() {
        return format;
    }

    public void setFormat(String format) {
        this.format = format;
    }

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }
}
