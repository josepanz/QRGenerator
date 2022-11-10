package com.qr.emvco;

import java.util.HashMap;
import java.util.Map;

/**
 * Parseador de TLV's
 * Es utilizado para decodificar el TLV resultante de la lectura de un QR
 */
public class EMVCoParser {
    private static final int DEFAULT_LENGTH = 2;
    private static final String DEFAULT_START_TAG = "00";
    private static final String OTHERS_START_TAGS = "03" ;

    /**
     * @param subTlv
     * @return
     */
    public static Map<String, Map<String, EmvcoTlvBean>> parseSubTlv(Map<String, EmvcoTlvBean> subTlv) {
        Map<String, Map<String, EmvcoTlvBean>> result = new HashMap<>();
        for (Map.Entry<String, EmvcoTlvBean> emvcoTlvBeanEntry : subTlv.entrySet()) {
            final EmvcoTlvBean emvcoTlvBean = emvcoTlvBeanEntry.getValue();
            final String value = emvcoTlvBean.getValue();
            if (value.length() > 4) {
                final String tag = value.substring(0, DEFAULT_LENGTH);
                final String lengthStr = value.substring(DEFAULT_LENGTH, DEFAULT_LENGTH * 2);
                if (isInteger(tag) && isInteger(lengthStr)) {
                    final int length = Integer.parseInt(lengthStr);
                    if (tag.equals(DEFAULT_START_TAG) && length > 0) {
                        final Map<String, EmvcoTlvBean> parse = parse(value);
                        result.put(emvcoTlvBeanEntry.getKey(), parse);
                    } else if (tag.equals(OTHERS_START_TAGS) && length > 0) {
                        final Map<String, EmvcoTlvBean> parse = parse(value);
                        result.put(emvcoTlvBeanEntry.getKey(), parse);
                    }
                }
            }
        }
        return result;

    }

    public static Map<String, EmvcoTlvBean> parse(String emvcoTlvStr) {
        Map<String, EmvcoTlvBean> result = new HashMap<>();
        int i = 0;
        while (i < emvcoTlvStr.length()) {
            int tagInd = i + DEFAULT_LENGTH;
            String tag = emvcoTlvStr.substring(i, tagInd);
            int lengthValue = Integer.parseInt(emvcoTlvStr.substring(tagInd, tagInd + DEFAULT_LENGTH));
            EmvcoTlvBean emvcoTlvBean = new EmvcoTlvBean();
            emvcoTlvBean.setTag(tag);
            emvcoTlvBean.setLength(lengthValue);
            emvcoTlvBean.setValue(emvcoTlvStr.substring(tagInd + DEFAULT_LENGTH, tagInd + DEFAULT_LENGTH + lengthValue));

            result.put(tag, emvcoTlvBean);

            i = tagInd + DEFAULT_LENGTH + lengthValue;
        }

        return result;
    }

    public static void printTag(Map<String, EmvcoTlvBean> emvcoTlvBeanMap) {
        for (Map.Entry<String, EmvcoTlvBean> emvcoTlvBeanEntry : emvcoTlvBeanMap.entrySet()) {
            final EmvcoTlvBean emv = emvcoTlvBeanEntry.getValue();
            final String key = emvcoTlvBeanEntry.getKey();
            printOneTlv(emv, key);
        }
    }

    public static void printOneTlv(EmvcoTlvBean emv, String key) {
        System.out.println("Tag:" + (Integer.parseInt(key) < 9 ? "0" + emv.getTag() : emv.getTag()) + "\t Length:" + emv.getLength() + "\t Value:" + emv.getValue());
    }

    private static boolean isInteger(String str) {
        return isCreatable(str);
    }
    public static boolean isCreatable(final String str) {
        if (str.isEmpty()) {
            return false;
        }
        final char[] chars = str.toCharArray();
        int sz = chars.length;
        boolean hasExp = false;
        boolean hasDecPoint = false;
        boolean allowSigns = false;
        boolean foundDigit = false;
        // deal with any possible sign up front
        final int start = chars[0] == '-' || chars[0] == '+' ? 1 : 0;
        if (sz > start + 1 && chars[start] == '0' && !str.contains(".")) { // leading 0, skip if is a decimal number
            if (chars[start + 1] == 'x' || chars[start + 1] == 'X') { // leading 0x/0X
                int i = start + 2;
                if (i == sz) {
                    return false; // str == "0x"
                }
                // checking hex (it can't be anything else)
                for (; i < chars.length; i++) {
                    if ((chars[i] < '0' || chars[i] > '9')
                            && (chars[i] < 'a' || chars[i] > 'f')
                            && (chars[i] < 'A' || chars[i] > 'F')) {
                        return false;
                    }
                }
                return true;
            }
            if (Character.isDigit(chars[start + 1])) {
                // leading 0, but not hex, must be octal
                int i = start + 1;
                for (; i < chars.length; i++) {
                    if (chars[i] < '0' || chars[i] > '7') {
                        return false;
                    }
                }
                return true;
            }
        }
        sz--; // don't want to loop to the last char, check it afterwords
        // for type qualifiers
        int i = start;
        // loop to the next to last char or to the last char if we need another digit to
        // make a valid number (e.g. chars[0..5] = "1234E")
        while (i < sz || i < sz + 1 && allowSigns && !foundDigit) {
            if (chars[i] >= '0' && chars[i] <= '9') {
                foundDigit = true;
                allowSigns = false;

            } else if (chars[i] == '.') {
                if (hasDecPoint || hasExp) {
                    // two decimal points or dec in exponent
                    return false;
                }
                hasDecPoint = true;
            } else if (chars[i] == 'e' || chars[i] == 'E') {
                // we've already taken care of hex.
                if (hasExp) {
                    // two E's
                    return false;
                }
                if (!foundDigit) {
                    return false;
                }
                hasExp = true;
                allowSigns = true;
            } else if (chars[i] == '+' || chars[i] == '-') {
                if (!allowSigns) {
                    return false;
                }
                allowSigns = false;
                foundDigit = false; // we need a digit after the E
            } else {
                return false;
            }
            i++;
        }
        if (i < chars.length) {
            if (chars[i] >= '0' && chars[i] <= '9') {
                // no type qualifier, OK
                return true;
            }
            if (chars[i] == 'e' || chars[i] == 'E') {
                // can't have an E at the last byte
                return false;
            }
            if (chars[i] == '.') {
                if (hasDecPoint || hasExp) {
                    // two decimal points or dec in exponent
                    return false;
                }
                // single trailing decimal point after non-exponent is ok
                return foundDigit;
            }
            if (!allowSigns
                    && (chars[i] == 'd'
                    || chars[i] == 'D'
                    || chars[i] == 'f'
                    || chars[i] == 'F')) {
                return foundDigit;
            }
            if (chars[i] == 'l'
                    || chars[i] == 'L') {
                // not allowing L with an exponent or decimal point
                return foundDigit && !hasExp && !hasDecPoint;
            }
            // last character is illegal
            return false;
        }
        // allowSigns is true iff the val ends in 'E'
        // found digit it to make sure weird stuff like '.' and '1E-' doesn't pass
        return !allowSigns && foundDigit;
    }
}
