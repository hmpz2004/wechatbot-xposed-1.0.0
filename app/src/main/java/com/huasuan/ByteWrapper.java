package com.huasuan;

class ByteWrapper {
    private byte[] reader;
    public void setBytes(byte[] tmp){
        this.reader = tmp;
    }
    public byte[] readAndClear(){
        byte[] tmp = this.reader;
        this.reader = new byte[]{};
        return tmp;
    }
}
