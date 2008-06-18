/*
 * Copyright (c)2004-2008 Mark Logic Corporation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * The use of the Apache License does not indicate that this project is
 * affiliated with the Apache Software Foundation.
 */
package com.marklogic.ps;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.CharsetDecoder;
import java.util.List;

/**
 * @author mike.blakeley@marklogic.com
 * 
 */
public class Utilities {

    public static String join(List<String> _items, String _delim) {
        return join(_items.toArray(new String[0]), _delim);
    }

    /**
     * @param _items
     * @param _delim
     * @return
     */
    public static String join(String[] _items, String _delim) {
        StringBuffer rval = new StringBuffer();
        for (int i = 0; i < _items.length; i++) {
            if (i == 0) {
                rval.append(_items[0]);
            } else {
                rval.append(_delim).append(_items[i]);
            }
        }
        return rval.toString();
    }

    /**
     * @param _items
     * @param _delim
     * @return
     */
    public static String join(Object[] _items, String _delim) {
        StringBuffer rval = new StringBuffer();
        for (int i = 0; i < _items.length; i++) {
            if (i == 0) {
                rval.append(_items[0]);
            } else {
                rval.append(_delim).append(_items[i]);
            }
        }
        return rval.toString();
    }

    public static String escapeXml(String _in) {
        if (_in == null)
            return "";
        return _in.replaceAll("&", "&amp;").replaceAll("<", "&lt;")
                .replaceAll(">", "&gt;");
    }

    public static final boolean stringToBoolean(String str) {
        return stringToBoolean(str, false);
    }

    // let the caller decide: should an unset string be true or false?
    public static final boolean stringToBoolean(String str,
            boolean defaultValue) {
        if (str == null)
            return defaultValue;

        String lcStr = str.toLowerCase();
        if (str.equals("") || str.equals("0") || lcStr.equals("f")
                || lcStr.equals("false") || lcStr.equals("n")
                || lcStr.equals("no"))
            return false;

        return true;
    }

    /**
     * @param t
     * @return
     */
    public static Throwable getCause(Throwable t) {
        // trace back to the original cause, if there was one
        Throwable cause = t;
        Throwable temp = null;
        while ((temp = cause.getCause()) != null) {
            cause = temp;
        }
        return cause;
    }

    /**
     * @param sb
     * @throws IOException
     */
    public static void read(Reader input, StringBuffer sb)
            throws IOException {
        // uses a reader, so charset translation should be ok
        int size;
        char[] buf = new char[32 * 1024];
        while ((size = input.read(buf)) > -1) {
            sb.append(buf, 0, size);
        }
    }

    /**
     * @param _is
     * @param _decoder
     * @return
     * @throws IOException
     */
    public static String read(InputStream _is, CharsetDecoder _decoder)
            throws IOException {
        StringBuffer sb = new StringBuffer();
        read(new InputStreamReader(_is, _decoder), sb);
        return sb.toString();
    }

    /**
     * @param _is
     * @param _decoder
     * @return
     * @throws IOException
     */
    public static byte[] read(InputStream _is) throws IOException {
        if (null == _is) {
            throw new IOException("null InputStream");
        }
        ByteArrayOutputStream os = new ByteArrayOutputStream(_is
                .available());
        byte[] buf = new byte[32 * 1024];
        int len = 0;
        while ((len = _is.read(buf)) != -1) {
            os.write(buf, 0, len);
        }
        os.flush();
        return os.toByteArray();
    }

    /**
     * @param name
     * @return
     */
    public static String stripExtension(String name) {
        if (name == null || name.length() < 3) {
            return name;
        }

        int i = name.lastIndexOf('.');
        if (i < 1) {
            return name;
        }

        return name.substring(0, i);
    }

}
