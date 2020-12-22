package com.ftp.file;

public class KeyGenerator {
    public static final long n=65535;
    public static final long g=255;
    private long a;
    private long finalCode;
    public KeyGenerator(long a){
        this.a=a;
    }

    public long getCodeToSend(){
        return (long)Math.pow(g, a)%n;
    }

    public void setReceivedCode(long code){
        finalCode=(long)Math.pow(code,a);
    }

    public long getFinalCode(){
        return finalCode;
    }
}
