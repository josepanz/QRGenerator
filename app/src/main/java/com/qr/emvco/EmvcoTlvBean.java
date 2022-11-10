package com.qr.emvco;

import java.util.StringJoiner;

/**
 * Clase que administra la estructura del TLV
 */
public class EmvcoTlvBean {
    private String tag;
    private int length;
    private String value;

    public String getTag() {
        return tag;
    }

    public void setTag(String tag) {
        this.tag = tag;
    }

    public int getLength() {
        return length;
    }

    public void setLength(int length) {
        this.length = length;
    }

    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = value;
    }

    @Override
    public String toString() {
        return new StringJoiner(", ", EmvcoTlvBean.class.getSimpleName() + "[", "]")
                .add("tag='" + tag + "'")
                .add("length=" + length)
                .add("value='" + value + "'")
                .toString();
    }
}
