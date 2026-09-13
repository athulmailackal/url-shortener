package com.devpeepu.urlshortener.exception;

public class UrlNotFoundException extends  RuntimeException{
    public UrlNotFoundException(String message){
        super(message);
    }
}
